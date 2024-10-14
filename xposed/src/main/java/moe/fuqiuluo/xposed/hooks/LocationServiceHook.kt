@file:Suppress("KotlinConstantConditions")
@file:OptIn(ExperimentalUuidApi::class)

package moe.fuqiuluo.xposed.hooks

import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IInterface
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.RemoteCommandHandler
import moe.fuqiuluo.xposed.hooks.gnss.GnssManagerServiceHook
import moe.fuqiuluo.xposed.hooks.miui.MiuiBlurLocationProviderHook
import moe.fuqiuluo.xposed.hooks.miui.MiuiTelephonyManagerHook
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object LocationServiceHook: BaseLocationHook() {
    val locationListeners = ConcurrentHashMap<String, Pair<String, Any>>()

    // A random command is generated to prevent some apps from detecting Portal
    operator fun invoke(classLoader: ClassLoader) {
        val cLocationManagerService = XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", classLoader)
        if (cLocationManagerService == null) {
            hookLocationManagerServiceV2(classLoader)
        } else {
            onService(cLocationManagerService)
        }
        startDaemon(classLoader)
    }

    private fun onService(cILocationManager: Class<*>) {
        // Got instance of ILocationManager.Stub here, you can hook it
        // Not directly Class.forName because of this thing, it can't be reflected, even if I'm system_server?!?!

        if (FakeLoc.enableDebugLog) {
            Logger.debug("ILocationManager.Stub: class = $cILocationManager")
        }

        cILocationManager.classLoader!!.let {
            BasicLocationHook(it)
            GnssManagerServiceHook(it)
            LocationProviderManagerHook(it)

            MiuiBlurLocationProviderHook(it)
            MiuiTelephonyManagerHook(it)
        }

        LocationNMEAHook(cILocationManager)

        if(cILocationManager.hookAllMethods("getLastLocation", afterHook {
                if (result == null) return@afterHook
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
                if (!FakeLoc.enable || BinderUtils.isSystemAppsCall()) return@afterHook

                // It can't be null, because I'm judging in the previous step
                val location = result as Location

//                if (!FakeLocationConfig.isInitTempLocation()) {
//                    FakeLocationConfig.initTempLocation(location)
//                }

                result = injectLocation(location)

                if(FakeLoc.enableDebugLog) {
                    Logger.debug("getLastLocation: injected! ${result}")
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
            if (hasThrowable()) return@beforeHook

            val provider = kotlin.runCatching {
                XposedHelpers.callMethod(args[0], "getProvider") as? String
            }.getOrNull() ?: "gps"
            val listener = args[1] ?: return@beforeHook

            if(FakeLoc.enableDebugLog) {
                Logger.debug("requestLocationUpdates: injected! $listener")
            }

            val listenerKey = Uuid.random().toHexString()
            if (listener is IInterface) {
                val binder = listener.asBinder()
                binder.linkToDeath({
                    removeLocationListener(listenerKey)
                }, 0)
            }
            locationListeners[listenerKey] = provider to listener
            hookILocationListener(listener)

            if (FakeLoc.disableRegisterLocationListener) {
                result = null
                return@beforeHook
            }

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = null
                return@beforeHook
            }

            // milliseconds
//                    val updateInterval = kotlin.runCatching {
//                        val currentInterval = XposedHelpers.getLongField(request, "mInterval")
//                        if (currentInterval > FakeLocationConfig.updateInterval) {
//                            FakeLocationConfig.updateInterval
//                        } else {
//                            currentInterval
//                        }
//                    }.getOrElse {
//                        runCatching {
//                            XposedHelpers.callMethod(request, "getInterval") as Long
//                        }.getOrElse {
//                            FakeLocationConfig.updateInterval
//                        }
//                    }
//                    val updateFastestInterval = kotlin.runCatching {
//                        val currentInterval = XposedHelpers.getLongField(request, "mFastestInterval")
//                        if (currentInterval > FakeLocationConfig.updateInterval) {
//                            FakeLocationConfig.updateInterval
//                        } else {
//                            currentInterval
//                        }
//                    }.getOrElse {
//                        kotlin.runCatching {
//                            XposedHelpers.callMethod(request, "getFastestInterval") as Long
//                        }.getOrElse {
//                            (updateInterval / 6.0).toLong()
//                        }
//                    }
//                    kotlin.runCatching {
//                        XposedHelpers.callMethod(thisObject, "setInterval", updateInterval)
//                        XposedHelpers.callMethod(thisObject, "setFastestInterval", updateFastestInterval)
//                    }.onFailure {
//                        XposedBridge.log("[Portal] modify locationRequest failed: ${it.stackTraceToString()}")
//                    }
        })
        cILocationManager.hookAllMethods("removeUpdates", afterHook {

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
            val listener = args[2] ?: return@beforeHook

            if(FakeLoc.enableDebugLog) {
                Logger.debug("registerLocationListener: injected! $listener")
            }

            val listenerKey = Uuid.random().toHexString()
            if (listener is IInterface) {
                val binder = listener.asBinder()
                binder.linkToDeath({
                    removeLocationListener(listenerKey)
                }, 0)
            }
            locationListeners[listenerKey] = provider to listener
            hookILocationListener(listener)

            if (FakeLoc.disableRegisterLocationListener) {
                result = null
                return@beforeHook
            }

            if(FakeLoc.enable && !BinderUtils.isSystemAppsCall() && provider == "network") {
                result = null
                return@beforeHook
            }

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = null
                return@beforeHook
            }
//                    val updateInterval = kotlin.runCatching {
//                        val currentInterval = XposedHelpers.getLongField(request, "mInterval")
//                        if (currentInterval > FakeLocationConfig.updateInterval) {
//                            FakeLocationConfig.updateInterval
//                        } else {
//                            currentInterval
//                        }
//                    }.getOrElse {
//                        runCatching {
//                            XposedHelpers.callMethod(request, "getInterval") as Long
//                        }.getOrElse {
//                            FakeLocationConfig.updateInterval
//                        }
//                    }
//                    val updateFastestInterval = kotlin.runCatching {
//                        val currentInterval = XposedHelpers.getLongField(request, "mFastestInterval")
//                        if (currentInterval > FakeLocationConfig.updateInterval) {
//                            FakeLocationConfig.updateInterval
//                        } else {
//                            currentInterval
//                        }
//                    }.getOrElse {
//                        kotlin.runCatching {
//                            XposedHelpers.callMethod(request, "getFastestInterval") as Long
//                        }.getOrElse {
//                            (updateInterval / 6.0).toLong()
//                        }
//                    }

//                    kotlin.runCatching {
//                        XposedHelpers.callMethod(thisObject, "setIntervalMillis", updateInterval)
//                    }.onFailure {
//                        XposedBridge.log("[Portal] change locationRequest failed: ${it.stackTraceToString()}")
//                    }
        })
        cILocationManager.hookAllMethods("unregisterLocationListener", afterHook {

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

//        // Does not involve satellite information falsification (illegal)
//        if(XposedBridge.hookAllMethods(cILocationManager, "registerGnssStatusCallback", object: XC_MethodHook() {
//                override fun afterHookedMethod(param: MethodHookParam?) {
//                    if(hasHookRegisterGnssStatusCallback.get() || param == null || param.args.isEmpty() || param.args[0] == null) return
//
//                    val callback = param.args[0] ?: return
//                    val cIGnssStatusListener = callback.javaClass
//
//                    //val cGnssStatus by lazy { XposedHelpers.findClass("android.location.GnssStatus", cIGnssStatusListener.classLoader) }
//                    if(XposedBridge.hookAllMethods(cIGnssStatusListener, "onSvStatusChanged", object: XC_MethodHook() {
//                        override fun beforeHookedMethod(param: MethodHookParam?) {
//                            if (param == null) return
//                            XposedBridge.log("[Portal] onSvStatusChanged")
//                            // android 7.0.0
//                            // void onSvStatusChanged(int svCount, in int[] svidWithFlags, in float[] cn0s,
//                            //            in float[] elevations, in float[] azimuths);
//                            // android 8.0.0
//                            // void onSvStatusChanged(int svCount, in int[] svidWithFlags, in float[] cn0s,
//                            //            in float[] elevations, in float[] azimuths,
//                            //            in float[] carrierFreqs);
//                            // android 11
//                            //  void onSvStatusChanged(int svCount, in int[] svidWithFlags, in float[] cn0s,
//                            //            in float[] elevations, in float[] azimuths,
//                            //            in float[] carrierFreqs, in float[] basebandCn0s);
//                            // android 12 ~ 15
//                            // void onSvStatusChanged(in GnssStatus gnssStatus);
//                            if (param.args[0] is Int) {
//                                val svCount = param.args[0] as Int
//                                val svidWithFlags = param.args[1] as IntArray
//                                val cn0s = param.args[2] as FloatArray
//                                val elevations = param.args[3] as FloatArray
//                                val azimuths = param.args[4] as FloatArray
//
//                            } else {
//                                val gnssStatus = param.args[0]
//
//                            }
//                        }
//                    }).isEmpty()) {
//                        XposedBridge.log("[Portal] hook onSvStatusChanged failed")
//                    }
//
//                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
//                        if(XposedBridge.hookAllMethods(cIGnssStatusListener, "onNmeaReceived", object: XC_MethodHook() {
//                            override fun beforeHookedMethod(param: MethodHookParam?) {
//                                if (param == null) return
//                                XposedBridge.log("[Portal] onNmeaReceived")
//
//                            }
//                        }).isEmpty()) {
//                            XposedBridge.log("[Portal] hook onNmeaReceived failed")
//                        }
//                    }
//
//                    hasHookRegisterGnssStatusCallback.set(true)
//                }
//        }).isEmpty()) {
//            XposedBridge.log("[Portal] hook registerGnssStatusCallback failed")
//        }

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

                val listener = args[1] ?: return@beforeHook

                val listenerKey = Uuid.random().toHexString()
                if (listener is IInterface) {
                    val binder = listener.asBinder()
                    binder.linkToDeath({
                        removeLocationListener(listenerKey)
                    }, 0)
                }
                locationListeners[listenerKey] = "gps" to listener

                if (FakeLoc.disableRegisterLocationListener) {
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
        })

        //  void requestListenerFlush(String provider, in ILocationListener listener, int requestCode);
        cILocationManager.hookAllMethods("requestListenerFlush", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                if(FakeLoc.enableDebugLog) {
                    Logger.debug("requestListenerFlush: injected!")
                }

                val listener = args[1] ?: return@beforeHook
                val listenerKey = Uuid.random().toHexString()
                if (listener is IInterface) {
                    val binder = listener.asBinder()
                    binder.linkToDeath({
                        removeLocationListener(listenerKey)
                    }, 0)
                }
                locationListeners[listenerKey] = "gps" to listener

                if (FakeLoc.disableRegisterLocationListener) {
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
                result = listOf("gps", "passive")
            }

            if(FakeLoc.enableDebugLog) {
                Logger.debug("getAllProviders: ${(result as List<*>).joinToString(",")}!")
            }
        })

        cILocationManager.hookAllMethods("getProviders", afterHook {
            if(FakeLoc.enable) {
                result = listOf("gps", "passive")
            }

            if(FakeLoc.enableDebugLog) {
                Logger.debug("getProviders: ${(result as List<*>).joinToString(",")}!")
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

    private fun startDaemon(classLoader: ClassLoader) {
        val cIRemoteCallback = XposedHelpers.findClass("android.os.IRemoteCallback", classLoader)
        thread(
            name = "LocationUpdater",
            isDaemon = true,
            start = true,
        ) {
            while (true) {
                kotlin.runCatching {
                    if (!FakeLoc.enable) {
                        Thread.sleep(3000)
                        return@runCatching
                    } else {
                        Thread.sleep(FakeLoc.updateInterval)
                    }

                    if (!FakeLoc.enable) return@runCatching // Prevent the last loop from being executed

                    if (FakeLoc.enableDebugLog)
                        Logger.debug("LocationUpdater: callOnLocationChanged: ${locationListeners.size}")
                    locationListeners.forEach { (_, listenerWithProvider) ->
                        val listener = listenerWithProvider.second
                        var location = FakeLoc.lastLocation
                        if (location == null) {
                            location = Location(listenerWithProvider.first)
                        } else {
                            location.provider = listenerWithProvider.first
                        }
                        location = injectLocation(location)
                        kotlin.runCatching {
                            val locations = listOf(location)
                            val mOnLocationChanged = XposedHelpers.findMethodBestMatch(listener.javaClass, "onLocationChanged", locations, null)
                            XposedBridge.invokeOriginalMethod(mOnLocationChanged, listener, arrayOf(locations, null))
                            false
                        }.onFailure {
                            if (it is InvocationTargetException && it.targetException is DeadObjectException) {
                                return@onFailure
                            }
                            if (it !is DeadObjectException) {
                                Logger.error("LocationUpdater", it)
                            }
                        }
                    }
                }.onFailure {
                    Logger.error("LocationUpdater", it)
                }
            }
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
                        if (param == null) return

                        val thisObject = param.thisObject
                        val code = param.args[0] as Int
//                val data = param.args[1] as Parcel
//                val reply = param.args[2] as Parcel
//                val flags = param.args[3] as Int

                        synchronized(this) {
                            if (!isHooked.get()) {
                                onService(thisObject.javaClass)
                                isHooked.set(true)
                            }
                        }

                        if (FakeLoc.enable && code == 43) {
                            param.result = true
                        }

//                if (FakeLocationConfig.DEBUG && code == 59) {
//                    val data = param.args[1] as Parcel
//                    val provider = data.readString()
//                    val command = data.readString()
//                    val extras = data.readTypedObject(Bundle.CREATOR)
//                    XposedBridge.log("[Portal] ILocationManager.Stub: onTransact(code=$code, provider=$provider, command=$command, extras=$extras)")
//                    param.result = true
//                }

                        if (FakeLoc.enableDebugLog) {
                            Logger.debug("ILocationManager.Stub: onTransact(code=$code)")
                        }

//                when(code) {
//                    TRANSACTION_getLastLocation -> handleGetLastLocation(thisObject, data, reply)
//                    else -> {
//                        XposedBridge.log("[Portal] ILocationManager.Stub: code = $code")
//                    }
//                }
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

    internal fun removeLocationListener(key: String) {
        locationListeners.remove(key)
    }

//    // This method is not used because it is not guaranteed that each device processes the data stream consistently
//    private fun handleGetLastLocation(thisObject: Any, data: Parcel, reply: Parcel) = kotlin.runCatching { // Add a catch to prevent the whole thing from blowing up
//        // mService.getLastLocation(provider, lastLocationRequest,
//        //                    mContext.getPackageName(), mContext.getAttributionTag());
//        val provider = data.readString()
//        val lastLocationRequest = data.readTypedObject(LastLocationRequest.CREATOR)
//        val packageName = data.readString()
//        val attributionTag = data.readString()
//
//        val location = XposedHelpers.callMethod(thisObject,
//            "getLastLocation",
//            provider, lastLocationRequest, packageName, attributionTag
//        ) as? Location
//        if(location == null || !FqlUtils.isEnableHookApp()) {
//            reply.writeNoException()
//            reply.writeTypedObject(location, TRANSACTION_getLastLocation)
//        }
//    }

    private inline fun handleInstruction(command: String, rely: Bundle): Boolean {
        return RemoteCommandHandler.handleInstruction(command, rely, locationListeners)
    }
}