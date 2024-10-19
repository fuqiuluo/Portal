package moe.fuqiuluo.xposed.hooks

import android.location.Location
import android.location.LocationManager
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.hooks.blindhook.BlindHookLocation
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.hookAllMethodsAfter
import moe.fuqiuluo.xposed.utils.hookAllMethodsBefore
import moe.fuqiuluo.xposed.utils.toClass
import moe.fuqiuluo.xposed.utils.toClassOrThrow
import kotlin.random.Random

object BasicLocationHook: BaseLocationHook() {
    operator fun invoke(classLoader: ClassLoader) {
//        val hookSetLatitude = object: XC_MethodHook() {
//            override fun beforeHookedMethod(param: MethodHookParam?) {
//                if (param == null) return
//                if (!FakeLocationConfig.enable) return
//
//            }
//        }
//        val hookSetLongitude = object: XC_MethodHook() {
//            override fun beforeHookedMethod(param: MethodHookParam?) {
//                if (param == null) return
//
//
//            }
//        }
//        XposedHelpers.findAndHookMethod("android.location.Location", classLoader, "setLatitude", Double::class.java, hookSetLatitude)
//        XposedHelpers.findAndHookMethod("android.location.Location", classLoader, "setLongitude", Double::class.java, hookSetLongitude)

        kotlin.runCatching {
            val cLocationResult = "android.location.LocationResult".toClassOrThrow(classLoader)
            BlindHookLocation(cLocationResult)

            cLocationResult.hookAllMethodsAfter("asList") {
                if (!FakeLoc.enable) return@hookAllMethodsAfter

                val locations = result as List<*>
                result = locations.map { injectLocation(it as Location) }
            }

            cLocationResult.hookAllMethodsBefore("writeToParcel") {
                if (!FakeLoc.enable) return@hookAllMethodsBefore

                val locationResult = thisObject
                val mLocationsField = XposedHelpers.findFieldIfExists(locationResult.javaClass, "mLocations")
                if (mLocationsField == null) {
                    Logger.error("Failed to find mLocations in LocationResult")
                    return@hookAllMethodsBefore
                }
                mLocationsField.isAccessible = true
                val mLocations = mLocationsField.get(locationResult) as ArrayList<*>

                val originLocation = mLocations.firstOrNull() as? Location
                    ?: Location(LocationManager.GPS_PROVIDER)

                val location = Location(originLocation.provider)

                val jitterLat = FakeLoc.jitterLocation()
                location.latitude = jitterLat.first
                location.longitude = jitterLat.second
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    location.isMock = false
                }
                location.altitude = FakeLoc.altitude
                location.speed = originLocation.speed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    location.speedAccuracyMetersPerSecond = 0F
                }

                location.time = originLocation.time
                location.accuracy = originLocation.accuracy
                var modBearing = FakeLoc.bearing % 360.0 + 0.0
                if (modBearing < 0) {
                    modBearing += 360.0
                }
                if (location.hasBearing()) {
                    location.bearing = modBearing.toFloat()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    location.bearingAccuracyDegrees = modBearing.toFloat()
                }
                location.elapsedRealtimeNanos = originLocation.elapsedRealtimeNanos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    location.elapsedRealtimeUncertaintyNanos = originLocation.elapsedRealtimeUncertaintyNanos
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    location.verticalAccuracyMeters = originLocation.verticalAccuracyMeters
                }
                originLocation.extras?.let {
                    location.extras = it
                }

                mLocationsField.set(locationResult, arrayListOf(location))
            }
        }.onFailure {
           Logger.error("Failed to hook LocationResult", it)
        }

//        XposedHelpers.findAndHookMethod(Location::class.java, "getLatitude", object : XC_MethodHook() {
//            override fun beforeHookedMethod(param: MethodHookParam) {
//                if (!FakeLocationConfig.enable) return
//                param.result = FakeLocationConfig.latitude + ((if(Random.nextBoolean()) -1 else 1) * (Random.nextInt(1, (FakeLocationConfig.accuracy * 10000).toInt()).toDouble() / 10000.0) * 8.99E-6)
//            }
//        })
//
//        XposedHelpers.findAndHookMethod(Location::class.java, "getLongitude", object : XC_MethodHook() {
//            override fun beforeHookedMethod(param: MethodHookParam) {
//                if (!FakeLocationConfig.enable) return
//                param.result = FakeLocationConfig.longitude + ((if(Random.nextBoolean()) -1 else 1) * (Random.nextInt(1, (FakeLocationConfig.accuracy * 10000).toInt()).toDouble() / 10000.0) * 8.99E-6)
//            }
//        })

        Location::class.java.hookAllMethodsBefore("set") {
            if (!FakeLoc.enable) return@hookAllMethodsBefore
            args[0] = injectLocation(args[0] as Location)
        }
//        if (FakeLocationConfig.DEBUG) {
//            // Track the invocation of AutoNavi map system services
//            val cBundle = XposedHelpers.findClass("android.os.Bundle", classLoader)
//            XposedBridge.hookAllMethods(cBundle, "putInt", object : XC_MethodHook() {
//                override fun beforeHookedMethod(param: MethodHookParam) {
//                    val key = param.args[0] as? String
//                    val value = param.args[1] as Int
//
//                    if (key == "amap" || key == "resubtype" || key == "maxCn0") {
//                        XposedBridge.log(RuntimeException())
//                    }
//                }
//            })
//            XposedBridge.hookAllMethods(Location::class.java, "setExtras", object : XC_MethodHook() {
//                override fun beforeHookedMethod(param: MethodHookParam) {
//                    XposedBridge.log(RuntimeException())
//                }
//            })
//        }


    }
}