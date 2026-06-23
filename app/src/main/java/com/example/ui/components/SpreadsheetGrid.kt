package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CellData
import com.example.data.model.FormulaParser
import com.example.data.model.Sheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpreadsheetGrid(
    sheet: Sheet,
    selectedCellRef: String,
    onCellSelected: (String) -> Unit,
    onCellRangeSelected: (String, String) -> Unit,
    selectedRangeEndRef: String?,
    modifier: Modifier = Modifier,
    maxCols: Int = 16384,
    maxRows: Int = 9999,
    zoomScale: Float = 1.0f,
    showGridLines: Boolean = true,
    viewportColStart: Int = 0,
    onViewportColStartChanged: (Int) -> Unit = {},
    viewportRowStart: Int = 0,
    onViewportRowStartChanged: (Int) -> Unit = {}
) {
    val rowHeaderWidth = (45 * zoomScale).dp
    val colCellWidth = (100 * zoomScale).dp
    val cellHeight = (40 * zoomScale).dp

    // Native scroll states for scrolling inside the active viewport window of cells
    val horizontalScrollState = rememberScrollState()
    val verticalLazyListState = rememberLazyListState()

    // Drag / multiselection flags
    var selectionRangeStart by remember { mutableStateOf<String?>(null) }

    // Use a lightweight layout block width/height for composing elements (dynamic paging virtualization)
    val windowCols = 35
    val windowRows = 80

    val startCol = viewportColStart
    val endCol = minOf(maxCols - 1, viewportColStart + windowCols - 1)

    val startRow = viewportRowStart
    val endRow = minOf(maxRows - 1, viewportRowStart + windowRows - 1)

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        
        // 1. Column Headers (A, B, C...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ) {
            // Spacer for Row Numbers Header on top-left intersection
            Box(
                modifier = Modifier
                    .width(rowHeaderWidth)
                    .height(cellHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ref",
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = (11 * zoomScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scrollable letters A, B, C...
            Row(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
                    .height(cellHeight)
            ) {
                for (col in startCol..endCol) {
                    val label = FormulaParser.getColumnLabel(col)
                    Box(
                        modifier = Modifier
                            .width(colCellWidth)
                            .fillMaxHeight()
                            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = (13 * zoomScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Row Grid Body (LazyColumn vertically)
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            
            // 2a. Frozen Left Column containing Row numbers (1, 2, 3...)
            LazyColumn(
                state = verticalLazyListState,
                userScrollEnabled = false, // driven by main list
                modifier = Modifier
                    .width(rowHeaderWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                items(endRow - startRow + 1) { index ->
                    val rowIndex = startRow + index
                    Box(
                        modifier = Modifier
                            .width(rowHeaderWidth)
                            .height(cellHeight)
                            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${rowIndex + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = (12 * zoomScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2b. Scrollable Central Cells Matrix Columns (LazyColumn of Row components)
            LazyColumn(
                state = verticalLazyListState,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                items(endRow - startRow + 1) { index ->
                    val rowIndex = startRow + index
                    Row(
                        modifier = Modifier
                            .horizontalScroll(horizontalScrollState)
                            .height(cellHeight)
                    ) {
                        for (colIndex in startCol..endCol) {
                            val colLabel = FormulaParser.getColumnLabel(colIndex)
                            val cellRef = "$colLabel${rowIndex + 1}"
                            
                            val cellData = sheet.cells[cellRef] ?: CellData()
                            val isSelected = cellRef == selectedCellRef
                            val isRangeHighlight = isCellInRange(cellRef, selectedCellRef, selectedRangeEndRef)

                            // Check and parsing custom cell Styles
                            val cellStyle = cellData.style
                            
                            // Adaptive backgrounds to make theme switching look polished
                            val defaultBackgroundFallback = MaterialTheme.colorScheme.surface
                            val isDarkThemeActive = androidx.compose.foundation.isSystemInDarkTheme()
                            
                            val cellBackgroundParsed = parseHexToComposeColor(cellStyle.backgroundColorHex, Color.Transparent)
                            
                            val bgFillColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else if (isRangeHighlight) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                if (cellBackgroundParsed == Color.Transparent || 
                                    (cellBackgroundParsed == Color.White && isDarkThemeActive) || 
                                    cellStyle.backgroundColorHex.isBlank()) {
                                    defaultBackgroundFallback
                                } else {
                                    cellBackgroundParsed
                                }
                            }

                            val fontStyleForCell = if (cellStyle.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            val fontWeightForCell = if (cellStyle.bold) FontWeight.Bold else FontWeight.Normal
                            val alignmentForCell = when (cellStyle.textAlign) {
                                "CENTER" -> TextAlign.Center
                                "RIGHT" -> TextAlign.Right
                                else -> TextAlign.Left
                            }
                            
                            val cellTextParsed = parseHexToComposeColor(cellStyle.textColorHex, Color.Transparent)
                            val textHexColor = if (cellTextParsed == Color.Transparent || 
                                                 (cellTextParsed == Color.Black && isDarkThemeActive) || 
                                                 cellStyle.textColorHex.isBlank()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                cellTextParsed
                            }

                            Box(
                                modifier = Modifier
                                    .width(colCellWidth)
                                    .fillMaxHeight()
                                    .background(bgFillColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            if (showGridLines) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f) else Color.Transparent
                                        }
                                    )
                                    .testTag("cell_$cellRef")
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionRangeStart != null && selectionRangeStart != cellRef) {
                                                onCellRangeSelected(selectionRangeStart!!, cellRef)
                                                selectionRangeStart = null
                                            } else {
                                                onCellSelected(cellRef)
                                            }
                                        },
                                        onLongClick = {
                                            // Start range drag mark selecting
                                            selectionRangeStart = cellRef
                                            onCellSelected(cellRef)
                                        }
                                    )
                                    .padding(horizontal = 6.dp),
                                contentAlignment = when (cellStyle.textAlign) {
                                    "CENTER" -> Alignment.Center
                                    "RIGHT" -> Alignment.CenterEnd
                                    else -> Alignment.CenterStart
                                }
                            ) {
                                if (cellData.value.isNotEmpty()) {
                                    Text(
                                        text = cellData.value,
                                        fontSize = ((cellStyle.fontSize ?: 14) * zoomScale).sp,
                                        fontWeight = fontWeightForCell,
                                        fontStyle = fontStyleForCell,
                                        color = textHexColor,
                                        textAlign = alignmentForCell,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Grid Navigation Bar (Paging Toolbar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.5.dp))
                .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Horizontal navigation (Columns)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lastColIndex = minOf(maxCols - 1, viewportColStart + windowCols - 1)
                val startLabel = FormulaParser.getColumnLabel(viewportColStart)
                val endLabel = FormulaParser.getColumnLabel(lastColIndex)
                Text(
                    text = "Cols: $startLabel-$endLabel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                IconButton(
                    onClick = { onViewportColStartChanged(0) },
                    enabled = viewportColStart > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.FirstPage, contentDescription = "First Column Group", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { onViewportColStartChanged(maxOf(0, viewportColStart - 15)) },
                    enabled = viewportColStart > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Previous Columns", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { onViewportColStartChanged(minOf(maxCols - 12, viewportColStart + 15)) },
                    enabled = viewportColStart < maxCols - windowCols,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.KeyboardDoubleArrowRight, contentDescription = "Next Columns", modifier = Modifier.size(16.dp))
                }
            }

            // Divider vertical marker
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Vertical navigation (Rows)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lastRowIndex = minOf(maxRows, viewportRowStart + windowRows)
                Text(
                    text = "Rows: ${viewportRowStart + 1}-$lastRowIndex of $maxRows",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                IconButton(
                    onClick = { onViewportRowStartChanged(0) },
                    enabled = viewportRowStart > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.FirstPage, contentDescription = "First Row", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { onViewportRowStartChanged(maxOf(0, viewportRowStart - 40)) },
                    enabled = viewportRowStart > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous Rows", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { onViewportRowStartChanged(minOf(maxRows - 40, viewportRowStart + 40)) },
                    enabled = viewportRowStart < maxRows - windowRows,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next Rows", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Checks if key cellRef falls inside selected start and end rectangular selection
 */
private fun isCellInRange(ref: String, startRef: String, endRef: String?): Boolean {
    if (endRef == null) return false
    val current = FormulaParser.parseCellReference(ref) ?: return false
    val start = FormulaParser.parseCellReference(startRef) ?: return false
    val end = FormulaParser.parseCellReference(endRef) ?: return false

    val minCol = minOf(start.first, end.first)
    val maxCol = maxOf(start.first, end.first)
    val minRow = minOf(start.second, end.second)
    val maxRow = maxOf(start.second, end.second)

    return current.first in minCol..maxCol && current.second in minRow..maxRow
}

private fun parseHexToComposeColor(hex: String, fallback: Color = Color.White): Color {
    if (hex.isBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}
