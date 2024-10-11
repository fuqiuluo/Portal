package moe.fuqiuluo.xposed.utils

import android.location.Location
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object FakeLoc {
    var enableLog = true
    var enableDebugLog = true

    var enable = false

    var disableGetCurrentLocation = true
    var disableRegisterLocationListener = false

    /**
     * 如果TelephonyHook失效，可能需要打开此开关
     */
    var disableFusedLocation = true

    var enableAGPS = false
    var enableNMEA = true
    var hideMock = true
    var autoRemoveUselessLocListener = false

    /**
     * may cause system to crash
     */
    var hookWifi = true
    var needDowngradeTo2G = true

//
//    /**
//     * Whitelists and blacklists mode
//     */
//    var accessMode = AccessMode.None
//    val accessList = arrayListOf<Int>()

    var updateInterval = 3000L

//    /**
//     * enable fake location
//     */
//    fun isEnableHookApp(uid: Int): Boolean {
//        return enable && when(accessMode) {
//            AccessMode.BLACK_LIST -> !accessList.contains(uid)
//            AccessMode.WHITE_LIST -> accessList.contains(uid)
//            AccessMode.None -> true
//        }
//    }

//    enum class AccessMode {
//        BLACK_LIST,
//        WHITE_LIST,
//        None
//    }

    var lastLocation: Location? = null
    var latitude = 0.0
    var longitude = 0.0
    var altitude = 80.0
    var speed = 0.0
    var speedAmplitude = 1.0
    var hasBearings = false
    var bearing = 0.0
        get() {
            if (hasBearings) {
                return field
            } else {
                if (field >= 360.0) {
                    field -= 360.0
                }
                field += 0.5
                return field
            }
        }
    var accuracy = 5.0f
        set(value) {
            field = if (value < 0) {
                -value
            } else {
                value
            }
        }

    fun randomOffset(): Pair<Double, Double> {
        val offset = 0.000045
        return ((Math.random() - 0.5) * offset) to ((Math.random() - 0.5) * offset)
    }

//    /**
//     * Test location
//     */
//    const val TEST_LAT = 37.86
//    const val TEST_LON = 112.58
//    // 4m/s
//    const val TEST_SPEED = 4.0f
//    // azimuth
//    const val TEST_BEARING = 0.0f

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radius * c
    }

    fun jitterLocation(lat: Double = latitude, lon: Double = longitude, n: Double = Random.nextDouble(-accuracy.toDouble(), accuracy.toDouble()), angle: Double = bearing): Pair<Double, Double> {
        val earthRadius = 6371000.0
        val radiusInDegrees = n / earthRadius * (180 / PI)
        val newLat = lat + radiusInDegrees * cos(Math.toRadians(angle))
        val newLon = lon + radiusInDegrees * sin(Math.toRadians(angle)) / cos(Math.toRadians(lat))
        return Pair(newLat, newLon)
    }
}