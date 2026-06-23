package com.example.data.dao

import androidx.room.*
import com.example.data.entity.SpreadsheetFile
import com.example.data.entity.SpreadsheetContent
import com.example.data.entity.SpreadsheetVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface SpreadsheetDao {
    @Query("SELECT * FROM spreadsheet_files ORDER BY lastModified DESC")
    fun getAllFiles(): Flow<List<SpreadsheetFile>>

    @Query("SELECT * FROM spreadsheet_files WHERE id = :id LIMIT 1")
    fun getFileById(id: Long): Flow<SpreadsheetFile?>

    @Query("SELECT * FROM spreadsheet_files WHERE id = :id LIMIT 1")
    suspend fun getFileByIdSuspend(id: Long): SpreadsheetFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SpreadsheetFile): Long

    @Update
    suspend fun updateFile(file: SpreadsheetFile)

    @Delete
    suspend fun deleteFile(file: SpreadsheetFile)

    @Query("SELECT * FROM spreadsheet_contents WHERE fileId = :fileId LIMIT 1")
    suspend fun getContentForFileSuspend(fileId: Long): SpreadsheetContent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: SpreadsheetContent)

    @Query("DELETE FROM spreadsheet_contents WHERE fileId = :fileId")
    suspend fun deleteContent(fileId: Long)

    @Query("SELECT * FROM spreadsheet_versions WHERE fileId = :fileId ORDER BY timestamp DESC")
    fun getVersionsForFile(fileId: Long): Flow<List<SpreadsheetVersion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: SpreadsheetVersion)

    @Query("DELETE FROM spreadsheet_versions WHERE fileId = :fileId")
    suspend fun deleteVersionsForFile(fileId: Long)
}
