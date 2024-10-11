package moe.fuqiuluo.xposed.utils

import android.location.Location
import kotlin.math.max
import kotlin.math.sign
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
        get() = field + ((if(Random.nextBoolean()) -1 else 1) * (Random.nextDouble(1.0, max(2.0, (accuracy * 10000).toDouble())) / 100000.0) * 6.99E-6)
    var longitude = 0.0
        get() = field + ((if(Random.nextBoolean()) 1 else -1) * (Random.nextDouble(1.0, max(2.0, (accuracy * 10000).toDouble())) / 100000.0) * 1.121E-5)
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
    var accuracy = 20.0f

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
}