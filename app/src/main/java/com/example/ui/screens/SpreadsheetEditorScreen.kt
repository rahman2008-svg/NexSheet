package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.SpreadsheetVersion
import com.example.viewmodel.SpreadsheetViewModel
import com.example.data.model.FormulaParser
import com.example.ui.components.ChartDataExtractor
import com.example.ui.components.SpreadsheetChartCard
import com.example.ui.components.SpreadsheetGrid
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpreadsheetEditorScreen(
    viewModel: SpreadsheetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val activeFile = viewModel.activeFile ?: return
    val activeSheets = viewModel.activeSheets
    val activeSheetIndex = viewModel.activeSheetIndex
    val currentSheet = viewModel.getActiveSheet()

    // Dialog state controllers
    var showRenameFileDialog by remember { mutableStateOf(false) }
    var renameFileText by remember { mutableStateOf(activeFile.title) }

    var showRenameSheetDialog by remember { mutableStateOf(false) }
    var renameSheetText by remember { mutableStateOf("") }

    var showPinSettingDialog by remember { mutableStateOf(false) }
    var filePinCode by remember { mutableStateOf(activeFile.pin ?: "") }
    var fileHasPin by remember { mutableStateOf(activeFile.hasPin) }

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showFormulaHelp by remember { mutableStateOf(false) }
    var showCellOperationsDialog by remember { mutableStateOf(false) }

    // Toggle spreadsheet workspace view tabs: GRID or VISUAL ANALYTICS CHART
    var workspaceTab by remember { mutableStateOf("GRID") }

    if (currentSheet == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Active cell configurations
    val selectedCellData = currentSheet.cells[viewModel.selectedCellRef]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable {
                            renameFileText = activeFile.title
                            showRenameFileDialog = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeFile.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.size(12.dp))
                        }
                        Text(
                            text = "Category: ${activeFile.category} | Auto-saved locally",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeActiveFile() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveBackupSnapshot()
                        Toast.makeText(context, "Version checkpoint snapshot saved!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save Snapshot")
                    }
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(Icons.Filled.History, contentDescription = "Local Backups")
                    }
                    IconButton(onClick = { showPinSettingDialog = true }) {
                        Icon(
                            imageVector = if (activeFile.hasPin) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Security PIN"
                        )
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            // 1. Workspace Tabs selection: Grid vs Visualization Dashboard
            TabRow(
                selectedTabIndex = if (workspaceTab == "GRID") 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = workspaceTab == "GRID",
                    onClick = { workspaceTab = "GRID" },
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grid Board")
                    }}
                )
                Tab(
                    selected = workspaceTab == "CHARTS",
                    onClick = { workspaceTab = "CHARTS" },
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PieChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dashboard View")
                    }}
                )
            }

            if (workspaceTab == "GRID") {
                // RENDER EXCEL-LIKE INTERACTIVE GRID EDITOR

                // 2. Active Cell Edit Formula bar with handy hints
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var nameBoxTextState by remember(viewModel.selectedCellRef, viewModel.selectedRangeEndRef) {
                        mutableStateOf(viewModel.selectedCellRef + (if (viewModel.selectedRangeEndRef != null) ":${viewModel.selectedRangeEndRef}" else ""))
                    }

                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = nameBoxTextState,
                            onValueChange = { nameBoxTextState = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val cleaned = nameBoxTextState.trim().uppercase()
                                    if (cleaned.isNotEmpty()) {
                                        if (cleaned.contains(":")) {
                                            val parts = cleaned.split(":")
                                            if (parts.size == 2) {
                                                val start = parts[0].trim()
                                                val end = parts[1].trim()
                                                val startCoords = FormulaParser.parseCellReference(start)
                                                val endCoords = FormulaParser.parseCellReference(end)
                                                if (startCoords != null && endCoords != null) {
                                                    viewModel.selectRange(start, end)
                                                    Toast.makeText(context, "Range selected: $start:$end", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Invalid range format (e.g., A1:B5)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            val coords = FormulaParser.parseCellReference(cleaned)
                                            if (coords != null) {
                                                viewModel.selectCell(cleaned)
                                                Toast.makeText(context, "Selected cell $cleaned", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Invalid address (e.g., A1)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "fx",
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = viewModel.formulaInputText,
                        onValueChange = { viewModel.updateActiveCell(it) },
                        placeholder = { Text("Enter number, text, or formula (=SUM(A1:B3))...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("formula_input_field"),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    IconButton(onClick = { showFormulaHelp = true }) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = "Formula Help",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. Command/Styles Quick Action toolbar ribbon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 11.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Font bold toggle
                    IconButton(
                        onClick = { viewModel.toggleSelectedCellBold() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (selectedCellData?.style?.bold == true) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    ) {
                        Icon(Icons.Filled.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp))
                    }

                    // Font italic toggle
                    IconButton(
                        onClick = { viewModel.toggleSelectedCellItalic() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (selectedCellData?.style?.italic == true) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    ) {
                        Icon(Icons.Filled.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp))
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Alignments
                    IconButton(
                        onClick = { viewModel.updateSelectedCellAlignment("LEFT") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.FormatAlignLeft, contentDescription = "Align Left", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.updateSelectedCellAlignment("CENTER") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.FormatAlignCenter, contentDescription = "Align Center", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.updateSelectedCellAlignment("RIGHT") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.FormatAlignRight, contentDescription = "Align Right", modifier = Modifier.size(18.dp))
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Font Size adjustments
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val currentSize = selectedCellData?.style?.fontSize ?: 14
                                viewModel.updateSelectedCellFontSize((currentSize - 1).coerceAtLeast(10))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Dec Font Size", modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = "${selectedCellData?.style?.fontSize ?: 14}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = {
                                val currentSize = selectedCellData?.style?.fontSize ?: 14
                                viewModel.updateSelectedCellFontSize((currentSize + 1).coerceAtMost(24))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Inc Font Size", modifier = Modifier.size(14.dp))
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Background highlights cell colors
                    Text("Highlight:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ColorPickerStrip(
                        selectedColorHex = selectedCellData?.style?.backgroundColorHex ?: "#FFFFFF",
                        onColorSelected = { viewModel.updateSelectedCellBackgroundColor(it) }
                    )

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Text Foreground color strip
                    Text("Text Color:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextColorPickerStrip(
                        selectedColorHex = selectedCellData?.style?.textColorHex ?: "#000000",
                        onColorSelected = { viewModel.updateSelectedCellTextColor(it) }
                    )

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Dynamic Smart Fill Pattern tool
                    Button(
                        onClick = {
                            viewModel.performAutoFillDown()
                            Toast.makeText(context, "Dynamic smart pattern filled 5 cells down!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Filled.AutoMode, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pattern Auto-fill", fontSize = 10.sp)
                    }

                    if (viewModel.selectedRangeEndRef != null) {
                        Button(
                            onClick = { viewModel.clearRangeSelection() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Selection", fontSize = 10.sp)
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Cell Operations Context Menu button
                    Button(
                        onClick = { showCellOperationsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cell Ops", fontSize = 10.sp)
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Gridline toggle
                    IconButton(
                        onClick = {
                            viewModel.showGridLines = !viewModel.showGridLines
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (viewModel.showGridLines) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GridOn,
                            contentDescription = "Toggle Grid lines",
                            tint = if (viewModel.showGridLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (viewModel.showGridLines) "Grid: Show" else "Grid: Hide",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Zoom Picker Button Action
                    IconButton(
                        onClick = {
                            val nextZoom = when (viewModel.zoomScale) {
                                0.5f -> 0.75f
                                0.75f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.50f
                                1.50f -> 2.0f
                                2.0f -> 0.5f
                                else -> 1.0f
                            }
                            viewModel.zoomScale = nextZoom
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ZoomIn,
                            contentDescription = "Zoom",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${(viewModel.zoomScale * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Suggestions Formula Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.getSuggestedFormulas().forEach { sumFormula ->
                        AssistChip(
                            onClick = {
                                viewModel.updateActiveCell(sumFormula)
                            },
                            label = { Text(sumFormula, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // 4. Multiple Sheet link tabs selector at the bottom or top of grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activeSheets.forEachIndexed { idx, sheet ->
                            val isSelected = idx == activeSheetIndex
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .combinedClickable(
                                        onClick = { viewModel.activeSheetIndex = idx },
                                        onLongClick = {
                                            renameSheetText = sheet.name
                                            showRenameSheetDialog = true
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sheet.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelected && activeSheets.size > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Delete Sheet",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { viewModel.deleteActiveSheet() }
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.addNewSheet() }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "Add New Sheet")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 5. MAIN SPREADSHEET 2D GRID WORKSPACE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    SpreadsheetGrid(
                        sheet = currentSheet,
                        selectedCellRef = viewModel.selectedCellRef,
                        onCellSelected = { ref -> viewModel.selectCell(ref) },
                        onCellRangeSelected = { start, end -> viewModel.selectRange(start, end) },
                        selectedRangeEndRef = viewModel.selectedRangeEndRef,
                        zoomScale = viewModel.zoomScale,
                        showGridLines = viewModel.showGridLines,
                        viewportColStart = viewModel.viewportColStart,
                        onViewportColStartChanged = { col -> viewModel.viewportColStart = col },
                        viewportRowStart = viewModel.viewportRowStart,
                        onViewportRowStartChanged = { row -> viewModel.viewportRowStart = row }
                    )
                }

                // 6. Range Multi-selection Basic Aggregation Statistics indicator
                viewModel.getSelectionRangeSummary()?.let { summaryMetrics ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        summaryMetrics.forEach { (title, valStr) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$title: ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = valStr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

            } else {
                // RENDER VISUAL ANALYTICS CHARTS TAB

                val start = viewModel.selectedCellRef
                val end = viewModel.selectedRangeEndRef
                val chartData = if (end != null) {
                    ChartDataExtractor.extract(start, end, currentSheet)
                } else {
                    // Default fallback: scan and pairing column A and B from rows 4 to 8 of current sheet
                    ChartDataExtractor.extract("A4", "B8", currentSheet)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "A1 Layout Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    item {
                        SpreadsheetChartCard(
                            extractedData = chartData,
                            titleName = if (end != null) "Data metrics on range $start:$end" else "Active Sheet Quick Analytics (A4:B8 preset)"
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Pivot summary (Basic aggregation checks)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To run custom aggregations, switch to the Grid Board, hold-drag or long-press on cell start, click a boundary range, and see metrics dynamically updated here.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Renaming File Dialog
    if (showRenameFileDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFileDialog = false },
            title = { Text("Rename Spreadsheet File") },
            text = {
                OutlinedTextField(
                    value = renameFileText,
                    onValueChange = { renameFileText = it },
                    label = { Text("Workbook Name") },
                    modifier = Modifier.fillMaxWidth().testTag("rename_file_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFileText.isNotBlank()) {
                            viewModel.renameSpreadsheetFile(renameFileText)
                            showRenameFileDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Renaming Sheet Tab Dialog
    if (showRenameSheetDialog) {
        AlertDialog(
            onDismissRequest = { showRenameSheetDialog = false },
            title = { Text("Rename Sheet Tab") },
            text = {
                OutlinedTextField(
                    value = renameSheetText,
                    onValueChange = { renameSheetText = it },
                    label = { Text("Tab Title") },
                    modifier = Modifier.fillMaxWidth().testTag("rename_sheet_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameSheetText.isNotBlank()) {
                            viewModel.renameActiveSheet(renameSheetText)
                            showRenameSheetDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameSheetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Security App/Spreadsheet PIN lock
    if (showPinSettingDialog) {
        AlertDialog(
            onDismissRequest = { showPinSettingDialog = false },
            title = { Text("Privacy Lock Settings") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Setting a security passcode secures this spreadsheet on opening from local folder dashboard.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Passcode Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Switch(
                            checked = fileHasPin,
                            onCheckedChange = { fileHasPin = it }
                        )
                    }

                    if (fileHasPin) {
                        OutlinedTextField(
                            value = filePinCode,
                            onValueChange = { filePinCode = it },
                            label = { Text("Numeric Vault PIN") },
                            placeholder = { Text("E.g. 1234") },
                            modifier = Modifier.fillMaxWidth().testTag("pin_code_field")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileHasPin && filePinCode.trim().length < 2) {
                            Toast.makeText(context, "Please set a valid security PIN (minimum 2 characters).", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setSpreadsheetPinLock(fileHasPin, if (fileHasPin) filePinCode.trim() else null)
                            showPinSettingDialog = false
                            Toast.makeText(context, "Workbook security settings updated!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Apply Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSettingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal History Version Backups Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Local Backups (Auto-save history)")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    Text(
                        text = "NexSheet secures backups locally. Clicking a backup restores this file grid status completely.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (viewModel.fileVersions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No checkpoints found. Try saving a snapshot!", fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(viewModel.fileVersions) { version ->
                                Card(
                                    onClick = {
                                        viewModel.restoreBackupSnapshot(version)
                                        showHistoryDialog = false
                                        Toast.makeText(context, "Restored backup snapshot successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(11.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Checkpoint ID: #${version.id}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Time: " + formatTimestamp(version.timestamp),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.RestorePage,
                                            contentDescription = "Restore",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal Export Dialog options: CSV and HTML Preview Output
    if (showExportDialog) {
        // Refresh local files list when dialog shows to ensure accurate live status
        LaunchedEffect(Unit) {
            viewModel.refreshInternalStorageFiles()
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Direct Saves & Exports")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CloudDone, contentDescription = "Sync Approved", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Offline Direct Autosave", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Your edits are saved physically to internal storage (JSON, CSV, and HTML format) in real time.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Storage folder: ${context.filesDir.absolutePath}/internal_spreadsheets/",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Copy/Export Current Active Sheet:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Button(
                            onClick = {
                                viewModel.refreshInternalStorageFiles()
                                Toast.makeText(context, "Direct-saves refreshed!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Refresh List", fontSize = 11.sp)
                        }
                    }

                    // Card for CSV
                    Card(
                        onClick = {
                            val csvText = viewModel.exportToCsvContent()
                            clipboardManager.setText(AnnotatedString(csvText))
                            Toast.makeText(context, "CSV copied to clipboard completely!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FileCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Copy Active Sheet as CSV", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Paste into Microsoft Excel or Notepad", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Card for HTML
                    Card(
                        onClick = {
                            val htmlText = viewModel.exportToHtmlPreview()
                            clipboardManager.setText(AnnotatedString(htmlText))
                            Toast.makeText(context, "Full styled HTML report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Html, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Copy as Styled HTML Report", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Open styled report on any browser", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Physically Direct-Saved Files:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val localFiles = viewModel.internalStorageFiles.filter { 
                        it.name.contains("_" + activeFile.id)
                    }

                    if (localFiles.isEmpty()) {
                        Text(
                            "No files physically found for this workbook yet. Make an edit to generate physical direct saves automatically.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 15.sp
                        )
                    } else {
                        localFiles.forEach { file ->
                            val fileSizeKb = (file.length() / 1024.0)
                            val sizeDisplay = String.format(java.util.Locale.US, "%.2f KB", fileSizeKb)
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Size: $sizeDisplay | Location: internal_spreadsheets/${file.name}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Row {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val content = file.readText()
                                                    clipboardManager.setText(AnnotatedString(content))
                                                    Toast.makeText(context, "${file.name} content copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Failed to copy content: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.FileCopy,
                                                contentDescription = "Copy Content",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(file.absolutePath))
                                                Toast.makeText(context, "Absolute path copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Share,
                                                contentDescription = "Copy Absolute Path",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal cell operations context menu
    if (showCellOperationsDialog) {
        val parsedCoords = FormulaParser.parseCellReference(viewModel.selectedCellRef)
        val colLabelText = if (parsedCoords != null) FormulaParser.getColumnLabel(parsedCoords.first) else ""
        val rowLabelText = if (parsedCoords != null) (parsedCoords.second + 1).toString() else ""

        AlertDialog(
            onDismissRequest = { showCellOperationsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cell Actions for $colLabelText$rowLabelText")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Perform native layout and structural modifications. Grid cells will shift cleanly.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    HorizontalDivider()

                    // Clear content in active cell
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.clearActiveCell()
                                showCellOperationsDialog = false
                                Toast.makeText(context, "Cleared cell content", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Clear Cell Content", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Insert Row above
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.insertRowAboveActiveCell()
                                showCellOperationsDialog = false
                                Toast.makeText(context, "Inserted row above $rowLabelText", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Insert Row Above $rowLabelText", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Insert Column Left
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.insertColumnLeftOfActiveCell()
                                showCellOperationsDialog = false
                                Toast.makeText(context, "Inserted column left of $colLabelText", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Insert Column Left of $colLabelText", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Delete Active Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.deleteActiveCellRow()
                                showCellOperationsDialog = false
                                Toast.makeText(context, "Deleted row $rowLabelText and shifted values up", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Active Row $rowLabelText", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Delete Active Column
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.deleteActiveCellColumn()
                                showCellOperationsDialog = false
                                Toast.makeText(context, "Deleted column $colLabelText and shifted values left", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Active Column $colLabelText", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCellOperationsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ColorPickerStrip(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit
) {
    val hexColorOptions = listOf(
        "#FFFFFF" to Color.White,
        "#FFF2CC" to Color(0xFFFFF2CC), // Yellow highlight
        "#E2F0D9" to Color(0xFFE2F0D9), // Green highlight
        "#DDEBF7" to Color(0xFFDDEBF7), // Blue highlight
        "#FCE4D6" to Color(0xFFFCE4D6), // Red highlight
        "#E8E8E8" to Color(0xFFE8E8E8)  // Grey highlight
    )

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        hexColorOptions.forEach { (hex, color) ->
            val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(hex) }
            )
        }
    }
}

@Composable
fun TextColorPickerStrip(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit
) {
    val hexColorOptions = listOf(
        "#1C1B1F" to Color(0xFF1C1B1F), // Dark Neutral
        "#C026D3" to Color(0xFFC026D3), // Magenta
        "#059669" to Color(0xFF059669), // Emerald
        "#2563EB" to Color(0xFF2563EB), // Blue
        "#DC2626" to Color(0xFFDC2626)  // Red
    )

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        hexColorOptions.forEach { (hex, color) ->
            val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(hex) }
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}
