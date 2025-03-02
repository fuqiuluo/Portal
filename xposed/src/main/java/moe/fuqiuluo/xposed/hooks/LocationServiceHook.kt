@file:Suppress("KotlinConstantConditions")
@file:OptIn(ExperimentalUuidApi::class)

package moe.fuqiuluo.xposed.hooks

import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.RemoteCommandHandler
import moe.fuqiuluo.xposed.hooks.gnss.GnssHook
import moe.fuqiuluo.xposed.hooks.miui.MiuiBlurLocationProviderHook
import moe.fuqiuluo.xposed.hooks.miui.MiuiLocationManagerHook
import moe.fuqiuluo.xposed.hooks.telephony.miui.MiuiTelephonyManagerHook
import moe.fuqiuluo.xposed.hooks.nmea.LocationNMEAHook
import moe.fuqiuluo.xposed.hooks.provider.LocationProviderManagerHook
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.afterHook
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi

private const val MIN_SATELLITES = 8
private const val MAX_SATELLITES = 35 // 北斗系统实际可见卫星数上限

// 载噪比范围，考虑不同轨道类型
private const val GEO_MIN_CN0 = 30.0f  // GEO卫星信号较强
private const val GEO_MAX_CN0 = 45.0f
private const val IGSO_MIN_CN0 = 25.0f
private const val IGSO_MAX_CN0 = 42.0f
private const val MEO_MIN_CN0 = 20.0f  // MEO卫星信号相对较弱
private const val MEO_MAX_CN0 = 40.0f

// 北斗频率
private const val BDS_B1I_FREQ = 1561.098f // MHz
private const val BDS_B2I_FREQ = 1207.140f
private const val BDS_B3I_FREQ = 1268.520f

private val satelliteList = listOf(
    BDSSatellite(1, OrbitType.GEO),
    BDSSatellite(2, OrbitType.GEO),
    BDSSatellite(3, OrbitType.GEO),
    BDSSatellite(4, OrbitType.GEO),
    BDSSatellite(5, OrbitType.GEO),
    BDSSatellite(6, OrbitType.IGSO),
    BDSSatellite(7, OrbitType.IGSO),
    BDSSatellite(8, OrbitType.IGSO),
    BDSSatellite(9, OrbitType.IGSO),
    BDSSatellite(10, OrbitType.IGSO),
    BDSSatellite(11, OrbitType.MEO),
    BDSSatellite(12, OrbitType.MEO),
    BDSSatellite(13, OrbitType.IGSO),
    BDSSatellite(14, OrbitType.MEO),
    BDSSatellite(16, OrbitType.IGSO),
    BDSSatellite(19, OrbitType.MEO),
    BDSSatellite(20, OrbitType.MEO),
    BDSSatellite(21, OrbitType.MEO),
    BDSSatellite(22, OrbitType.MEO),
    BDSSatellite(23, OrbitType.MEO),
    BDSSatellite(24, OrbitType.MEO),
    BDSSatellite(25, OrbitType.MEO),
    BDSSatellite(26, OrbitType.MEO),
    BDSSatellite(27, OrbitType.MEO),
    BDSSatellite(28, OrbitType.MEO),
    BDSSatellite(29, OrbitType.MEO),
    BDSSatellite(30, OrbitType.MEO),
    BDSSatellite(31, OrbitType.IGSO),
    BDSSatellite(32, OrbitType.MEO),
    BDSSatellite(33, OrbitType.MEO),
    BDSSatellite(34, OrbitType.MEO),
    BDSSatellite(35, OrbitType.MEO),
    BDSSatellite(36, OrbitType.MEO),
    BDSSatellite(37, OrbitType.MEO),
    BDSSatellite(38, OrbitType.IGSO),
    BDSSatellite(39, OrbitType.IGSO),
    BDSSatellite(40, OrbitType.IGSO),
    BDSSatellite(41, OrbitType.MEO),
    BDSSatellite(42, OrbitType.MEO),
    BDSSatellite(43, OrbitType.MEO),
    BDSSatellite(44, OrbitType.MEO),
    BDSSatellite(45, OrbitType.MEO),
    BDSSatellite(46, OrbitType.MEO),
    BDSSatellite(56, OrbitType.IGSO),
    BDSSatellite(57, OrbitType.MEO),
    BDSSatellite(58, OrbitType.MEO),
    BDSSatellite(59, OrbitType.GEO),
    BDSSatellite(60, OrbitType.GEO),
    BDSSatellite(61, OrbitType.GEO),
    BDSSatellite(62, OrbitType.GEO),
    BDSSatellite(48, OrbitType.MEO),
    BDSSatellite(50, OrbitType.MEO),
    BDSSatellite(47, OrbitType.MEO),
    BDSSatellite(49, OrbitType.MEO),
//    BDSSatellite(130, OrbitType.GEO),
//    BDSSatellite(143, OrbitType.GEO),
//    BDSSatellite(144, OrbitType.GEO),
)

object GnssFlags {
    // 基本标志位
    const val SVID_FLAGS_NONE = 0
    const val SVID_FLAGS_HAS_EPHEMERIS_DATA = (1 shl 0)
    const val SVID_FLAGS_HAS_ALMANAC_DATA = (1 shl 1)
    const val SVID_FLAGS_USED_IN_FIX = (1 shl 2)
    const val SVID_FLAGS_HAS_CARRIER_FREQUENCY = (1 shl 3)
    const val SVID_FLAGS_HAS_BASEBAND_CN0 = (1 shl 4)

    // 位移宽度
    const val SVID_SHIFT_WIDTH = 12
    const val CONSTELLATION_TYPE_SHIFT_WIDTH = 8
    const val CONSTELLATION_TYPE_MASK = 0xf

    // 星座类型（与 Android GnssStatus.CONSTELLATION_ 常量对应）
    const val CONSTELLATION_GPS = 1
    const val CONSTELLATION_SBAS = 2
    const val CONSTELLATION_GLONASS = 3
    const val CONSTELLATION_QZSS = 4
    const val CONSTELLATION_BEIDOU = 5
    const val CONSTELLATION_GALILEO = 6
    const val CONSTELLATION_IRNSS = 7
}

sealed class OrbitType(val minCn0: Float, val maxCn0: Float, val elevationRange: ClosedRange<Float>) {
    object GEO : OrbitType(GEO_MIN_CN0, GEO_MAX_CN0, 35f..50f)
    object IGSO : OrbitType(IGSO_MIN_CN0, IGSO_MAX_CN0, 20f..60f)
    object MEO : OrbitType(MEO_MIN_CN0, MEO_MAX_CN0, 0f..90f)
}

data class BDSSatellite(
    val prn: Int,
    val type: OrbitType,
)

data class MockGnssData(
    val svCount: Int,
    val svidWithFlags: IntArray,
    val cn0s: FloatArray,
    val elevations: FloatArray,
    val azimuths: FloatArray,
    val carrierFreqs: FloatArray
)

internal object LocationServiceHook: BaseLocationHook() {
    val locationListeners = LinkedBlockingQueue<Pair<String, IInterface>>()

    // A random command is generated to prevent some apps from detecting Portal
    operator fun invoke(classLoader: ClassLoader) {
        val cLocationManagerService = XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", classLoader)
        if (cLocationManagerService == null) {
            hookLocationManagerServiceV2(classLoader)
        } else {
            onService(cLocationManagerService)
        }
        //startDaemon(classLoader)
    }

    fun onService(cILocationManager: Class<*>) {
        // Got instance of ILocationManager.Stub here, you can hook it
        // Not directly Class.forName because of this thing, it can't be reflected, even if I'm system_server?!?!

        if (FakeLoc.enableDebugLog) {
            Logger.debug("ILocationManager.Stub: class = $cILocationManager")
        }

        cILocationManager.classLoader!!.let {
            BasicLocationHook(it)
            GnssHook(it)
            LocationProviderManagerHook(it)

            MiuiBlurLocationProviderHook(it)
            MiuiLocationManagerHook(it)
            MiuiTelephonyManagerHook(it)
        }

        LocationNMEAHook(cILocationManager)

        if(cILocationManager.hookAllMethods("getLastLocation", afterHook {
                // android 7.0.0 ~ 10.0.0
                // Location getLastLocation(in LocationRequest request, String packageName);
                // android 11.0.0
                // Location getLastLocation(in LocationRequest request, String packageName, String featureId);
                // android 12.0.0 ~ 15.0.0
                // @nullable Location getLastLocation(String provider, in LastLocationRequest request, String packageName, @nullable String attributionTag);
                // Why are there so... I'm really speechless

                // Virtual Coordinate: Instantly update the latest virtual coordinates
                // Roulette Move: Each request moves a certain distance
                // Route Simulation: Move according to a preset route
                //val uid = FqlUtils.getCallerUid()
                // Determine whether it is an app that needs a hook
                if (!FakeLoc.enable) return@afterHook

                // It can't be null, because I'm judging in the previous step
                val location = result as? Location ?: Location("gps")

                result = injectLocation(location)

                if(FakeLoc.enableDebugLog) {
                    Logger.debug("getLastLocation: injected! $result")
                }
        }).isEmpty()) {
            Logger.error("hook getLastLocation failed")
        }

        // android 12 and later remove `requestLocationUpdates`
        cILocationManager.hookAllMethods("requestLocationUpdates", beforeHook {
            // android 7.0.0
            // void requestLocationUpdates(in LocationRequest request, in ILocationListener listener, String packageName);
            //
            // oneway interface ILocationListener
            //{
            //    void onLocationChanged(in Location location);
            //    void onStatusChanged(String provider, int status, in Bundle extras);
            //    void onProviderEnabled(String provider);
            //    void onProviderDisabled(String provider);
            //}
            //
            // android 7.1.1 ~ 9.0.0
            // void requestLocationUpdates(in LocationRequest request, in ILocationListener listener,
            //            in PendingIntent intent, String packageName);
            //
            // oneway interface ILocationListener
            //{
            //    void onLocationChanged(in Location location);
            //    void onStatusChanged(String provider, int status, in Bundle extras);
            //    void onProviderEnabled(String provider);
            //    void onProviderDisabled(String provider);
            //
            // android 10.0.0
            // oneway interface ILocationListener
            //{
            //    @UnsupportedAppUsage
            //    void onLocationChanged(in Location location);
            //    @UnsupportedAppUsage
            //    void onProviderEnabled(String provider);
            //    @UnsupportedAppUsage
            //    void onProviderDisabled(String provider);
            //    // --- deprecated ---
            //    @UnsupportedAppUsage
            //    void onStatusChanged(String provider, int status, in Bundle extras);
            //}
            //
            // android 11.0.0
            // void requestLocationUpdates(in LocationRequest request, in ILocationListener listener,
            //            in PendingIntent intent, String packageName, String featureId, String listenerId);
            //
            // oneway interface ILocationListener
            //{
            //    @UnsupportedAppUsage
            //    void onLocationChanged(in Location location);
            //    @UnsupportedAppUsage
            //    void onProviderEnabled(String provider);
            //    @UnsupportedAppUsage
            //    void onProviderDisabled(String provider);
            //    // called when the listener is removed from the server side; no further callbacks are expected
            //    void onRemoved();
            //}
            // android 12 and later
            // remove this method
            val provider = kotlin.runCatching {
                XposedHelpers.callMethod(args[0], "getProvider") as? String
            }.getOrNull() ?: "gps"
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                Logger.error("requestLocationUpdates: listener is null: $method")
                return@beforeHook
            }

            if(FakeLoc.enableDebugLog) {
                Logger.debug("requestLocationUpdates: injected! $listener")
            }

//            if (FakeLoc.enableEnhancedAntiLocRestoration) {
//
//            }

            addLocationListenerInner(provider, listener)

            if (FakeLoc.disableRegisterLocationListener || FakeLoc.enable) {
                result = null
                return@beforeHook
            }

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = null
                return@beforeHook
            }
        })
        cILocationManager.hookAllMethods("removeUpdates", afterHook {
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                Logger.error("removeUpdates: listener is null: $method")
                return@afterHook
            }
            if(FakeLoc.enableDebugLog) {
                Logger.debug("removeUpdates: injected! $listener")
            }

            removeLocationListenerInner(listener)
        })
        cILocationManager.hookAllMethods("registerLocationListener", beforeHook {
            // android 12 ~ android 15
            // void registerLocationListener(String provider, in LocationRequest request, in ILocationListener listener, String packageName, @nullable String attributionTag, String listenerId);
            //
            // oneway interface ILocationListener
            //{
            //    void onLocationChanged(in List<Location> locations, in @nullable IRemoteCallback onCompleteCallback);
            //    void onProviderEnabledChanged(String provider, boolean enabled);
            //    void onFlushComplete(int requestCode);
            //}
            val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                kotlin.runCatching {
                    XposedHelpers.callMethod(args[1], "getProvider") as? String
                }.getOrNull()
            } else {
                args[0] as? String
            } ?: "gps"
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                Logger.error("registerLocationListener: listener is null: $method")
                return@beforeHook
            }

            if(FakeLoc.enableDebugLog) {
                Logger.debug("registerLocationListener: injected! $listener, from ${BinderUtils.getUidPackageNames()}")
            }

            addLocationListenerInner(provider, listener)

            if (FakeLoc.disableRegisterLocationListener) {
                result = null
                return@beforeHook
            }

            if(FakeLoc.enable) {
                result = null
                return@beforeHook
            }

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = null
                return@beforeHook
            }
        })
        cILocationManager.hookAllMethods("unregisterLocationListener", afterHook {
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                Logger.error("unregisterLocationListener: listener is null: $method")
                return@afterHook
            }
            if(FakeLoc.enableDebugLog) {
                Logger.debug("unregisterLocationListener: injected! $listener")
            }

            removeLocationListenerInner(listener)
        })

        run {
            cILocationManager.hookAllMethods("addGnssBatchingCallback", beforeHook {
                if (hasThrowable() || args.isEmpty() || args[0] == null) return@beforeHook
                val callback = args[0] ?: return@beforeHook
                val classCallback = callback.javaClass

                if(FakeLoc.enableDebugLog) {
                    Logger.debug("addGnssBatchingCallback: injected!")
                }

                classCallback.onceHookAllMethod("onLocationBatch", beforeHook onLocationBatch@ {
                    if (args.isEmpty()) return@onLocationBatch

                    if (!FakeLoc.enable) {
                        return@onLocationBatch
                    }

                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("onLocationBatch: injected!")
                    }

                    val location = (args[0] ?: return@onLocationBatch) as Location
                    args[0] = injectLocation(location)
                })
            })
        }

        cILocationManager.hookAllMethods("requestGeofence", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("requestGeofence: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("removeGeofence", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("removeGeofence: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("getFromLocation", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("getFromLocation: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("getFromLocationName", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("getFromLocationName: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("addTestProvider", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("addTestProvider: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("removeTestProvider", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("removeTestProvider: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("setTestProviderLocation", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("setTestProviderLocation: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("setTestProviderEnabled", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("setTestProviderEnabled: injected!")
                }
                result = null
            }
        })


        if(XposedBridge.hookAllMethods(cILocationManager, "registerGnssStatusCallback", object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if(param == null || param.args.isEmpty() || param.args[0] == null) return

                    val callback = param.args[0] ?: return
                    val cIGnssStatusListener = callback.javaClass

                    if (!FakeLoc.enable) {
                        return
                    } else if(FakeLoc.enableDebugLog) {
                        Logger.debug("registerGnssStatusCallback: injected!")
                    }

                    //val cGnssStatus by lazy { XposedHelpers.findClass("android.location.GnssStatus", cIGnssStatusListener.classLoader) }
                    if(XposedBridge.hookAllMethods(cIGnssStatusListener, "onSvStatusChanged", object: XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam?) {
                                if (param == null) return
                                // android 7.0.0
                                // void onSvStatusChanged(int svCount, in int[] svidWithFlags, in float[] cn0s,
                                //            in float[] elevations, in float[] azimuths);
                                // android 8.0.0
                                // void onSvStatusChanged(int svCount, in int[] svidWithFlags, in float[] cn0s,
                                //            in float[] elevations, in float[] azimuths,
                                //            in float[] carrierFreqs);
                                // android 11
                                //  void onSvStatusChanged(int svCount, in int[] svidWithFlags, in float[] cn0s,
                                //            in float[] elevations, in float[] azimuths,
                                //            in float[] carrierFreqs, in float[] basebandCn0s);
                                // android 12 ~ 15
                                // void onSvStatusChanged(in GnssStatus gnssStatus);

                                // https://www.csno-tarc.cn/system/constellation

                                if (!FakeLoc.enable) return

                                val svCount = Random.nextInt(MIN_SATELLITES, MAX_SATELLITES + 1)
                                val mockGps = MockGnssData(
                                    svCount = svCount,
                                    svidWithFlags = IntArray(svCount),
                                    cn0s = FloatArray(svCount),
                                    elevations = FloatArray(svCount),
                                    azimuths = FloatArray(svCount),
                                    carrierFreqs = FloatArray(svCount)
                                ).apply {
                                    val selectedSatellites = satelliteList.shuffled().take(svCount)

                                    selectedSatellites.forEachIndexed { index, sat ->
                                        svidWithFlags[index] = 0

                                        val hasEphemeris = Random.nextFloat() > 0.1f    // 90%概率有星历
                                        val hasAlmanac = Random.nextFloat() > 0.05f     // 95%概率有年历
                                        val usedInFix = Random.nextFloat() > 0.3f       // 70%概率用于定位
                                        val hasCarrierFreq = true                       // 总是有载波频率
                                        val hasBasebandCn0 = true                       // 总是有基带载噪比

                                        var flags = GnssFlags.SVID_FLAGS_NONE

                                        // 设置基本标志位
                                        if (hasEphemeris) flags = flags or GnssFlags.SVID_FLAGS_HAS_EPHEMERIS_DATA
                                        if (hasAlmanac) flags = flags or GnssFlags.SVID_FLAGS_HAS_ALMANAC_DATA
                                        if (usedInFix) flags = flags or GnssFlags.SVID_FLAGS_USED_IN_FIX
                                        if (hasCarrierFreq) flags = flags or GnssFlags.SVID_FLAGS_HAS_CARRIER_FREQUENCY
                                        if (hasBasebandCn0) flags = flags or GnssFlags.SVID_FLAGS_HAS_BASEBAND_CN0

                                        // 组合SVID、星座类型和标志位
                                        svidWithFlags[index] = (sat.prn shl GnssFlags.SVID_SHIFT_WIDTH) or
                                                ((GnssFlags.CONSTELLATION_BEIDOU and GnssFlags.CONSTELLATION_TYPE_MASK) shl GnssFlags.CONSTELLATION_TYPE_SHIFT_WIDTH) or
                                                flags

                                        cn0s[index] = when (sat.type) {
                                            is OrbitType.GEO -> Random.nextFloat(GEO_MIN_CN0, GEO_MAX_CN0)
                                            is OrbitType.IGSO -> Random.nextFloat(IGSO_MIN_CN0, IGSO_MAX_CN0)
                                            is OrbitType.MEO -> Random.nextFloat(MEO_MIN_CN0, MEO_MAX_CN0)
                                        }
                                        elevations[index] = Random.nextFloat(sat.type.elevationRange.start, sat.type.elevationRange.endInclusive)
                                        azimuths[index] = Random.nextFloat(0f, 360f)
                                        carrierFreqs[index] = when (Random.nextInt(3)) {
                                            0 -> BDS_B1I_FREQ
                                            1 -> BDS_B2I_FREQ
                                            else -> BDS_B3I_FREQ
                                        }
                                    }
                                }

                                if (param.args[0] is Int) {
//                                var svCount = param.args[0] as Int
//                                val svidWithFlags = param.args[1] as IntArray
//                                val cn0s = param.args[2] as FloatArray
//                                val elevations = param.args[3] as FloatArray
//                                val azimuths = param.args[4] as FloatArray
//                                val carrierFreqs = if (param.args.size > 5) param.args[5] as FloatArray else null

                                    param.args[0] = svCount
                                    param.args[1] = mockGps.svidWithFlags
                                    param.args[2] = mockGps.cn0s
                                    param.args[3] = mockGps.elevations
                                    param.args[4] = mockGps.azimuths
                                    if (param.args.size > 5) {
                                        param.args[5] = mockGps.carrierFreqs
                                    }

                                    if (param.args.size > 6) {
                                        param.args[6] = FloatArray(svCount) {
                                            mockGps.cn0s[it] - Random.nextFloat(2f, 5f)
                                        }
                                    }
                                    return
                                }

                                if (param.args[0] != null && param.args[0].javaClass.name == "android.location.GnssStatus") {
                                    runCatching {
                                        val mConstructor = param.args[0].javaClass.declaredConstructors.firstOrNull {
                                            it.parameterTypes.size == 7
                                        }.also {
                                            it?.isAccessible = true
                                        }

                                        if (mConstructor != null) {
                                            param.args[0] = mConstructor.newInstance(
                                                svCount,
                                                mockGps.svidWithFlags,
                                                mockGps.cn0s,
                                                mockGps.elevations,
                                                mockGps.azimuths,
                                                mockGps.carrierFreqs,
                                                FloatArray(svCount) {
                                                    mockGps.cn0s[it] - Random.nextFloat(2f, 5f)
                                                }
                                            )
                                        } else {
                                            Logger.error("onSvStatusChanged: unsupported version: ${param.method}, constructor not found")
                                        }
                                    }.onFailure {
                                        XposedBridge.log(it)
                                    }
                                    return
                                }

                                Logger.error("onSvStatusChanged: unsupported version: ${param.method}")
                            }
                        }).isEmpty()) {
                        XposedBridge.log("[Portal] hook onSvStatusChanged failed")
                    }

                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        if(XposedBridge.hookAllMethods(cIGnssStatusListener, "onNmeaReceived", object: XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    if (param == null) return
                                    if (FakeLoc.enableDebugLog) {
                                        Logger.info("onNmeaReceived")
                                    }
                                    param.result = null
                                }
                            }).isEmpty()) {
                            XposedBridge.log("[Portal] hook onNmeaReceived failed")
                        }
                    }
                }
            }).isEmpty()) {
            XposedBridge.log("[Portal] hook registerGnssStatusCallback failed")
        }

        // android 11+
        // @EnforcePermission("LOCATION_HARDWARE")
        // void startGnssBatch(long periodNanos, in ILocationListener listener, String packageName, @nullable String attributionTag, String listenerId);
        //
        // void startGnssBatch(long periodNanos, in ILocationListener listener, String packageName, @nullable String attributionTag, String listenerId);
        cILocationManager.hookAllMethods("startGnssBatch", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("startGnssBatch: injected!")
                }

                val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                    Logger.error("startGnssBatch: listener is null: $method")
                    return@beforeHook
                }

                addLocationListenerInner("GnssBatch", listener)

                if (FakeLoc.disableRegisterLocationListener || FakeLoc.enable) {
                    result = null
                }

                hookILocationListener(listener)
            }
        })
        cILocationManager.hookAllMethods("stopGnssBatch", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("stopGnssBatch: injected!")
                }
            }

            locationListeners.removeIf { it.first == "GnssBatch" }
        })

        //  void requestListenerFlush(String provider, in ILocationListener listener, int requestCode);
        cILocationManager.hookAllMethods("requestListenerFlush", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("requestListenerFlush: injected!")
                }

                val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                    Logger.error("requestListenerFlush: listener is null: $method")
                    return@beforeHook
                }

                addLocationListenerInner("gps", listener)

                if (FakeLoc.disableRegisterLocationListener || FakeLoc.enable) {
                    result = null
                }

                hookILocationListener(listener)
            }
        })

        cILocationManager.hookAllMethods("getBestProvider", beforeHook {
            if (FakeLoc.enable) {
                result = "gps"
            }
        })

        cILocationManager.hookAllMethods("getAllProviders", afterHook {
            if(FakeLoc.enable) {
                result = if (result is List<*>) {
                    listOf("gps", "passive")
                } else if (result is Array<*>) {
                    arrayOf("gps", "passive")
                } else {
                    Logger.error("getAllProviders: result is not List or Array")
                    return@afterHook
                }
            }
        })

        cILocationManager.hookAllMethods("getProviders", afterHook {
            if(FakeLoc.enable) {
                result = if (result is List<*>) {
                    listOf("gps", "passive")
                } else if (result is Array<*>) {
                    arrayOf("gps", "passive")
                } else {
                    Logger.error("getProviders: result is not List or Array")
                    return@afterHook
                }
            }
        })

        cILocationManager.hookAllMethods("hasProvider", beforeHook {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("hasProvider: ${args[0]}")
            }

            if(FakeLoc.enable) {
                if (args[0] == "gps") {
                    result = true
                } else if (args[0] == "network") {
                    result = false
                } else if (args[0] == "fused" && FakeLoc.disableFusedLocation) {
                    result = false
                }
            }
        })

        cILocationManager.hookAllMethods("getCurrentLocation", beforeHook {
            val callback = args[2] ?: return@beforeHook

            if (FakeLoc.enableDebugLog) {
                Logger.debug("getCurrentLocation: injected!")
            }

            if (FakeLoc.disableGetCurrentLocation) {
                result = null
                return@beforeHook
            }

            val classCallback = callback.javaClass
            classCallback.onceHookAllMethod("onLocation", beforeHook onLocation@ {
                val location = args[0] as? Location ?: return@onLocation

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("onLocation(getCurrentLocation): injected!")
                }

                args[0] = injectLocation(location)
            })
        })

        XposedBridge.hookAllMethods(cILocationManager, "sendExtraCommand", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    if (param == null || param.args.size < 3) return

                    val provider = param.args[0] as String
                    val command = param.args[1] as String
                    val result = param.args[2] as? Bundle

                    if (FakeLoc.disableFusedLocation && provider == "fused") {
                        param.result = false
                        return
                    }

                    // If the GPS provider is enabled, the GPS provider is disabled
                    if(provider == "gps" && FakeLoc.enable) {
                        param.result = false
                        return
                    }

                    if(provider == "LOCATION_BIG_DATA") {
                        param.result = false
                        return
                    }

                    // Not the provider of the portal, does not process
                    if (provider != "portal") {
                        if (FakeLoc.enableDebugLog)
                            Logger.debug("sendExtraCommand provider: $provider, command: $command, result: $result")
                        return
                    }
                    if (result == null) return

                    if (handleInstruction(command, result)) {
                        param.result = true
                    }
                }
            })

        if(
        // boolean isProviderEnabledForUser(String provider, int userId); from android 9.0.0
            XposedBridge.hookAllMethods(
                cILocationManager,
                "isProviderEnabledForUser",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null || param.args.size < 2 || param.args[0] == null) return
                        val provider = param.args[0] as String
                        var userId = param.args[1] as Int
                        if (provider == "portal") {
                            if (userId == 0) {
                                userId = BinderUtils.getCallerUid()
                            }
                            param.result = BinderUtils.isLocationProviderEnabled(userId)
                        } else if(provider == "network") {
                            param.result = !FakeLoc.enable
                        } else if (FakeLoc.disableFusedLocation && provider == "fused") {
                            param.result = false
                            return
                        } else {
                            if (FakeLoc.enableDebugLog) {
                                 Logger.debug("isProviderEnabledForUser provider: $provider, userId: $userId")
                            }
                        }
                    }
                }).isEmpty()
        ) {
            // boolean isProviderEnabled(String provider);
            XposedBridge.hookAllMethods(
                cILocationManager,
                "isProviderEnabled",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null || param.args.isEmpty() || param.args[0] == null) return
                        val provider = param.args[0] as String
                        val userId = BinderUtils.getCallerUid()
                        if (provider == "portal" && BinderUtils.isLocationProviderEnabled(userId)) {
                            param.result = true
                        } else if(provider == "network") {
                            param.result = !FakeLoc.enable
                        } else if (FakeLoc.disableFusedLocation && provider == "fused") {
                            param.result = false
                            return
                        }
                    }
                })
        }


        // F**k You! AMAP Service!
        XposedBridge.hookAllMethods(cILocationManager, "setExtraLocationControllerPackageEnabled", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (FakeLoc.enable) {
                    param.args[0] = false
                }
            }
        })

        XposedBridge.hookAllMethods(cILocationManager, "setExtraLocationControllerPackage", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (FakeLoc.enable) {
                    param.result = null
                }
            }
        })

    }

    private fun hookILocationListener(listener: Any) {
        val classListener = listener.javaClass
        if (FakeLoc.enableDebugLog)
            Logger.debug("will hook ILocationListener: ${classListener.name}")

        if(XposedBridge.hookAllMethods(classListener, "onLocationChanged", object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.isEmpty()) return
                    if (!FakeLoc.enable) return

                    when (param.args[0]) {
                        is Location -> {
                            val location = param.args[0] as? Location ?: run {
                                param.result = null
                                return
                            }
                            param.args[0] = injectLocation(location)
                        }

                        is List<*> -> {
                            val locations = param.args[0] as List<*>
                            param.args[0] = locations.map { injectLocation(it as Location) }
                        }
                        else -> Logger.error("onLocationChanged args is not `Location`")
                    }

                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("${param.method}: injected! ${param.args[0]}")
                    }
                }
            }).isEmpty()) {
            Logger.error("hook onLocationChanged failed")
            return // If the hook fails, the listener is not added
        }
    }

//    private fun startDaemon(classLoader: ClassLoader) {
//        //val cIRemoteCallback = XposedHelpers.findClass("android.os.IRemoteCallback", classLoader)
//        thread(
//            name = "LocationUpdater",
//            isDaemon = true,
//            start = true,
//        ) {
//            while (true) {
//                kotlin.runCatching {
//                    if (!FakeLoc.enable) {
//                        Thread.sleep(3000)
//                        return@runCatching
//                    } else {
//                        Thread.sleep(FakeLoc.updateInterval)
//                    }
//
//                    if (!FakeLoc.enable) return@runCatching // Prevent the last loop from being executed
//
//                    if (FakeLoc.enableDebugLog)
//                        Logger.debug("LocationUpdater: callOnLocationChanged: ${locationListeners.size}")
//
//                    callOnLocationChanged()
//                }.onFailure {
//                    Logger.error("LocationUpdater", it)
//                }
//            }
//        }
//    }

    private fun addLocationListenerInner(provider: String, listener: IInterface) {
        val mDeathRecipient = object: IBinder.DeathRecipient {
            override fun binderDied() {}
            override fun binderDied(who: IBinder) {
                who.unlinkToDeath(this, 0)
                removeLocationListenerByBinder(who)
            }
        }
        listener.asBinder().linkToDeath(mDeathRecipient, 0)
        locationListeners.add(provider to listener)
        hookILocationListener(listener)
    }

    private fun removeLocationListenerInner(listener: IInterface) {
        removeLocationListenerByBinder(listener.asBinder())
    }

    private fun removeLocationListenerByBinder(binder: IBinder) {
        locationListeners.removeIf { it.second.asBinder() == binder }
    }

    fun callOnLocationChanged() {
        if (FakeLoc.enableDebugLog) {
            Logger.debug("==> callOnLocationChanged: ${locationListeners.size}")
        }
        locationListeners.forEach { listenerWithProvider ->
            val listener = listenerWithProvider.second
            var location = FakeLoc.lastLocation
            if (location == null) {
                location = if (listenerWithProvider.first == "GnssBatch") {
                    Location("gps")
                } else {
                    Location(listenerWithProvider.first)
                }
            }
            location = injectLocation(location)
            var called = false
            var error: Throwable? = null
            kotlin.runCatching {
                val locations = listOf(location)
                val mOnLocationChanged = XposedHelpers.findMethodBestMatch(listener.javaClass, "onLocationChanged", locations, null)
                XposedBridge.invokeOriginalMethod(mOnLocationChanged, listener, arrayOf(locations, null))
                called = true
            }.onFailure {
                if (it is InvocationTargetException && it.targetException is DeadObjectException) {
                    return@forEach
                }
                error = it
            }

            if (!called) runCatching {
                val mOnLocationChanged = XposedHelpers.findMethodBestMatch(listener.javaClass, "onLocationChanged", location)
                XposedBridge.invokeOriginalMethod(mOnLocationChanged, listener, arrayOf(location))
                called = true
            }.onFailure {
                if (it is InvocationTargetException && it.targetException is DeadObjectException) {
                    return@forEach
                }
                error = it
            }

            if (!called) {
                Logger.error("callOnLocationChanged failed: " + error?.stackTraceToString())
                Logger.error("The listener all methods: " + listener.javaClass.declaredMethods.joinToString { it.name })
            }
        }

        if (FakeLoc.enableDebugLog) {
            Logger.debug("==> callOnLocationChanged: end")
        }
    }

    private fun hookLocationManagerServiceV2(classLoader: ClassLoader) {
        // As a system_server, the hook can get all the location information here
        kotlin.runCatching {
            XposedHelpers.findClass("android.location.ILocationManager\$Stub", classLoader)
        }.onSuccess {
            fun hookOnTransactForServiceInstance(m: Method) {
                val isHooked = AtomicBoolean(false)
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param?.thisObject == null || param.args.size < 4) return

                        val thisObject = param.thisObject
                        val code = param.args[0] as? Int ?: return
                        val data = param.args[1] as? Parcel ?: return
                        val reply = param.args[2] as? Parcel ?: return
                        val flags = param.args[3] as? Int ?: return

                        if (isHooked.compareAndSet(false, true)) {
                            onService(thisObject.javaClass)
                        }

                        if (!FakeLoc.enable) {
                            return
                        }

                        // TRANSACTION_isProviderEnabledForUser
                        when (code) {
                            47 -> {
                                val provider = data.readString()
                                val userId = data.readInt()
                                if (provider == "portal") {
                                    if (userId == 0) {
                                        val uid = BinderUtils.getCallerUid()
                                        reply.writeNoException()
                                        reply.writeInt(if (BinderUtils.isLocationProviderEnabled(uid)) 1 else 0)
                                    }
                                } else if(provider == "gps") {
                                    reply.writeNoException()
                                    reply.writeInt(1)
                                } else if (!FakeLoc.disableFusedLocation && provider == "fused") {
                                    reply.writeNoException()
                                    reply.writeInt(1)
                                } else {
                                    reply.writeNoException()
                                    reply.writeInt(0)
                                }
                                param.result = true
                            }

                        }

                        if (FakeLoc.enable && code == 43) {
                            param.result = true
                        }

                        if (FakeLoc.enableDebugLog) {
                            Logger.debug("ILocationManager.Stub: onTransact(code=$code)")
                        }
                    }
                })
            }

            it.declaredMethods.forEach {
                if (it.name == "onTransact") {
                    hookOnTransactForServiceInstance(it)

                    // Hey, hey, you've found onTransact, what else are you looking for
                    // It's time to end the cycle! BaKa!
                    return@forEach
                }
            }
        }.onFailure {
            Logger.error("ILocationManager.Stub not found", it)
        }

//        // This is the intrusive hook
//        kotlin.runCatching {
//            XposedHelpers.findClass("android.location.ILocationManager\$Stub\$Proxy", cLocationManager.classLoader)
//        }.onSuccess {
//            it.declaredMethods.forEach {
//                XposedBridge.hookMethod(it, object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam?) {
//                        if (param == null) return
//
//                        XposedBridge.log("[Portal] ILocationManager.Stub.Proxy: c = ${param.thisObject?.javaClass}, m = ${param.method}")
//                    }
//                })
//            }
//        }
    }

    private inline fun handleInstruction(command: String, rely: Bundle): Boolean {
        return RemoteCommandHandler.handleInstruction(command, rely)
    }
}

private fun Random.nextFloat(min: Float, max: Float): Float {
    return nextFloat() * (max - min) + min
}
