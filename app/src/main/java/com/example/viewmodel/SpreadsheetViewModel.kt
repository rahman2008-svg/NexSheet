package com.example.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.SpreadsheetFile
import com.example.data.entity.SpreadsheetVersion
import com.example.data.model.CellData
import com.example.data.model.CellStyle
import com.example.data.model.FormulaParser
import com.example.data.model.Sheet
import com.example.data.repository.SpreadsheetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class SpreadsheetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SpreadsheetRepository

    // Full file lists
    val allFiles: StateFlow<List<SpreadsheetFile>>

    // Current State for active sheet editing
    var activeFile by mutableStateOf<SpreadsheetFile?>(null)
        private set

    var activeSheets by mutableStateOf<List<Sheet>>(emptyList())
        private set

    var activeSheetIndex by mutableIntStateOf(0)

    var selectedCellRef by mutableStateOf("A1")

    // Range editing / selecting (e.g. "A3", "C7")
    var selectedRangeEndRef by mutableStateOf<String?>(null)

    // Formula text currently in input bar
    var formulaInputText by mutableStateOf("")

    // List of local database versions for backup restore
    var fileVersions by mutableStateOf<List<SpreadsheetVersion>>(emptyList())
        private set

    // Category organization state
    var selectedCategoryFilter by mutableStateOf("All")

    // Zoom scale multiplier (50% to 200%)
    var zoomScale by mutableStateOf(1.0f)

    // Option to show or hide gridlines
    var showGridLines by mutableStateOf(true)

    // Viewport window starting points for virtualization to keep Compose layouts in valid Bounds
    var viewportColStart by mutableStateOf(0)
    var viewportRowStart by mutableStateOf(0)

    fun adjustViewportToSelectedCell() {
        val parsed = FormulaParser.parseCellReference(selectedCellRef) ?: return
        val col = parsed.first
        val row = parsed.second

        // Col bounds check: ensure the cell is inside the col start window
        val currentMaxColIndex = viewportColStart + 35
        if (col < viewportColStart) {
            viewportColStart = maxOf(0, col - 3)
        } else if (col > currentMaxColIndex) {
            viewportColStart = minOf(16384 - 36, col - 12)
        }

        // Row bounds check: ensure the cell is inside the row start window
        val currentMaxRowIndex = viewportRowStart + 80
        if (row < viewportRowStart) {
            viewportRowStart = maxOf(0, row - 5)
        } else if (row > currentMaxRowIndex) {
            viewportRowStart = minOf(9999 - 81, row - 20)
        }
    }

    // PIN lock verification states
    var pinRequiredFileId by mutableStateOf<Long?>(null)
    var pinErrorText by mutableStateOf("")

    // List of physical files direct-saved to APP Internal Storage
    var internalStorageFiles by mutableStateOf<List<java.io.File>>(emptyList())
        private set

    fun refreshInternalStorageFiles() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val exportDir = java.io.File(context.filesDir, "internal_spreadsheets")
                if (exportDir.exists() && exportDir.isDirectory) {
                    val filesList = exportDir.listFiles()?.toList() ?: emptyList()
                    internalStorageFiles = filesList.sortedByDescending { it.lastModified() }
                } else {
                    internalStorageFiles = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.spreadsheetDao()
        repository = SpreadsheetRepository(dao, application)
        allFiles = repository.allFiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        refreshInternalStorageFiles()
    }

    /**
     * Category list compiled dynamically from existing spreadsheet files
     */
    fun getCategories(files: List<SpreadsheetFile>): List<String> {
        val list = files.map { it.category }.distinct().toMutableList()
        if ("All" !in list) list.add(0, "All")
        if ("Budget" !in list) list.add("Budget")
        if ("Attendance" !in list) list.add("Attendance")
        if ("Report" !in list) list.add("Report")
        if ("Invoice" !in list) list.add("Invoice")
        return list
    }

    /**
     * Filters list based on selected tab category
     */
    fun filteredFiles(files: List<SpreadsheetFile>): List<SpreadsheetFile> {
        if (selectedCategoryFilter == "All") return files
        return files.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    fun selectCell(ref: String) {
        selectedCellRef = ref
        selectedRangeEndRef = null
        val cell = getActiveSheet()?.cells?.get(ref)
        formulaInputText = cell?.formula?.ifEmpty { cell.value } ?: cell?.value ?: ""
        adjustViewportToSelectedCell()
    }

    fun selectRange(start: String, end: String) {
        selectedCellRef = start
        selectedRangeEndRef = end
        adjustViewportToSelectedCell()
    }

    fun clearRangeSelection() {
        selectedRangeEndRef = null
    }

    fun getActiveSheet(): Sheet? {
        if (activeSheets.isEmpty() || activeSheetIndex < 0 || activeSheetIndex >= activeSheets.size) return null
        return activeSheets[activeSheetIndex]
    }

    /**
     * Resolves all formulas in the active sheet to display updated cached values
     */
    private fun reevaluateActiveSheet(sheetsList: List<Sheet>): List<Sheet> {
        if (sheetsList.isEmpty() || activeSheetIndex < 0 || activeSheetIndex >= sheetsList.size) return sheetsList
        val currentSheet = sheetsList[activeSheetIndex]
        val resolvedCells = mutableMapOf<String, CellData>()

        // Copy everything first
        resolvedCells.putAll(currentSheet.cells)

        // Evaluate each cell
        currentSheet.cells.forEach { (ref, cell) ->
            if (cell.formula.startsWith("=")) {
                val evaluatedStr = FormulaParser.evaluateCell(ref, cell, currentSheet.cells)
                resolvedCells[ref] = cell.copy(value = evaluatedStr)
            }
        }

        val updatedSheet = currentSheet.copy(cells = resolvedCells)
        val copyOfList = sheetsList.toMutableList()
        copyOfList[activeSheetIndex] = updatedSheet
        return copyOfList
    }

    /**
     * Updates content in active selected cell and executes evaluation
     */
    fun updateActiveCell(text: String) {
        val fileId = activeFile?.id ?: return
        val sheetsCopy = activeSheets.toMutableList()
        val currentSheet = getActiveSheet() ?: return

        val trimmedText = text.trim()
        val isFormula = trimmedText.startsWith("=")

        val existingCell = currentSheet.cells[selectedCellRef] ?: CellData()
        val updatedCell = if (isFormula) {
            existingCell.copy(formula = trimmedText, value = "")
        } else {
            existingCell.copy(formula = "", value = trimmedText)
        }

        val updatedCells = currentSheet.cells.toMutableMap()
        updatedCells[selectedCellRef] = updatedCell

        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        // Recompute formulas across the sheets
        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets
        formulaInputText = text

        // Save progress to local Room Database
        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    /**
     * Toggle Bold text format on selected cell
     */
    fun toggleSelectedCellBold() {
        updateSelectedCellStyle { it.copy(bold = !it.bold) }
    }

    /**
     * Toggle Italic text format on selected cell
     */
    fun toggleSelectedCellItalic() {
        updateSelectedCellStyle { it.copy(italic = !it.italic) }
    }

    /**
     * Change alignment formatting of active cell
     */
    fun updateSelectedCellAlignment(align: String) {
        updateSelectedCellStyle { it.copy(textAlign = align) }
    }

    /**
     * Apply background fill color to selected cell
     */
    fun updateSelectedCellBackgroundColor(colorHex: String) {
        updateSelectedCellStyle { it.copy(backgroundColorHex = colorHex) }
    }

    /**
     * Apply text color to selected cell
     */
    fun updateSelectedCellTextColor(colorHex: String) {
        updateSelectedCellStyle { it.copy(textColorHex = colorHex) }
    }

    /**
     * Increase/Decrease Font Size of active cell
     */
    fun updateSelectedCellFontSize(size: Int) {
        updateSelectedCellStyle { it.copy(fontSize = size) }
    }

    private fun updateSelectedCellStyle(block: (CellStyle) -> CellStyle) {
        val fileId = activeFile?.id ?: return
        val currentSheet = getActiveSheet() ?: return
        val cell = currentSheet.cells[selectedCellRef] ?: CellData()
        val updatedStyle = block(cell.style)

        val updatedCells = currentSheet.cells.toMutableMap()
        updatedCells[selectedCellRef] = cell.copy(style = updatedStyle)

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets

        // Save
        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    /**
     * Smart formula suggestion rule-based helper
     */
    fun getSuggestedFormulas(): List<String> {
        // Suggests SUM or AVERAGE depending on cursor coordinate surrounding
        val ref = selectedCellRef
        val coords = FormulaParser.parseCellReference(ref) ?: return listOf("=SUM(", "=AVERAGE(", "=MIN(", "=MAX(", "=COUNT(")
        val col = coords.first
        val row = coords.second

        val colLabel = FormulaParser.getColumnLabel(col)

        // Offer formulas relative to column context
        return if (row > 1) {
            val startRef = "${colLabel}1"
            val endRef = "${colLabel}$row"
            listOf(
                "=SUM($startRef:$endRef)",
                "=AVERAGE($startRef:$endRef)",
                "=MAX($startRef:$endRef)",
                "=MIN($startRef:$endRef)",
                "=COUNT($startRef:$endRef)"
            )
        } else {
            listOf("=SUM(", "=AVERAGE(", "=COUNT(", "=MIN(", "=MAX(")
        }
    }

    /**
     * Dynamic Smart Pattern Auto-fill
     * Autofills numbers/dates linearly downwards based on previous selection context.
     */
    fun performAutoFillDown() {
        val currentSheet = getActiveSheet() ?: return
        val parsedRef = FormulaParser.parseCellReference(selectedCellRef) ?: return
        val col = parsedRef.first
        val row = parsedRef.second

        val colLabel = FormulaParser.getColumnLabel(col)

        // Read current cell value
        val currentVal = currentSheet.cells[selectedCellRef]?.value ?: ""
        val numericVal = currentVal.toDoubleOrNull() ?: return

        // Read previous cell value if exists to identify step pattern
        val prevRef = "$colLabel$row" // Row is 0-based, so row is the row above (which is index row - 1, label is index row + 1)
        val prevVal = if (row > 0) currentSheet.cells["$colLabel$row"]?.value ?: "" else ""
        val step = if (prevVal.isNotEmpty()) {
            val prevNum = prevVal.toDoubleOrNull()
            if (prevNum != null) numericVal - prevNum else 1.0
        } else {
            1.0
        }

        // Autofill 5 cells down
        val sheetsCopy = activeSheets.toMutableList()
        val updatedCells = currentSheet.cells.toMutableMap()

        var currentStepNum = numericVal
        for (i in 1..5) {
            currentStepNum += step
            val fillRef = "$colLabel${row + 1 + i}" // Row coordinate is row index + 1
            val fillStr = if (currentStepNum % 1.0 == 0.0) currentStepNum.toLong().toString() else String.format(Locale.US, "%.2f", currentStepNum)
            updatedCells[fillRef] = (updatedCells[fillRef] ?: CellData()).copy(value = fillStr, formula = "")
        }

        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)
        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets

        viewModelScope.launch {
            repository.saveContentForFile(activeFile?.id ?: return@launch, evaluatedSheets)
        }
    }

    /**
     * Multiple Sheets Controls: Add Sheet
     */
    fun addNewSheet() {
        val fileId = activeFile?.id ?: return
        val currentSheets = activeSheets.toMutableList()
        val nextNumber = currentSheets.size + 1
        currentSheets.add(Sheet("Sheet $nextNumber", emptyMap()))

        activeSheets = currentSheets
        activeSheetIndex = currentSheets.size - 1

        viewModelScope.launch {
            repository.saveContentForFile(fileId, currentSheets)
        }
    }

    /**
     * Multiple Sheets Controls: Rename Sheet
     */
    fun renameActiveSheet(newName: String) {
        val fileId = activeFile?.id ?: return
        if (newName.isBlank() || activeSheets.isEmpty()) return
        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = sheetsCopy[activeSheetIndex].copy(name = newName)

        activeSheets = sheetsCopy

        viewModelScope.launch {
            repository.saveContentForFile(fileId, sheetsCopy)
        }
    }

    /**
     * Multiple Sheets Controls: Delete Sheet
     */
    fun deleteActiveSheet() {
        val fileId = activeFile?.id ?: return
        if (activeSheets.size <= 1) return // Keep at least one sheet

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy.removeAt(activeSheetIndex)

        activeSheets = sheetsCopy
        activeSheetIndex = maxOf(0, activeSheetIndex - 1)

        viewModelScope.launch {
            repository.saveContentForFile(fileId, sheetsCopy)
        }
    }

    /**
     * Open spreadsheet file for edit
     */
    fun openFile(file: SpreadsheetFile) {
        if (file.hasPin) {
            pinRequiredFileId = file.id
            pinErrorText = ""
            return
        }

        forceOpenFileWithoutPinCheck(file)
    }

    fun submitPinUnlock(pin: String) {
        val fileId = pinRequiredFileId ?: return
        viewModelScope.launch {
            val fileObj = repository.getFileByIdSuspend(fileId)
            if (fileObj != null) {
                if (fileObj.pin == pin) {
                    // Success!
                    pinRequiredFileId = null
                    pinErrorText = ""
                    forceOpenFileWithoutPinCheck(fileObj)
                } else {
                    pinErrorText = "Incorrect security PIN. Please try again."
                }
            }
        }
    }

    private fun forceOpenFileWithoutPinCheck(file: SpreadsheetFile) {
        viewModelScope.launch {
            activeFile = file
            val loadedSheets = repository.getContentForFile(file.id)
            activeSheets = if (loadedSheets.isEmpty()) {
                listOf(Sheet("Sheet 1", emptyMap()))
            } else {
                loadedSheets
            }
            activeSheetIndex = 0
            selectedCellRef = "A1"
            selectedRangeEndRef = null
            formulaInputText = activeSheets.firstOrNull()?.cells?.get("A1")?.value ?: ""

            // Load backup snapshots
            repository.getVersionsForFile(file.id).collect {
                fileVersions = it
            }
        }
    }

    fun clearActiveCell() {
        val fileId = activeFile?.id ?: return
        val currentSheet = getActiveSheet() ?: return
        val updatedCells = currentSheet.cells.toMutableMap()
        updatedCells.remove(selectedCellRef)

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets
        formulaInputText = ""

        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    fun deleteActiveCellRow() {
        val fileId = activeFile?.id ?: return
        val currentSheet = getActiveSheet() ?: return
        val parsed = FormulaParser.parseCellReference(selectedCellRef) ?: return
        val targetRowIndex = parsed.second // 0-based

        val updatedCells = mutableMapOf<String, CellData>()

        currentSheet.cells.forEach { (ref, cell) ->
            val cellCoords = FormulaParser.parseCellReference(ref) ?: return@forEach
            val col = cellCoords.first
            val row = cellCoords.second

            if (row < targetRowIndex) {
                updatedCells[ref] = cell
            } else if (row > targetRowIndex) {
                val shiftedRef = "${FormulaParser.getColumnLabel(col)}$row"
                updatedCells[shiftedRef] = cell
            }
        }

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets
        selectCell(selectedCellRef)

        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    fun deleteActiveCellColumn() {
        val fileId = activeFile?.id ?: return
        val currentSheet = getActiveSheet() ?: return
        val parsed = FormulaParser.parseCellReference(selectedCellRef) ?: return
        val targetColIndex = parsed.first // 0-based

        val updatedCells = mutableMapOf<String, CellData>()

        currentSheet.cells.forEach { (ref, cell) ->
            val cellCoords = FormulaParser.parseCellReference(ref) ?: return@forEach
            val col = cellCoords.first
            val row = cellCoords.second

            if (col < targetColIndex) {
                updatedCells[ref] = cell
            } else if (col > targetColIndex) {
                val shiftedRef = "${FormulaParser.getColumnLabel(col - 1)}${row + 1}"
                updatedCells[shiftedRef] = cell
            }
        }

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets
        selectCell(selectedCellRef)

        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    fun insertRowAboveActiveCell() {
        val fileId = activeFile?.id ?: return
        val currentSheet = getActiveSheet() ?: return
        val parsed = FormulaParser.parseCellReference(selectedCellRef) ?: return
        val targetRowIndex = parsed.second // 0-based

        val updatedCells = mutableMapOf<String, CellData>()

        currentSheet.cells.forEach { (ref, cell) ->
            val cellCoords = FormulaParser.parseCellReference(ref) ?: return@forEach
            val col = cellCoords.first
            val row = cellCoords.second

            if (row < targetRowIndex) {
                updatedCells[ref] = cell
            } else {
                val shiftedRef = "${FormulaParser.getColumnLabel(col)}${row + 2}"
                updatedCells[shiftedRef] = cell
            }
        }

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets

        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    fun insertColumnLeftOfActiveCell() {
        val fileId = activeFile?.id ?: return
        val currentSheet = getActiveSheet() ?: return
        val parsed = FormulaParser.parseCellReference(selectedCellRef) ?: return
        val targetColIndex = parsed.first // 0-based

        val updatedCells = mutableMapOf<String, CellData>()

        currentSheet.cells.forEach { (ref, cell) ->
            val cellCoords = FormulaParser.parseCellReference(ref) ?: return@forEach
            val col = cellCoords.first
            val row = cellCoords.second

            if (col < targetColIndex) {
                updatedCells[ref] = cell
            } else {
                val shiftedRef = "${FormulaParser.getColumnLabel(col + 1)}${row + 1}"
                updatedCells[shiftedRef] = cell
            }
        }

        val sheetsCopy = activeSheets.toMutableList()
        sheetsCopy[activeSheetIndex] = currentSheet.copy(cells = updatedCells)

        val evaluatedSheets = reevaluateActiveSheet(sheetsCopy)
        activeSheets = evaluatedSheets

        viewModelScope.launch {
            repository.saveContentForFile(fileId, evaluatedSheets)
        }
    }

    fun closeActiveFile() {
        activeFile = null
        activeSheets = emptyList()
        activeSheetIndex = 0
        fileVersions = emptyList()
        selectedRangeEndRef = null
    }

    /**
     * Local Version System: Creates local version history checkpoint
     */
    fun saveBackupSnapshot() {
        val file = activeFile ?: return
        viewModelScope.launch {
            repository.saveVersionSnapshot(file.id, activeSheets)
        }
    }

    /**
     * Restores workbook to previously saved room database checkpoint snapshot
     */
    fun restoreBackupSnapshot(version: SpreadsheetVersion) {
        val file = activeFile ?: return
        viewModelScope.launch {
            repository.restoreVersionSnapshot(file.id, version)
            // Reload
            val loadedSheets = repository.getContentForFile(file.id)
            activeSheets = loadedSheets.ifEmpty { listOf(Sheet("Sheet 1", emptyMap())) }
            activeSheetIndex = 0
            selectCell("A1")
        }
    }

    /**
     * Creates standard spreadsheets based on templates
     */
    fun createSpreadsheetFromTemplate(title: String, category: String, templateType: String) {
        viewModelScope.launch {
            val initialCells = when (templateType) {
                "BUDGET" -> getBudgetTrackerTemplateCells()
                "ATTENDANCE" -> getAttendanceSheetTemplateCells()
                "RESULT" -> getResultSheetTemplateCells()
                "INVOICE" -> getInvoiceTemplateCells()
                "EXPENSE" -> getExpenseTrackerTemplateCells()
                else -> emptyMap()
            }
            val firstSheet = Sheet("Sheet 1", initialCells)
            val sheets = listOf(firstSheet)

            val newId = repository.createFile(title, category, sheets)
            val updatedFile = repository.getFileByIdSuspend(newId)
            if (updatedFile != null) {
                forceOpenFileWithoutPinCheck(updatedFile)
                // Save an initial version snapshot
                repository.saveVersionSnapshot(newId, sheets)
            }
        }
    }

    /**
     * Fast Delete Spreadsheet File
     */
    fun deleteFile(file: SpreadsheetFile) {
        viewModelScope.launch {
            repository.deleteFile(file)
            if (activeFile?.id == file.id) {
                closeActiveFile()
            }
        }
    }

    /**
     * Update spreadsheet file metadata (Rename / Set security PIN)
     */
    fun renameSpreadsheetFile(newName: String) {
        val file = activeFile ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = file.copy(title = newName)
            repository.updateFile(updated)
            activeFile = updated
        }
    }

    fun setSpreadsheetPinLock(hasPin: Boolean, pinCode: String?) {
        val file = activeFile ?: return
        viewModelScope.launch {
            val updated = file.copy(hasPin = hasPin, pin = pinCode)
            repository.updateFile(updated)
            activeFile = updated
        }
    }

    /**
     * Basic Aggregation (Pivot-like SUMMARY features)
     */
    fun getSelectionRangeSummary(): Map<String, String>? {
        val start = selectedCellRef
        val end = selectedRangeEndRef ?: return null
        val currentSheet = getActiveSheet() ?: return null

        val startRef = FormulaParser.parseCellReference(start) ?: return null
        val endRef = FormulaParser.parseCellReference(end) ?: return null

        val minCol = minOf(startRef.first, endRef.first)
        val maxCol = maxOf(startRef.first, endRef.first)
        val minRow = minOf(startRef.second, endRef.second)
        val maxRow = maxOf(startRef.second, endRef.second)

        val numbers = mutableListOf<Double>()
        var count = 0
        var emptyCount = 0

        for (col in minCol..maxCol) {
            for (row in minRow..maxRow) {
                val label = "${FormulaParser.getColumnLabel(col)}${row + 1}"
                count++
                val valueStr = currentSheet.cells[label]?.value ?: ""
                if (valueStr.isEmpty()) {
                    emptyCount++
                } else {
                    valueStr.toDoubleOrNull()?.let { numbers.add(it) }
                }
            }
        }

        if (numbers.isEmpty()) {
            return mapOf(
                "Total Selected" to count.toString(),
                "Blank Cells" to emptyCount.toString(),
                "Numeric Values" to "0",
                "SUM" to "0",
                "AVERAGE" to "0"
            )
        }

        return mapOf(
            "Selected Range" to "$start:$end",
            "Total Cells" to count.toString(),
            "Numeric Count" to numbers.size.toString(),
            "Empty Count" to emptyCount.toString(),
            "SUM" to formatDoubleNicely(numbers.sum()),
            "AVERAGE" to formatDoubleNicely(numbers.average()),
            "MIN" to formatDoubleNicely(numbers.minOrNull() ?: 0.0),
            "MAX" to formatDoubleNicely(numbers.maxOrNull() ?: 0.0)
        )
    }

    private fun formatDoubleNicely(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    /**
     * EXPORT TO CSV System (Offline string rendering)
     */
    fun exportToCsvContent(): String {
        val currentSheet = getActiveSheet() ?: return ""
        val builder = java.lang.StringBuilder()

        // Scan columns and rows up to max limits dynamically to export full block
        var maxCol = 1
        var maxRow = 1

        currentSheet.cells.keys.forEach { ref ->
            FormulaParser.parseCellReference(ref)?.let { (col, row) ->
                if (col > maxCol) maxCol = col
                if (row > maxRow) maxRow = row
            }
        }

        for (r in 0..maxRow) {
            val rowParts = mutableListOf<String>()
            for (c in 0..maxCol) {
                val ref = "${FormulaParser.getColumnLabel(c)}${r + 1}"
                val valStr = currentSheet.cells[ref]?.value ?: ""
                // Escape commas or double quotes for proper CSV representation
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

    /**
     * EXPORT TO PDF/HTML PREVIEW System (Generates CSS styled offline HTML)
     */
    fun exportToHtmlPreview(): String {
        val currentSheet = getActiveSheet() ?: return "<h3>No sheet available</h3>"
        val title = activeFile?.title ?: "NexSheet File"
        val sheetName = currentSheet.name

        // Identify spreadsheet dimensions to print nice aligned grid
        var maxCol = 4
        var maxRow = 9
        currentSheet.cells.keys.forEach { ref ->
            FormulaParser.parseCellReference(ref)?.let { (col, row) ->
                if (col > maxCol) maxCol = col
                if (row > maxRow) maxRow = row
            }
        }

        val html = StringBuilder()
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <title>$title - $sheetName</title>
            <style>
                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f9f9f9; padding: 24px; color: #333; }
                .card { background: #fff; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.06); padding: 24px; max-width: 1200px; margin: 0 auto; }
                h1 { margin-top: 0; color: #1e3a8a; font-size: 24px; }
                .subtitle { color: #555; margin-bottom: 20px; font-size: 14px; border-bottom: 1px solid #eee; padding-bottom: 12px; }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; table-layout: auto; }
                th, td { border: 1px solid #ddd; padding: 10px; font-size: 13px; min-width: 80px; }
                th { background-color: #f1f5f9; color: #475569; font-weight: 600; text-align: center; }
                .row-header { background-color: #f1f5f9; font-weight: 600; text-align: center; width: 40px; }
                .text-left { text-align: left; }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .footer { text-align: center; font-size: 11px; color: #999; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px; }
            </style>
            </head>
            <body>
            <div class="card">
                <h1>$title</h1>
                <div class="subtitle">Spreadsheet Workbook Report | Active Tab: <strong>$sheetName</strong> | Generated completely offline via NexSheet</div>
                <table>
                    <thead>
                        <tr>
                            <th></th>
        """.trimIndent())

        // Print column headers A, B, C...
        for (c in 0..maxCol) {
            html.append("<th>${FormulaParser.getColumnLabel(c)}</th>")
        }
        html.append("</tr></thead><tbody>")

        // Print grid cells row by row
        for (r in 0..maxRow) {
            html.append("<tr>")
            html.append("<td class=\"row-header\">${r + 1}</td>")
            for (c in 0..maxCol) {
                val ref = "${FormulaParser.getColumnLabel(c)}${r + 1}"
                val cell = currentSheet.cells[ref]
                val valueStr = cell?.value ?: ""

                // Get custom styles alignment class
                val alignClass = when (cell?.style?.textAlign ?: "LEFT") {
                    "CENTER" -> "text-center"
                    "RIGHT" -> "text-right"
                    else -> "text-left"
                }

                val inlineStyle = StringBuilder()
                if (cell?.style?.bold == true) inlineStyle.append("font-weight: bold;")
                if (cell?.style?.italic == true) inlineStyle.append("font-style: italic;")
                if (cell?.style?.backgroundColorHex != null && cell.style.backgroundColorHex != "#FFFFFF") {
                    inlineStyle.append("background-color: ${cell.style.backgroundColorHex};")
                }
                if (cell?.style?.textColorHex != null && cell.style.textColorHex != "#000000") {
                    inlineStyle.append("color: ${cell.style.textColorHex};")
                }

                html.append("<td class=\"$alignClass\" style=\"$inlineStyle\">$valueStr</td>")
            }
            html.append("</tr>")
        }

        html.append("""
                </table>
                <div class="footer">
                    Produced Offline by NexSheet (Productivity Suite) &copy; 2026 NexVora Lab's Ofc.
                </div>
            </div>
            </body>
            </html>
        """.trimIndent())
        return html.toString()
    }

    /**
     * Local spreadsheet formula solver generator helpers
     */
    private fun getBudgetTrackerTemplateCells(): Map<String, CellData> {
        val baseCells = mutableMapOf<String, CellData>()
        baseCells["A1"] = CellData(value = "Monthly Budget Spreadsheet", style = CellStyle(bold = true, fontSize = 16))

        baseCells["A3"] = CellData(value = "Expense Category", style = CellStyle(bold = true))
        baseCells["B3"] = CellData(value = "Planned Expense", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["C3"] = CellData(value = "Actual Spent", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D3"] = CellData(value = "Total Variance", style = CellStyle(bold = true, textAlign = "RIGHT"))

        val details = listOf(
            Triple("Housing Rent", "1200", "1200"),
            Triple("Groceries & Stores", "450", "480"),
            Triple("Utilities & Wi-Fi", "250", "235"),
            Triple("Transit Fare", "150", "120"),
            Triple("Entertainment", "100", "135"),
            Triple("Insurance Plan", "180", "180"),
            Triple("Subscriptions", "50", "55")
        )

        details.forEachIndexed { idx, pair ->
            val row = 4 + idx
            baseCells["A$row"] = CellData(value = pair.first)
            baseCells["B$row"] = CellData(value = pair.second, style = CellStyle(textAlign = "RIGHT"))
            baseCells["C$row"] = CellData(value = pair.third, style = CellStyle(textAlign = "RIGHT"))
            baseCells["D$row"] = CellData(formula = "=B$row-C$row", style = CellStyle(textAlign = "RIGHT"))
        }

        val totalRow = 4 + details.size
        baseCells["A$totalRow"] = CellData(value = "Total Sum", style = CellStyle(bold = true))
        baseCells["B$totalRow"] = CellData(formula = "=SUM(B4:B10)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["C$totalRow"] = CellData(formula = "=SUM(C4:C10)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D$totalRow"] = CellData(formula = "=SUM(D4:D10)", style = CellStyle(bold = true, textAlign = "RIGHT"))

        return baseCells
    }

    private fun getAttendanceSheetTemplateCells(): Map<String, CellData> {
        val baseCells = mutableMapOf<String, CellData>()
        baseCells["A1"] = CellData(value = "Class Attendance Sheet", style = CellStyle(bold = true, fontSize = 16))

        // Headers
        baseCells["A3"] = CellData(value = "Student Name", style = CellStyle(bold = true))
        baseCells["B3"] = CellData(value = "Mon", style = CellStyle(bold = true, textAlign = "CENTER"))
        baseCells["C3"] = CellData(value = "Tue", style = CellStyle(bold = true, textAlign = "CENTER"))
        baseCells["D3"] = CellData(value = "Wed", style = CellStyle(bold = true, textAlign = "CENTER"))
        baseCells["E3"] = CellData(value = "Thu", style = CellStyle(bold = true, textAlign = "CENTER"))
        baseCells["F3"] = CellData(value = "Fri", style = CellStyle(bold = true, textAlign = "CENTER"))
        baseCells["G3"] = CellData(value = "Total Attended", style = CellStyle(bold = true, textAlign = "CENTER"))

        val students = listOf("Abdur Rahman", "Kazi Rahim", "Salma Khatun", "Zakir Hussain", "Fatima Islam")
        students.forEachIndexed { idx, name ->
            val row = 4 + idx
            baseCells["A$row"] = CellData(value = name)
            baseCells["B$row"] = CellData(value = "1", style = CellStyle(textAlign = "CENTER"))
            baseCells["C$row"] = CellData(value = "1", style = CellStyle(textAlign = "CENTER"))
            baseCells["D$row"] = CellData(value = (if (idx % 2 == 0) "1" else "0"), style = CellStyle(textAlign = "CENTER"))
            baseCells["E$row"] = CellData(value = "1", style = CellStyle(textAlign = "CENTER"))
            baseCells["F$row"] = CellData(value = "1", style = CellStyle(textAlign = "CENTER"))
            baseCells["G$row"] = CellData(formula = "=SUM(B$row:F$row)", style = CellStyle(bold = true, textAlign = "CENTER"))
        }

        return baseCells
    }

    private fun getResultSheetTemplateCells(): Map<String, CellData> {
        val baseCells = mutableMapOf<String, CellData>()
        baseCells["A1"] = CellData(value = "Term Examination Scores", style = CellStyle(bold = true, fontSize = 16))

        baseCells["A3"] = CellData(value = "Student Name", style = CellStyle(bold = true))
        baseCells["B3"] = CellData(value = "English", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["C3"] = CellData(value = "Bangla", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D3"] = CellData(value = "Mathematics", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["E3"] = CellData(value = "Science", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["F3"] = CellData(value = "Total Score", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["G3"] = CellData(value = "Average Grade", style = CellStyle(bold = true, textAlign = "RIGHT"))

        val grades = listOf(
            Quadruple("Amina Begum", "82", "94", "96", "88"),
            Quadruple("Chayan Roy", "74", "80", "88", "82"),
            Quadruple("Tasnim Kabir", "90", "85", "92", "94"),
            Quadruple("Babul Hossain", "68", "72", "70", "75"),
            Quadruple("Farhana Khan", "85", "88", "91", "89")
        )

        grades.forEachIndexed { idx, item ->
            val row = 4 + idx
            baseCells["A$row"] = CellData(value = item.name)
            baseCells["B$row"] = CellData(value = item.b1, style = CellStyle(textAlign = "RIGHT"))
            baseCells["C$row"] = CellData(value = item.b2, style = CellStyle(textAlign = "RIGHT"))
            baseCells["D$row"] = CellData(value = item.b3, style = CellStyle(textAlign = "RIGHT"))
            baseCells["E$row"] = CellData(value = item.b4, style = CellStyle(textAlign = "RIGHT"))
            baseCells["F$row"] = CellData(formula = "=SUM(B$row:E$row)", style = CellStyle(bold = true, textAlign = "RIGHT"))
            baseCells["G$row"] = CellData(formula = "=AVERAGE(B$row:E$row)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        }

        val footerRow = 4 + grades.size
        baseCells["A$footerRow"] = CellData(value = "Class Average", style = CellStyle(bold = true))
        baseCells["B$footerRow"] = CellData(formula = "=AVERAGE(B4:B8)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["C$footerRow"] = CellData(formula = "=AVERAGE(C4:C8)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D$footerRow"] = CellData(formula = "=AVERAGE(D4:D8)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["E$footerRow"] = CellData(formula = "=AVERAGE(E4:E8)", style = CellStyle(bold = true, textAlign = "RIGHT"))

        return baseCells
    }

    private fun getInvoiceTemplateCells(): Map<String, CellData> {
        val baseCells = mutableMapOf<String, CellData>()
        baseCells["A1"] = CellData(value = "PROJECT INVOICE", style = CellStyle(bold = true, fontSize = 16))

        baseCells["A3"] = CellData(value = "Client Name:", style = CellStyle(bold = true))
        baseCells["B3"] = CellData(value = "NexVora Lab's Ofc", style = CellStyle(bold = false))

        baseCells["A4"] = CellData(value = "Invoice Date:", style = CellStyle(bold = true))
        baseCells["B4"] = CellData(value = "2026-06-23", style = CellStyle(bold = false))

        // Items headers
        baseCells["A6"] = CellData(value = "Service Description", style = CellStyle(bold = true))
        baseCells["B6"] = CellData(value = "Unit Rate ($)", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["C6"] = CellData(value = "Qty / Hours", style = CellStyle(bold = true, textAlign = "CENTER"))
        baseCells["D6"] = CellData(value = "Line Total", style = CellStyle(bold = true, textAlign = "RIGHT"))

        val items = listOf(
            Triple("Custom Jetpack Compose UI Suite", "450", "2"),
            Triple("Local SQLite Room Database Setup", "300", "1"),
            Triple("Formulas Offline Engine Implementation", "500", "1")
        )

        items.forEachIndexed { idx, pair ->
            val row = 7 + idx
            baseCells["A$row"] = CellData(value = pair.first)
            baseCells["B$row"] = CellData(value = pair.second, style = CellStyle(textAlign = "RIGHT"))
            baseCells["C$row"] = CellData(value = pair.third, style = CellStyle(textAlign = "CENTER"))
            baseCells["D$row"] = CellData(formula = "=B$row*C$row", style = CellStyle(bold = true, textAlign = "RIGHT"))
        }

        // Subtotals block
        baseCells["C11"] = CellData(value = "Subtotal:", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D11"] = CellData(formula = "=SUM(D7:D9)", style = CellStyle(bold = true, textAlign = "RIGHT"))

        baseCells["C12"] = CellData(value = "VAT (15%):", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D12"] = CellData(formula = "=D11*0.15", style = CellStyle(bold = true, textAlign = "RIGHT"))

        baseCells["C13"] = CellData(value = "Grand Total Due:", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D13"] = CellData(formula = "=D11+D12", style = CellStyle(bold = true, textAlign = "RIGHT"))

        return baseCells
    }

    private fun getExpenseTrackerTemplateCells(): Map<String, CellData> {
        val baseCells = mutableMapOf<String, CellData>()
        baseCells["A1"] = CellData(value = "Monthly Expense Ledger", style = CellStyle(bold = true, fontSize = 16))

        baseCells["A3"] = CellData(value = "Purchase Date", style = CellStyle(bold = true))
        baseCells["B3"] = CellData(value = "Item Description", style = CellStyle(bold = true))
        baseCells["C3"] = CellData(value = "Category Tag", style = CellStyle(bold = true))
        baseCells["D3"] = CellData(value = "Amount Spent ($)", style = CellStyle(bold = true, textAlign = "RIGHT"))

        val items = listOf(
            Quadruple("2026-06-20", "Google Cloud Database Hosting", "Business", "45"),
            Quadruple("2026-06-21", "Premium Asset Vectors Pack", "Design", "15"),
            Quadruple("2026-06-22", "Coffee Shop Workshop", "Food", "8"),
            Quadruple("2026-06-23", "Figma Design Pro Subscription", "Software", "15"),
            Quadruple("2026-06-23", "Local Transit Commute", "Travel", "12")
        )

        items.forEachIndexed { idx, item ->
            val row = 4 + idx
            baseCells["A$row"] = CellData(value = item.name)
            baseCells["B$row"] = CellData(value = item.b1)
            baseCells["C$row"] = CellData(value = item.b2)
            baseCells["D$row"] = CellData(value = item.b3, style = CellStyle(textAlign = "RIGHT"))
        }

        val totalRow = 4 + items.size
        baseCells["C$totalRow"] = CellData(value = "Total Spent:", style = CellStyle(bold = true, textAlign = "RIGHT"))
        baseCells["D$totalRow"] = CellData(formula = "=SUM(D4:D8)", style = CellStyle(bold = true, textAlign = "RIGHT"))

        return baseCells
    }

    // Helper data structures for templates constructor
    private data class Quadruple(val name: String, val b1: String, val b2: String, val b3: String, val b4: String = "")
}
