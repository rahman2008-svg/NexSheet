package com.example.data.repository

import android.content.Context
import com.example.data.dao.SpreadsheetDao
import com.example.data.entity.SpreadsheetFile
import com.example.data.entity.SpreadsheetContent
import com.example.data.entity.SpreadsheetVersion
import com.example.data.model.Sheet
import com.example.data.model.CellData
import com.example.data.model.FormulaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class SpreadsheetRepository(private val dao: SpreadsheetDao, private val context: Context) {

    val allFiles: Flow<List<SpreadsheetFile>> = dao.getAllFiles()

    fun getFileById(id: Long): Flow<SpreadsheetFile?> = dao.getFileById(id)

    suspend fun getFileByIdSuspend(id: Long): SpreadsheetFile? = withContext(Dispatchers.IO) {
        dao.getFileByIdSuspend(id)
    }

    suspend fun createFile(title: String, category: String, initialSheets: List<Sheet>): Long = withContext(Dispatchers.IO) {
        val file = SpreadsheetFile(
            title = title,
            category = category,
            lastModified = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        val fileId = dao.insertFile(file)
        val contentJson = Sheet.serializeList(initialSheets)
        dao.insertContent(SpreadsheetContent(fileId, contentJson))
        
        // Also physically save newly created spreadsheet to internal storage
        saveToPhysicalInternalStorage(file, initialSheets, contentJson)
        
        fileId
    }

    suspend fun updateFile(file: SpreadsheetFile) = withContext(Dispatchers.IO) {
        dao.updateFile(file)
    }

    suspend fun deleteFile(file: SpreadsheetFile) = withContext(Dispatchers.IO) {
        dao.deleteFile(file)
        dao.deleteContent(file.id)
        dao.deleteVersionsForFile(file.id)
        
        // Clean up direct-saved physical files from internal storage
        try {
            val exportDir = File(context.filesDir, "internal_spreadsheets")
            if (exportDir.exists() && exportDir.isDirectory) {
                val sanitizedName = file.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                val filesToDelete = exportDir.listFiles { _, name ->
                    name.startsWith("${sanitizedName}_${file.id}.") ||
                    name.endsWith("_${file.id}.json") ||
                    name.endsWith("_${file.id}.csv") ||
                    name.endsWith("_${file.id}.html") ||
                    name.contains("_${file.id}_")
                }
                filesToDelete?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getContentForFile(fileId: Long): List<Sheet> = withContext(Dispatchers.IO) {
        val content = dao.getContentForFileSuspend(fileId)
        if (content != null) {
            Sheet.deserializeList(content.sheetsJson)
        } else {
            emptyList()
        }
    }

    suspend fun saveContentForFile(fileId: Long, sheets: List<Sheet>) = withContext(Dispatchers.IO) {
        val contentJson = Sheet.serializeList(sheets)
        dao.insertContent(SpreadsheetContent(fileId, contentJson))

        // Update the last modified time of the file
        val file = dao.getFileByIdSuspend(fileId)
        if (file != null) {
            val updatedFile = file.copy(lastModified = System.currentTimeMillis())
            dao.updateFile(updatedFile)
            
            // Auto-Save direct representation in physical storage
            saveToPhysicalInternalStorage(updatedFile, sheets, contentJson)
        }
    }

    private fun saveToPhysicalInternalStorage(file: SpreadsheetFile, sheets: List<Sheet>, contentJson: String) {
        try {
            val exportDir = File(context.filesDir, "internal_spreadsheets")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val sanitizedName = file.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val fileId = file.id

            // 1. Save Full Workbook JSON
            val jsonFile = File(exportDir, "${sanitizedName}_${fileId}.json")
            jsonFile.writeText(contentJson)

            // 2. Save first standard active sheet as CSV for clean portability
            val activeSheet = sheets.firstOrNull() ?: Sheet("Sheet 1", emptyMap())
            val csvContent = buildCsvString(activeSheet)
            val csvFile = File(exportDir, "${sanitizedName}_${fileId}.csv")
            csvFile.writeText(csvContent)

            // 3. Save beautiful styled custom HTML report
            val htmlContent = buildHtmlString(file.title, activeSheet)
            val htmlFile = File(exportDir, "${sanitizedName}_${fileId}.html")
            htmlFile.writeText(htmlContent)
            
            android.util.Log.d("DirectSave", "Physically direct saved the file direct on internal storage: ${jsonFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildCsvString(sheet: Sheet): String {
        val builder = java.lang.StringBuilder()
        var maxCol = 1
        var maxRow = 1
        sheet.cells.keys.forEach { ref ->
            FormulaParser.parseCellReference(ref)?.let { (col, row) ->
                if (col > maxCol) maxCol = col
                if (row > maxRow) maxRow = row
            }
        }
        for (r in 0..maxRow) {
            val rowParts = mutableListOf<String>()
            for (c in 0..maxCol) {
                val ref = "${FormulaParser.getColumnLabel(c)}${r + 1}"
                val valStr = sheet.cells[ref]?.value ?: ""
                val escaped = if (valStr.contains(",") || valStr.contains("\"") || valStr.contains("\n")) {
                    "\"" + valStr.replace("\"", "\"\"") + "\""
                } else {
                    valStr
                }
                rowParts.add(escaped)
            }
            builder.append(rowParts.joinToString(",")).append("\n")
        }
        return builder.toString()
    }

    private fun buildHtmlString(title: String, sheet: Sheet): String {
        var maxCol = 4
        var maxRow = 9
        sheet.cells.keys.forEach { ref ->
            FormulaParser.parseCellReference(ref)?.let { (col, row) ->
                if (col > maxCol) maxCol = col
                if (row > maxRow) maxRow = row
            }
        }
        val html = java.lang.StringBuilder()
        html.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>")
        html.append(title)
        html.append(" - ")
        html.append(sheet.name)
        html.append("</title>\n<style>\n")
        html.append("    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f9f9f9; padding: 24px; color: #333; }\n")
        html.append("    .card { background: #fff; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.06); padding: 24px; max-width: 1200px; margin: 0 auto; }\n")
        html.append("    h1 { margin-top: 0; color: #1e3a8a; font-size: 24px; }\n")
        html.append("    .subtitle { color: #555; margin-bottom: 20px; font-size: 14px; border-bottom: 1px solid #eee; padding-bottom: 12px; }\n")
        html.append("    table { width: 100%; border-collapse: collapse; margin-top: 15px; table-layout: auto; }\n")
        html.append("    th, td { border: 1px solid #ddd; padding: 10px; font-size: 13px; min-width: 80px; }\n")
        html.append("    th { background-color: #f1f5f9; color: #475569; font-weight: 600; text-align: center; }\n")
        html.append("    .row-header { background-color: #f1f5f9; font-weight: 600; text-align: center; width: 40px; }\n")
        html.append("    .footer { text-align: center; font-size: 11px; color: #999; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px; }\n")
        html.append("</style>\n</head>\n<body>\n<div class=\"card\">\n    <h1>")
        html.append(title)
        html.append("</h1>\n    <div class=\"subtitle\">Direct-Saved Offline Report | Sheet: ")
        html.append(sheet.name)
        html.append(" | Generated: ")
        html.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        html.append("</div>\n    <table>\n        <thead>\n            <tr>\n                <th></th>\n")
        for (c in 0..maxCol) {
            html.append("                <th>").append(FormulaParser.getColumnLabel(c)).append("</th>\n")
        }
        html.append("            </tr>\n        </thead>\n        <tbody>\n")
        for (r in 0..maxRow) {
            html.append("            <tr>\n                <td class=\"row-header\">").append(r + 1).append("</td>\n")
            for (c in 0..maxCol) {
                val ref = "${FormulaParser.getColumnLabel(c)}${r + 1}"
                val cell = sheet.cells[ref]
                val v = cell?.value ?: ""
                html.append("                <td>").append(v).append("</td>\n")
            }
            html.append("            </tr>\n")
        }
        html.append("        </tbody>\n    </table>\n    <div class=\"footer\">NexSheet Offline Engine. Prince AR Abdur Rahman.</div>\n</div>\n</body>\n</html>")
        return html.toString()
    }

    fun getVersionsForFile(fileId: Long): Flow<List<SpreadsheetVersion>> = dao.getVersionsForFile(fileId)

    suspend fun saveVersionSnapshot(fileId: Long, sheets: List<Sheet>) = withContext(Dispatchers.IO) {
        val contentJson = Sheet.serializeList(sheets)
        dao.insertVersion(
            SpreadsheetVersion(
                fileId = fileId,
                timestamp = System.currentTimeMillis(),
                sheetsJson = contentJson
            )
        )
    }

    suspend fun restoreVersionSnapshot(fileId: Long, version: SpreadsheetVersion) = withContext(Dispatchers.IO) {
        dao.insertContent(SpreadsheetContent(fileId, version.sheetsJson))
        val file = dao.getFileByIdSuspend(fileId)
        if (file != null) {
            dao.updateFile(file.copy(lastModified = System.currentTimeMillis()))
        }
    }
}
