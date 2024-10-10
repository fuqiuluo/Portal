package moe.fuqiuluo.portal.ui.mock

import java.math.BigDecimal

data class HistoryLocation(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double
) {
    companion object {
        // $name,$address,${newLat},${newLon}
        fun fromString(str: String): HistoryLocation {
            val parts = str.split(",")
            if (parts.size != 4) {
                throw IllegalArgumentException("Invalid string: $str")
            }
            return HistoryLocation(
                parts[0],
                parts[1],
                parts[2].toDouble(),
                parts[3].toDouble()
            )
        }
    }

    override fun toString(): String {
        val plainLat = BigDecimal(lat).toPlainString()
        val plainLon = BigDecimal(lon).toPlainString()
        return "$name,$address,$plainLat,$plainLon"
    }
}
