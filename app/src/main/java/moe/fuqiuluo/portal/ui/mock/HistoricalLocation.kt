package moe.fuqiuluo.portal.ui.mock

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject

data class HistoricalLocation(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double
) {
    companion object {
        fun fromString(str: String): HistoricalLocation {
            // Try to parse as JSON
            return try {
                JSON.parseObject(str, HistoricalLocation::class.java)
            } catch (e: Exception) {
                // Fallback to legacy CSV format for backward compatibility
                parseFromLegacyCsv(str)
            }
        }

        // Legacy CSV parser for backward compatibility
        private fun parseFromLegacyCsv(str: String): HistoricalLocation {
            val fields = mutableListOf<String>()
            var currentField = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < str.length) {
                val char = str[i]
                when {
                    char == '"' && (i + 1 >= str.length || str[i + 1] != '"') -> inQuotes = !inQuotes
                    char == '"' && i + 1 < str.length && str[i + 1] == '"' -> {
                        currentField.append('"'); i++
                    }
                    char == ',' && !inQuotes -> {
                        fields.add(currentField.toString().trim())
                        currentField = StringBuilder()
                    }
                    else -> currentField.append(char)
                }
                i++
            }
            fields.add(currentField.toString().trim())

            if (fields.size < 4) throw IllegalArgumentException("Invalid CSV: $str")

            val latStr = fields[fields.size - 2].trim('"')
            val lonStr = fields[fields.size - 1].trim('"')
            val name = fields.first().trim('"')
            val address = fields.subList(1, fields.size - 2)
                .joinToString(",") { it.trim('"') }

            return HistoricalLocation(name, address, latStr.toDouble(), lonStr.toDouble())
        }
    }

    override fun toString(): String {
        return JSON.toJSONString(this)
    }
}
