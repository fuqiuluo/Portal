package moe.fuqiuluo.portal.ext

import com.baidu.location.BDLocation
import com.baidu.location.Jni
import com.baidu.mapapi.model.LatLng

val LatLng.wgs84: Pair<Double, Double>
    get() = Loc4j.gcj2wgs(latitude, longitude)

val BDLocation.wgs84: Pair<Double, Double>
    get() = Loc4j.gcj2wgs(latitude, longitude)

val Pair<Double, Double>.gcj02: LatLng
    get() = Loc4j.wgs2gcj(first, second).let { LatLng(it.first, it.second) }

object Loc4j {
    fun gcj2wgs(lat: Double, lon: Double): Pair<Double, Double> {
        return Jni.coorEncrypt(lon, lat, "gcj2wgs").let { it[1] to it[0] }
    }

    fun wgs2gcj(lat: Double, lon: Double): Pair<Double, Double> {
        return Jni.coorEncrypt(lon, lat, "gps2gcj").let { it[1] to it[0] }
    }
}