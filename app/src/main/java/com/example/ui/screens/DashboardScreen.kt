package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.SpreadsheetFile
import com.example.viewmodel.SpreadsheetViewModel
import com.example.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SpreadsheetViewModel,
    onOpenFile: (SpreadsheetFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val files by viewModel.allFiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTemplateForCreate by remember { mutableStateOf<String?>(null) }

    // Dialog form states
    var newFileTitle by remember { mutableStateOf("") }
    var newFileCategory by remember { mutableStateOf("All") }

    // About Dialog State
    var showAboutDialog by remember { mutableStateOf(false) }

    val categories = viewModel.getCategories(files)
    val filteredAndSearchedFiles = viewModel.filteredFiles(files).filter {
        it.title.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NexSheet",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Credits")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedTemplateForCreate = "EMPTY"
                    newFileTitle = "Blank Ledger"
                    newFileCategory = "General"
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_file_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Blank Sheet")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            // Search & Filters Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search spreadsheets...") },
                placeholder = { Text("Search by workbook name...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_bar"),
                shape = RoundedCornerShape(12.dp)
            )

            // Dynamic folder categorization tabs
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(viewModel.selectedCategoryFilter).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}, // Hide standard indicator line to let our custom background pill shine
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = viewModel.selectedCategoryFilter == cat
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectedCategoryFilter = cat },
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Core Main Content area
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                // Show Onboarding banner when list is empty
                if (filteredAndSearchedFiles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Image(
                                    painter = painterResource(id = R.drawable.img_onboarding_illustration),
                                    contentDescription = "Onboarding banner decoration",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Build Smarter Offline Workbooks",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "NexSheet lets you build offline-only spreadsheets, evaluate mathematical formulas, visualize metrics, and protect security, completely cloud-free.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Templates Section: Budget, Result, Attendance, etc.
                item {
                    Text(
                        text = "Create from Productivity Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TemplateCard(
                            title = "Budget Tracker",
                            desc = "Calculate monthly variances",
                            icon = Icons.Filled.AccountBalanceWallet,
                            color = Color(0xFF2E7D32),
                            onClick = {
                                selectedTemplateForCreate = "BUDGET"
                                newFileTitle = "Monthly Budget"
                                newFileCategory = "Budget"
                                showCreateDialog = true
                            }
                        )
                        TemplateCard(
                            title = "Attendance Sheet",
                            desc = "Track daily presence",
                            icon = Icons.Filled.HowToReg,
                            color = Color(0xFF1565C0),
                            onClick = {
                                selectedTemplateForCreate = "ATTENDANCE"
                                newFileTitle = "Class Attendance Ledger"
                                newFileCategory = "Attendance"
                                showCreateDialog = true
                            }
                        )
                        TemplateCard(
                            title = "Exam Result Sheet",
                            desc = "Class marks & grade averages",
                            icon = Icons.Filled.Task,
                            color = Color(0xFFC2185B),
                            onClick = {
                                selectedTemplateForCreate = "RESULT"
                                newFileTitle = "Term Marks Analysis"
                                newFileCategory = "Report"
                                showCreateDialog = true
                            }
                        )
                        TemplateCard(
                            title = "Invoice Generator",
                            desc = "Line totals, tax & sums",
                            icon = Icons.Filled.ReceiptLong,
                            color = Color(0xFFE65100),
                            onClick = {
                                selectedTemplateForCreate = "INVOICE"
                                newFileTitle = "Client Billable Invoice"
                                newFileCategory = "Invoice"
                                showCreateDialog = true
                            }
                        )
                        TemplateCard(
                            title = "Expense Tracker",
                            desc = "Daily expenditure tags",
                            icon = Icons.Filled.MonetizationOn,
                            color = Color(0xFF7B1FA2),
                            onClick = {
                                selectedTemplateForCreate = "EXPENSE"
                                newFileTitle = "Daily Expenditure Ledger"
                                newFileCategory = "Expense"
                                showCreateDialog = true
                            }
                        )
                    }
                }

                // Recent Files Listing
                item {
                    Text(
                        text = "Your Local Spreadsheets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (filteredAndSearchedFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No worksheets found in this category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredAndSearchedFiles) { file ->
                        FileListItem(
                            file = file,
                            onOpen = { onOpenFile(file) },
                            onDelete = { viewModel.deleteFile(file) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Modal creation dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = if (selectedTemplateForCreate == "EMPTY") "Create Blank Spreadsheet" else "Create from Template"
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newFileTitle,
                        onValueChange = { newFileTitle = it },
                        label = { Text("Spreadsheet Name / Title") },
                        modifier = Modifier.fillMaxWidth().testTag("new_file_title_input")
                    )

                    OutlinedTextField(
                        value = newFileCategory,
                        onValueChange = { newFileCategory = it },
                        label = { Text("Category Tag / Folder") },
                        modifier = Modifier.fillMaxWidth().testTag("new_file_category_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileTitle.isNotBlank()) {
                            viewModel.createSpreadsheetFromTemplate(
                                title = newFileTitle,
                                category = newFileCategory.ifBlank { "All" },
                                templateType = selectedTemplateForCreate ?: "EMPTY"
                            )
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Dialog: Show credits
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Filled.AccountBox, contentDescription = null, modifier = Modifier.size(36.dp)) },
            title = { Text("NexSheet Suite") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Fast, lightweight, offline spreadsheet and data manager without cloud sync.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text("Developer Information", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Author: Prince AR Abdur Rahman", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("WhatsApp Contacts: 01707424006 / 01796951709", fontSize = 12.sp)
                    Text("Facebook & Instagram: @ur___abdur____rahman__2008", fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Company Profiles", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Published by: NexVora Lab's Ofc", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Mission: Build beautiful, privacy-friendly, local-first dynamic utility products accessible to everyone.", fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Details", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Version: 1.0.0 (Technical Release)", fontSize = 12.sp)
                    Text("Copyright: © 2026 NexVora Lab's Ofc. All Rights Reserved.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun TemplateCard(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(130.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun FileListItem(
    file: SpreadsheetFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.hasPin) Icons.Filled.Lock else Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(file.category, fontSize = 9.sp) },
                        modifier = Modifier.height(20.dp),
                        enabled = false,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null
                    )
                    Text(
                        text = "Modified: " + formatTimestamp(file.lastModified),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete File",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Spreadsheet?") },
            text = { Text("Are you sure you want to permanently erase \"${file.title}\"? This operation cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}
