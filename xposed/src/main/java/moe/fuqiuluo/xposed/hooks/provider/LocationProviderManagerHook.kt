package moe.fuqiuluo.xposed.hooks.provider

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.CellIdentity
import android.telephony.CellInfo
import android.util.ArrayMap
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.hooks.BasicLocationHook.injectLocation
import moe.fuqiuluo.xposed.hooks.blindhook.BlindHookLocation
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.diyHook
import moe.fuqiuluo.xposed.utils.hook
import moe.fuqiuluo.xposed.utils.onceHook
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import moe.fuqiuluo.xposed.utils.onceHookMethodBefore
import java.util.Collections
import kotlin.random.Random

object LocationProviderManagerHook {
    private val hookOnFetchLocationResult = beforeHook {
        if (args.isEmpty() || args.isEmpty()) return@beforeHook
        if (!FakeLoc.enable) return@beforeHook

        if (FakeLoc.enableDebugLog) {
            Logger.debug("${method}: injected!")
        }

        val locationResult = args[0]
        val mLocationsField = XposedHelpers.findFieldIfExists(locationResult.javaClass, "mLocations")
        if (mLocationsField == null) {
            Logger.error("Failed to find mLocations in LocationResult")
            return@beforeHook
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
        location.bearing = modBearing.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && originLocation.hasBearingAccuracy()) {
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

    operator fun invoke(classLoader: ClassLoader) {
        hookLocationProviderManager(classLoader)
        hookDelegateLocationProvider(classLoader)
        hookPassiveLocationProvider(classLoader)
        hookProxyLocationProvider(classLoader)
        hookAbstractLocationProvider(classLoader)
        hookOtherProvider(classLoader)
        hookGeofenceProvider(classLoader)
    }

    private fun hookAbstractLocationProvider(classLoader: ClassLoader) {
        run {
            val cAbstractLocationProvider = XposedHelpers.findClassIfExists("com.android.server.location.provider.AbstractLocationProvider", classLoader)
                ?: return@run
            val cLocationResult = XposedHelpers.findClassIfExists("android.location.LocationResult", classLoader)
                ?: return@run
            val mReportLocation = XposedHelpers.findMethodExactIfExists(cAbstractLocationProvider.javaClass, "reportLocation", cLocationResult)
                ?: return@run

            mReportLocation.onceHook(hookOnFetchLocationResult)
        }

        run {
            val cInternalState = XposedHelpers.findClassIfExists("com.android.server.location.provider.AbstractLocationProvider\$InternalState", classLoader)
                ?: return@run

            XposedBridge.hookAllConstructors(cInternalState, object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val listener = param.args[0] ?: return

                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("AbstractLocationProvider.InternalState: injected!")
                    }

                    // will hook class AbstractLocationProvider.Listener, Be careful not to repeat the hooker!
                    listener.javaClass.onceHookAllMethod("onReportLocation", hookOnFetchLocationResult)
                }
            })
        }

    }

    private fun hookProxyLocationProvider(classLoader: ClassLoader) {
        val cProxyLocationProvider = XposedHelpers.findClassIfExists("com.android.server.location.provider.proxy.ProxyLocationProvider", classLoader)
            ?: return


    }

    private fun hookPassiveLocationProvider(classLoader: ClassLoader) {
        val cPassiveLocationProvider = XposedHelpers.findClassIfExists("com.android.server.location.provider.PassiveLocationProvider", classLoader)
            ?: return
        val cLocationResult = XposedHelpers.findClassIfExists("android.location.LocationResult", classLoader)
            ?: return
        val updateLocation = XposedHelpers.findMethodExactIfExists(cPassiveLocationProvider, "updateLocation", cLocationResult)
            ?: return

        updateLocation.hook(hookOnFetchLocationResult)
    }

    private fun hookDelegateLocationProvider(classLoader: ClassLoader) {
        val cDelegateLocationProvider = XposedHelpers.findClassIfExists("com.android.server.location.provider.DelegateLocationProvider", classLoader)
            ?: return

        val waitForInitialization = XposedHelpers.findMethodExactIfExists(cDelegateLocationProvider, "waitForInitialization") ?: return
        waitForInitialization.diyHook(
            hookOnce = true,
            before = {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("DelegateLocationProvider.waitForInitialization: injected!")
                }

                val cLocationResult = XposedHelpers.findClassIfExists("android.location.LocationResult", classLoader)
                    ?: return@diyHook true
                XposedHelpers.findMethodExactIfExists(thisObject.javaClass, "onReportLocation", cLocationResult)?.onceHook(hookOnFetchLocationResult)
                XposedHelpers.findMethodExactIfExists(thisObject.javaClass, "reportLocation", cLocationResult)?.onceHook(hookOnFetchLocationResult)

                return@diyHook true
            }
        )
    }

    private fun hookLocationProviderManager(classLoader: ClassLoader) {
        val cLocationProviderManager = XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager", classLoader)
            ?: return
        BlindHookLocation(cLocationProviderManager, classLoader)

        XposedBridge.hookAllMethods(cLocationProviderManager, "setRealProvider", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val locationProvider = param.args[0]
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("setRealProvider: $locationProvider")
                }
            }
        })
        XposedBridge.hookAllMethods(cLocationProviderManager, "setMockProvider", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val locationProvider = param.args[0]
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("setMockProvider: $locationProvider")
                }
            }
        })
        XposedBridge.hookAllMethods(cLocationProviderManager, "sendExtraCommand", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if(param.args.size < 4) return
                val command = param.args[2]

                if (command == "force_xtra_injection" || command == "CMD_SHOW_GPS_TIPS_CONFIG") {
                    param.result = null
                    return
                }

                val extras = param.args[3]
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("sendExtraCommand: $command, $extras")
                }
            }
        })

        run {
            val hookedListeners = Collections.synchronizedSet(HashSet<String>())
            if(cLocationProviderManager.onceHookAllMethod("getCurrentLocation", object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.size < 4 || param.args[3] == null) return

                    val callback = param.args[3]
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("getCurrentLocation injected: $callback")
                    }

                    if(FakeLoc.disableGetCurrentLocation) {
                        param.result = null
                        return
                    }

                    val classCallback = callback.javaClass
                    if (hookedListeners.contains(classCallback.name)) return // Prevent repeated hooking
                    if (XposedBridge.hookAllMethods(classCallback, "onLocation", object: XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam?) {
                            if (param == null || param.args.isEmpty()) return
                            val location = (param.args[0] ?: return) as Location

                            if (FakeLoc.enableDebugLog)
                                Logger.debug("onLocation(LocationProviderManager.getCurrentLocation): injected!")
                            param.args[0] = injectLocation(location)
                        }
                    }).isEmpty()) {
                        Logger.error("hook onLocation(LocationProviderManager.getCurrentLocation) failed")
                    }

                    hookedListeners.add(classCallback.name)
                }
            }).isEmpty()) {
                Logger.error("hook LocationProviderManager.getCurrentLocation failed")
            }
        }

        cLocationProviderManager.onceHookMethodBefore("onReportLocation") {
            val fieldMRegistrations = XposedHelpers.findFieldIfExists(cLocationProviderManager, "mRegistrations")
            if (fieldMRegistrations == null) {
                Logger.error("Failed to find mRegistrations in LocationProviderManager")
                return@onceHookMethodBefore
            }
            if (!fieldMRegistrations.isAccessible)
                fieldMRegistrations.isAccessible = true

            if (!FakeLoc.enable) {
                return@onceHookMethodBefore
            }

            val registrations = fieldMRegistrations.get(thisObject) as ArrayMap<*, *>
            val newRegistrations = ArrayMap<Any, Any>()
            registrations.forEach { registration ->
                val value = registration.value ?: return@forEach
                val locationResult = args[0]

                val mLocationsField = XposedHelpers.findFieldIfExists(locationResult.javaClass, "mLocations")
                if (mLocationsField == null) {
                    Logger.error("Failed to find mLocations in LocationResult")
                    return@onceHookMethodBefore
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
                location.bearing = modBearing.toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && originLocation.hasBearingAccuracy()) {
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

                val operation = XposedHelpers.callMethod(value, "acceptLocationChange", locationResult)
                XposedHelpers.callMethod(value, "executeOperation", operation)
            }

            if (FakeLoc.enableDebugLog) {
                Logger.debug("onReportLocation: injected!")
            }

            fieldMRegistrations.set(thisObject, newRegistrations)
        }
    }

    private fun hookGeofenceProvider(classLoader: ClassLoader) {
        val cGeofenceManager = XposedHelpers.findClassIfExists("com.android.server.geofence.GeofenceManager", classLoader)
            ?: return
        BlindHookLocation(cGeofenceManager, classLoader)
    }

    private fun hookOtherProvider(classLoader: ClassLoader) {
        kotlin.runCatching {
            val cGnssLocationProvider = XposedHelpers.findClassIfExists("com.android.location.provider.LocationProviderBase", classLoader)
                ?: return@runCatching
            if(BlindHookLocation(cGnssLocationProvider, classLoader) == 0) {
                cGnssLocationProvider.onceHookMethodBefore("reportLocation", Location::class.java) {
                    if (!FakeLoc.enable) return@onceHookMethodBefore
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("LocationProviderBase.reportLocation: injected!")
                    }
                    args[0] = injectLocation(args[0] as Location)
                }
            }
            cGnssLocationProvider.onceHookMethodBefore("reportLocations", List::class.java) {
                if (!FakeLoc.enable) return@onceHookMethodBefore
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("LocationProviderBase.reportLocations: injected!")
                }
                args[0] = (args[0] as List<*>).map {
                    injectLocation(it as Location)
                }
            }
        }.onFailure {
            Logger.warn("Failed to hook LocationProviderBase", it)
        }

        kotlin.runCatching {
            val cGnssLocationProvider = XposedHelpers.findClass("com.android.server.location.gnss.GnssLocationProvider", classLoader)
            cGnssLocationProvider.onceHookMethodBefore("onReportLocation", Boolean::class.java, Location::class.java) {
                if (!FakeLoc.enable) return@onceHookMethodBefore

                args[1] = injectLocation(args[1] as Location)

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("GnssLocationProvider.onReportLocation: injected! ${args[1]}")
                }
            }

            cGnssLocationProvider.onceHookMethodBefore("onReportLocations", Boolean::class.java, Array<Location>::class.java) {
                if (!FakeLoc.enable) return@onceHookMethodBefore

                args[0] = (args[0] as Array<*>).map {
                    injectLocation(it as Location)
                }.toTypedArray()

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("GnssLocationProvider.onReportLocations: injected! ${args[0]}")
                }
            }

            cGnssLocationProvider.onceHookMethodBefore("getCellType", CellInfo::class.java) {
                if (!FakeLoc.enable) return@onceHookMethodBefore
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("GnssLocationProvider.getCellType: injected!")
                }

                result = 0
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cGnssLocationProvider.onceHookMethodBefore("getCidFromCellIdentity", CellIdentity::class.java) {
                    if (!FakeLoc.enable) return@onceHookMethodBefore
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("GnssLocationProvider.getCidFromCellIdentity: injected!")
                    }

                    result = -1L
                }

                cGnssLocationProvider.onceHookMethodBefore("setRefLocation", Int::class.java, CellIdentity::class.java) {
                    if (!FakeLoc.enable) return@onceHookMethodBefore
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("GnssLocationProvider.setRefLocation: injected!")
                    }

                    args[0] = 114514 // disable AGPS
                }
            }
        }.onFailure {
            Logger.warn("Failed to hook GnssLocationProvider", it)
        }
    }
}