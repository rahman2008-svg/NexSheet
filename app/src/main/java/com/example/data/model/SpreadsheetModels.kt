package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class CellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val textAlign: String = "LEFT", // LEFT, CENTER, RIGHT
    val backgroundColorHex: String = "#FFFFFF",
    val textColorHex: String = "#1C1B1F", // Default M3 on-surface or typical dark text
    val fontSize: Int = 14
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("bold", bold)
            put("italic", italic)
            put("textAlign", textAlign)
            put("backgroundColorHex", backgroundColorHex)
            put("textColorHex", textColorHex)
            put("fontSize", fontSize)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): CellStyle {
            if (json == null) return CellStyle()
            return CellStyle(
                bold = json.optBoolean("bold", false),
                italic = json.optBoolean("italic", false),
                textAlign = json.optString("textAlign", "LEFT"),
                backgroundColorHex = json.optString("backgroundColorHex", "#FFFFFF"),
                textColorHex = json.optString("textColorHex", "#1C1B1F"),
                fontSize = json.optInt("fontSize", 14)
            )
        }
    }
}

data class CellData(
    val formula: String = "", // E.g., "=SUM(A1:A10)"
    val value: String = "", // Cached evaluated or direct text input
    val style: CellStyle = CellStyle()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("formula", formula)
            put("value", value)
            put("style", style.toJsonObject())
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): CellData {
            if (json == null) return CellData()
            return CellData(
                formula = json.optString("formula", ""),
                value = json.optString("value", ""),
                style = CellStyle.fromJsonObject(json.optJSONObject("style"))
            )
        }
    }
}

data class Sheet(
    val name: String,
    val cells: Map<String, CellData> = emptyMap() // Key: "A1", "B12", etc.
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            val cellsObj = JSONObject()
            cells.forEach { (ref, cell) ->
                cellsObj.put(ref, cell.toJsonObject())
            }
            put("cells", cellsObj)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Sheet {
            val name = json.optString("name", "Sheet")
            val cellsMap = mutableMapOf<String, CellData>()
            val cellsObj = json.optJSONObject("cells")
            if (cellsObj != null) {
                val keys = cellsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val cellJson = cellsObj.optJSONObject(key)
                    if (cellJson != null) {
                        cellsMap[key] = CellData.fromJsonObject(cellJson)
                    }
                }
            }
            return Sheet(name, cellsMap)
        }

        fun serializeList(list: List<Sheet>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun deserializeList(jsonStr: String): List<Sheet> {
            val list = mutableListOf<Sheet>()
            if (jsonStr.isEmpty()) return list
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val fallbackObj = arr.optJSONObject(i)
                    if (fallbackObj != null) {
                        list.add(fromJsonObject(fallbackObj))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }
}
