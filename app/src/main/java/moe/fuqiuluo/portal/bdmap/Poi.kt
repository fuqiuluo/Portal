package moe.fuqiuluo.portal.bdmap

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Poi(
    val name: String,
    var address: String,
    val longitude: Double,
    val latitude: Double,

    val tag: String,
) {
    companion object {
        const val KEY_NAME = "name"
        const val KEY_ADDRESS = "address"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE_RAW = "longitude_raw"
        const val KEY_LATITUDE_RAW = "latitude_raw"
        const val KEY_TAG = "tag"
    }

    fun toMap(): Map<String, String> {
        return mapOf(
            KEY_NAME to name,
            KEY_ADDRESS to address,
            KEY_LONGITUDE to longitude.toString().take(5),
            KEY_LATITUDE to latitude.toString().take(5),
            KEY_TAG to tag,
            KEY_LONGITUDE_RAW to longitude.toString(),
            KEY_LATITUDE_RAW to latitude.toString(),
        )
    }

    /**
     * Calculate the distance between two points on the Earth's surface.
     * @param other The other point.
     * @return The distance in meters.
     */
    fun distanceTo(other: Poi): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLng = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun distanceTo(lat: Double, lng: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat - latitude)
        val dLng = Math.toRadians(lng - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
