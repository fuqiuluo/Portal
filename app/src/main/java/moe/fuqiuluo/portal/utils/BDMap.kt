package moe.fuqiuluo.portal.utils

import com.baidu.location.Jni
import com.baidu.mapapi.search.sug.SuggestionResult

fun SuggestionResult.toPoi(
    currentLocation: Pair<Double, Double>? = null
) = this.allSuggestions.map {
    val gcj02Lat = it.pt.latitude
    val gcj02Lon = it.pt.longitude
    val (lon, lat) = Jni.coorEncrypt(gcj02Lon, gcj02Lat, "gcj2wgs")
    if (currentLocation != null) {
        Poi(
            name = it.key,
            address = it.city + " " + it.district,
            longitude = lon,
            latitude = lat,
            tag = it.tag,
        ).also {
            val distance = it.distanceTo(currentLocation.first, currentLocation.second).toInt()
            if (distance < 1000) {
                it.address = "${distance}m ${it.address}"
            } else {
                it.address = "${(distance / 1000.0).toString().take(4)}km ${it.address}"
            }
        }
    } else {
        Poi(
            name = it.key,
            address = it.city + " " + it.district,
            longitude = lon,
            latitude = lat,
            tag = it.tag,
        )
    }
}

