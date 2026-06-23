package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spreadsheet_files")
data class SpreadsheetFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val hasPin: Boolean = false,
    val pin: String? = null,
    val category: String = "All" // folder organization (e.g. Budget, Work, Personal, School)
)

@Entity(tableName = "spreadsheet_contents")
data class SpreadsheetContent(
    @PrimaryKey val fileId: Long,
    val sheetsJson: String // Serialized List<Sheet>
)

@Entity(tableName = "spreadsheet_versions")
data class SpreadsheetVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val sheetsJson: String
)
