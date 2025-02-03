package moe.fuqiuluo.portal.bdmap

import android.util.Log
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.search.sug.SuggestionResult
import moe.fuqiuluo.portal.ext.Loc4j

fun SuggestionResult.toPoi(
    currentLocation: Pair<Double, Double>? = null
) = this.allSuggestions.map {
    val gcj02Lat = it.pt.latitude
    val gcj02Lon = it.pt.longitude
    val (lat, lon) = Loc4j.gcj2wgs(gcj02Lat, gcj02Lon)
    if (currentLocation != null) {
        Log.d("toPoi", "currentLocation: $currentLocation, lat: $lat, lon: $lon")
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

fun BaiduMap.setMapConfig(mode: MyLocationConfiguration.LocationMode, resourceId: Int?) {
    setMyLocationConfiguration(MyLocationConfiguration(mode, true,  resourceId?.let { BitmapDescriptorFactory.fromResource(it) }))
}

fun BaiduMap.locateMe() {
    setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.FOLLOWING, true, null))
    setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null))
}