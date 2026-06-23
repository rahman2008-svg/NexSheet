package com.example.data.model

import java.util.Locale
import java.util.regex.Pattern

object FormulaParser {

    /**
     * Converts a column index (0-based) to Excel-style letter labels (e.g. 0 -> "A", 25 -> "Z", 26 -> "AA")
     */
    fun getColumnLabel(index: Int): String {
        var temp = index
        val label = StringBuilder()
        while (temp >= 0) {
            label.insert(0, ('A'.code + (temp % 26)).toChar())
            temp = (temp / 26) - 1
        }
        return label.toString()
    }

    /**
     * Converts a column label (e.g. "A", "AA") to a 0-based column index
     */
    fun getColumnIndex(label: String): Int {
        var colIdx = 0
        for (element in label.uppercase(Locale.ROOT)) {
            if (element in 'A'..'Z') {
                colIdx = colIdx * 26 + (element - 'A' + 1)
            }
        }
        return colIdx - 1
    }

    /**
     * Parses cell reference, returns Pair(colIndex, rowIndex 0-based) or null if invalid.
     * E.g., "A1" -> Pair(0, 0)
     */
    fun parseCellReference(ref: String): Pair<Int, Int>? {
        val uppercaseRef = ref.uppercase(Locale.ROOT).trim()
        val matcher = Pattern.compile("^([A-Z]+)([0-9]+)$").matcher(uppercaseRef)
        if (matcher.find()) {
            val colStr = matcher.group(1) ?: return null
            val rowStr = matcher.group(2) ?: return null
            val col = getColumnIndex(colStr)
            val row = (rowStr.toIntOrNull() ?: 1) - 1
            if (col >= 0 && row >= 0) {
                return Pair(col, row)
            }
        }
        return null
    }

    /**
     * Evaluates a formula or raw string in a single cell, resolving references reactively.
     * Keeps track of [evaluatingRefs] to prevent infinite recursion loop crash.
     */
    fun evaluateCell(
        ref: String,
        cellData: CellData,
        allCells: Map<String, CellData>,
        evaluatingRefs: MutableSet<String> = mutableSetOf()
    ): String {
        val formula = cellData.formula.trim()
        if (!formula.startsWith("=")) {
            return cellData.value // Raw input
        }

        val uppercaseFormula = formula.uppercase(Locale.ROOT)
        if (ref in evaluatingRefs) {
            return "#REF_LOOP!"
        }

        evaluatingRefs.add(ref)
        val result = try {
            val formulaText = uppercaseFormula.substring(1).trim() // Remove '='
            evaluateFormulaText(formulaText, allCells, evaluatingRefs)
        } catch (e: Exception) {
            "#ERR: " + (e.message ?: "Invalid formula")
        } finally {
            evaluatingRefs.remove(ref)
        }
        return result
    }

    /**
     * Internal parsing of the formula string (without the leading '=').
     */
    private fun evaluateFormulaText(
        formulaText: String,
        allCells: Map<String, CellData>,
        evaluatingRefs: MutableSet<String>
    ): String {
        // 1. Check for standard math functions: SUM, AVERAGE, MIN, MAX, COUNT
        val functionPattern = Pattern.compile("^([A-Z]+)\\(([^)]+)\\)$")
        val matcher = functionPattern.matcher(formulaText)
        if (matcher.find()) {
            val func = matcher.group(1) ?: ""
            val arg = matcher.group(2) ?: ""
            return executeSpreadsheetFunction(func, arg, allCells, evaluatingRefs)
        }

        // 2. Fall back to arithmetic expressions involving direct cells or numbers (e.g. A1 + B1 * 2)
        return try {
            evaluateArithmeticExpression(formulaText, allCells, evaluatingRefs)
        } catch (e: Exception) {
            "#VALUE!"
        }
    }

    /**
     * Resolves all cell references within a range (e.g. "A1:B3" or single "A1")
     */
    private fun resolveCellRange(
        rangeStr: String,
        allCells: Map<String, CellData>,
        evaluatingRefs: MutableSet<String>
    ): List<Double> {
        val parts = rangeStr.split(":")
        val resolvedNumbers = mutableListOf<Double>()

        if (parts.size == 1) {
            // Single Cell or Number
            val cellRef = parts[0].trim()
            val parsedRef = parseCellReference(cellRef)
            if (parsedRef != null) {
                val valueStr = getCellEvaluatedValue(cellRef, allCells, evaluatingRefs)
                valueStr.toDoubleOrNull()?.let { resolvedNumbers.add(it) }
            } else {
                // Check if it is a raw number
                cellRef.toDoubleOrNull()?.let { resolvedNumbers.add(it) }
            }
        } else if (parts.size == 2) {
            // Range (e.g. A1:B3)
            val startRef = parseCellReference(parts[0].trim())
            val endRef = parseCellReference(parts[1].trim())
            if (startRef != null && endRef != null) {
                val minCol = minOf(startRef.first, endRef.first)
                val maxCol = maxOf(startRef.first, endRef.first)
                val minRow = minOf(startRef.second, endRef.second)
                val maxRow = maxOf(startRef.second, endRef.second)

                for (col in minCol..maxCol) {
                    for (row in minRow..maxRow) {
                        val refKey = "${getColumnLabel(col)}${row + 1}"
                        val valueStr = getCellEvaluatedValue(refKey, allCells, evaluatingRefs)
                        valueStr.toDoubleOrNull()?.let { resolvedNumbers.add(it) }
                    }
                }
            }
        }
        return resolvedNumbers
    }

    /**
     * Gets the evaluated value of a cellular reference dynamically
     */
    private fun getCellEvaluatedValue(
        ref: String,
        allCells: Map<String, CellData>,
        evaluatingRefs: MutableSet<String>
    ): String {
        val cell = allCells[ref] ?: return ""
        if (cell.formula.isNotEmpty()) {
            return evaluateCell(ref, cell, allCells, evaluatingRefs)
        }
        return cell.value
    }

    /**
     * Supports basic functions: SUM, AVERAGE, MIN, MAX, COUNT
     */
    private fun executeSpreadsheetFunction(
        funcName: String,
        argument: String,
        allCells: Map<String, CellData>,
        evaluatingRefs: MutableSet<String>
    ): String {
        val values = mutableListOf<Double>()
        // Arguments can be separated by commas (e.g., A1,B2,C1:C3)
        val args = argument.split(",")
        for (arg in args) {
            values.addAll(resolveCellRange(arg.trim(), allCells, evaluatingRefs))
        }

        if (values.isEmpty() && funcName != "COUNT") {
            return "0"
        }

        val result = when (funcName) {
            "SUM" -> values.sum()
            "AVERAGE" -> if (values.isNotEmpty()) values.average() else 0.0
            "MIN" -> if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0
            "MAX" -> if (values.isNotEmpty()) values.maxOrNull() ?: 0.0 else 0.0
            "COUNT" -> values.size.toDouble()
            else -> throw IllegalArgumentException("Unknown function $funcName")
        }

        // Format result nicely
        return formatAsMathResult(result)
    }

    private fun formatAsMathResult(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * A lightweight arithmetic solver for expressions like A1+B2*3 or 10-5.
     * Note: Evaluates left-to-right supporting +, -, *, / for ease of expression.
     */
    private fun evaluateArithmeticExpression(
        expr: String,
        allCells: Map<String, CellData>,
        evaluatingRefs: MutableSet<String>
    ): String {
        // Strip out and resolve variables (cell references) with their numeric values
        // E.g., "A1 + B2" -> "20 + 3"
        val cleanExpr = StringBuilder()
        val matcher = Pattern.compile("([A-Z]+[0-9]+)|([+-/*()\\s])|([0-9.]+)").matcher(expr)

        while (matcher.find()) {
            val cellRef = matcher.group(1)
            val operator = matcher.group(2)
            val numValue = matcher.group(3)

            if (cellRef != null) {
                val evalVal = getCellEvaluatedValue(cellRef, allCells, evaluatingRefs)
                val numericVal = evalVal.toDoubleOrNull() ?: 0.0
                cleanExpr.append(numericVal)
            } else if (operator != null) {
                cleanExpr.append(operator)
            } else if (numValue != null) {
                cleanExpr.append(numValue)
            }
        }

        val expressionString = cleanExpr.toString().replace("\\s".toRegex(), "")
        if (expressionString.isEmpty()) return "0"

        // Evaluate using a simple mathematical parser
        return try {
            val res = simpleMathEval(expressionString)
            formatAsMathResult(res)
        } catch (e: Exception) {
            "#VAL!"
        }
    }

    /**
     * Evaluates clean math expressions containing +, -, *, / and floating numbers (e.g. 10+2.5*4)
     */
    private fun simpleMathEval(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm() // addition
                    else if (eat('-'.code)) x -= parseTerm() // subtraction
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor() // multiplication
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor // division
                    } else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor() // unary plus
                if (eat('-'.code)) return -parseFactor() // unary minus

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) { // parentheses
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) { // numbers
                    while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                    x = str.substring(startPos, this.pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                return x
            }
        }.parse()
    }
}
