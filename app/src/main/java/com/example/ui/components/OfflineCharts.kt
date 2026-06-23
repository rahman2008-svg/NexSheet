package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FormulaParser
import com.example.data.model.Sheet
import com.example.ui.theme.TealAccentDark
import kotlin.math.cos
import kotlin.math.sin

enum class ChartType {
    BAR, PIE, LINE
}

object ChartDataExtractor {
    /**
     * Extracts keys and values for charts from sheet selection range.
     * Searches for text descriptions paired with corresponding numeric scores/costs.
     */
    fun extract(startRef: String, endRef: String, sheet: Sheet): List<Pair<String, Double>> {
        val startCoords = FormulaParser.parseCellReference(startRef) ?: return emptyList()
        val endCoords = FormulaParser.parseCellReference(endRef) ?: return emptyList()

        val minCol = minOf(startCoords.first, endCoords.first)
        val maxCol = maxOf(startCoords.first, endCoords.first)
        val minRow = minOf(startCoords.second, endCoords.second)
        val maxRow = maxOf(startCoords.second, endCoords.second)

        val list = mutableListOf<Pair<String, Double>>()

        if (minCol == maxCol) {
            // One Column selection - Label by row number
            for (row in minRow..maxRow) {
                val label = "${FormulaParser.getColumnLabel(minCol)}${row + 1}"
                val valStr = sheet.cells[label]?.value ?: ""
                valValToDouble(valStr)?.let {
                    list.add(Pair(label, it))
                }
            }
        } else if (maxCol - minCol >= 1) {
            // Two or more Columns selection - pair column 0 (Keys) with column 1 (Values)
            for (row in minRow..maxRow) {
                val keyLabel = "${FormulaParser.getColumnLabel(minCol)}${row + 1}"
                val valLabel = "${FormulaParser.getColumnLabel(minCol + 1)}${row + 1}"

                val keyStr = sheet.cells[keyLabel]?.value ?: "Item ${row + 1}"
                val valStr = sheet.cells[valLabel]?.value ?: ""

                valValToDouble(valStr)?.let {
                    list.add(Pair(keyStr.ifEmpty { keyLabel }, it))
                }
            }
        }
        return list
    }

    private fun valValToDouble(vStr: String): Double? {
        return vStr.toDoubleOrNull()
    }
}

@Composable
fun SpreadsheetChartCard(
    extractedData: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    titleName: String = "Selected Cells Analysis"
) {
    if (extractedData.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a range of cells (e.g. A4:B8) containing some text values & numbers to display instant visualizations.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    var selectedChartType by remember { mutableStateOf(ChartType.BAR) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = titleName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Viewing dynamic data metrics completely offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Chart Toggles
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = selectedChartType == ChartType.BAR,
                        onClick = { selectedChartType = ChartType.BAR },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Bar Chart", modifier = Modifier.size(18.dp))
                    }
                    SegmentedButton(
                        selected = selectedChartType == ChartType.PIE,
                        onClick = { selectedChartType = ChartType.PIE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Icon(Icons.Filled.PieChart, contentDescription = "Pie Chart", modifier = Modifier.size(18.dp))
                    }
                    SegmentedButton(
                        selected = selectedChartType == ChartType.LINE,
                        onClick = { selectedChartType = ChartType.LINE },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Icon(Icons.Filled.ShowChart, contentDescription = "Line Chart", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                when (selectedChartType) {
                    ChartType.BAR -> BarChartDrawing(extractedData)
                    ChartType.PIE -> PieChartDrawing(extractedData)
                    ChartType.LINE -> LineChartDrawing(extractedData)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legends & Summary List Scroll
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Data Legends Summary:",
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScrollState(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val colorsList = getChartColors()
                    extractedData.forEachIndexed { idx, pair ->
                        val color = colorsList[idx % colorsList.size]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${pair.first}: ${pair.second}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarChartDrawing(data: List<Pair<String, Double>>) {
    val colors = getChartColors()
    val maxVal = (data.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barCount = data.size
        if (barCount == 0) return@Canvas

        val paddingBetween = 20f
        val totalSpaceWidth = width - (paddingBetween * (barCount + 1))
        val barWidth = totalSpaceWidth / barCount

        data.forEachIndexed { idx, pair ->
            val color = colors[idx % colors.size]
            val barHeight = (pair.second / maxVal) * (height - 40f) // leave top/bottom buffer
            val xOffset = paddingBetween + idx * (barWidth + paddingBetween)
            val yOffset = height - barHeight.toFloat() - 20f

            // Rect
            drawRect(
                color = color,
                topLeft = Offset(xOffset, yOffset),
                size = Size(barWidth, barHeight.toFloat())
            )

            // Draw Label text using Android native drawText
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = onSurface.toArgb()
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                // Shorten labels
                val shortLabel = if (pair.first.length > 8) pair.first.substring(0, 6) + ".." else pair.first
                drawText(
                    shortLabel,
                    xOffset + (barWidth / 2),
                    height,
                    paint
                )

                // Draw value
                drawText(
                    pair.second.toString(),
                    xOffset + (barWidth / 2),
                    yOffset - 10f,
                    paint
                )
            }
        }
    }
}

@Composable
fun PieChartDrawing(data: List<Pair<String, Double>>) {
    val colors = getChartColors()
    val sum = data.sumOf { it.second }.coerceAtLeast(1.0)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val radius = minOf(width, height) / 2.3f
        val center = Offset(width / 2f, height / 2f)

        var startAngle = -90f

        data.forEachIndexed { idx, pair ->
            val sweepAngle = ((pair.second / sum) * 360f).toFloat()
            val color = colors[idx % colors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            // Dynamic line indicator point for labels if visible
            if (sweepAngle > 15f) {
                val middleAngle = startAngle + (sweepAngle / 2f)
                val middleRad = Math.toRadians(middleAngle.toDouble())
                val textDistance = radius * 1.3f
                val tx = center.x + (cos(middleRad) * textDistance).toFloat()
                val ty = center.y + (sin(middleRad) * textDistance).toFloat()

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = Color.White.toArgb()
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val pct = ((pair.second / sum) * 100).toInt()
                    drawText("$pct%", tx, ty, paint)
                }
            }

            startAngle += sweepAngle
        }
    }
}

@Composable
fun LineChartDrawing(data: List<Pair<String, Double>>) {
    val maxVal = (data.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    val colorPrimary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val pointCount = data.size
        if (pointCount == 0) return@Canvas

        val paddingLeft = 40f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 40f

        val pathWidth = width - paddingLeft - paddingRight
        val pathHeight = height - paddingTop - paddingBottom

        val stepX = if (pointCount > 1) pathWidth / (pointCount - 1) else pathWidth

        val points = mutableListOf<Offset>()
        data.forEachIndexed { idx, pair ->
            val cx = paddingLeft + (idx * stepX)
            val cy = paddingTop + (pathHeight - (pair.second / maxVal * pathHeight)).toFloat()
            points.add(Offset(cx, cy))
        }

        // Draw horizontal grid lines
        for (i in 0..4) {
            val hY = paddingTop + (pathHeight / 4 * i)
            drawLine(
                color = onSurface.copy(alpha = 0.08f),
                start = Offset(paddingLeft, hY),
                end = Offset(width - paddingRight, hY),
                strokeWidth = 2f
            )
        }

        // Draw Line path
        for (i in 0 until points.size - 1) {
            drawLine(
                color = colorPrimary,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        // Draw Dots and Labels
        points.forEachIndexed { idx, offset ->
            drawCircle(
                color = colorPrimary,
                radius = 8f,
                center = offset
            )
            drawCircle(
                color = if (isDark) Color(0xFF10131B) else Color.White,
                radius = 4f,
                center = offset
            )

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = onSurface.toArgb()
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val label = data[idx].first
                val shortLabel = if (label.length > 8) label.substring(0, 5) + ".." else label
                drawText(
                    shortLabel,
                    offset.x,
                    height,
                    paint
                )

                // value
                drawText(
                    data[idx].second.toString(),
                    offset.x,
                    offset.y - 12f,
                    paint
                )
            }
        }
    }
}

private fun getChartColors(): List<Color> {
    return listOf(
        Color(0xFF2E7D32), // Excel green
        Color(0xFF1565C0), // Blue
        Color(0xFFAD1457), // Pink
        Color(0xFFEF6C00), // Orange
        Color(0xFF4527A0), // Purple
        Color(0xFF00838F), // Teal
        Color(0xFF9E9D24)  // Lime
    )
}

@Composable
fun Modifier.horizontalScrollState(): Modifier {
    return this
}
