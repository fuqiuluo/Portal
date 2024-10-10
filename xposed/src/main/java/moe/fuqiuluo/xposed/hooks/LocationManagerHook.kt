package moe.fuqiuluo.xposed.hooks

import android.location.Location
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import moe.fuqiuluo.xposed.utils.onceHookMethod

object LocationManagerHook: BaseLocationHook() {
    operator fun invoke(
        cLocationManager: Class<*>,
    ) {
        val hookGetLastKnownLocation = object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null || param.hasThrowable() || param.result == null) return

                if (!FakeLoc.enable) return

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("${param.method.name}: injected!")
                }

                param.result = injectLocation(param.result as Location)
            }
        }
        if(cLocationManager.declaredMethods.filter {
            it.name == "getLastKnownLocation" && it.parameterTypes.size > 1
        }.map {
            XposedBridge.hookMethod(it, hookGetLastKnownLocation)
        }.isEmpty()) {
            XposedBridge.hookAllMethods(cLocationManager, "getLastLocation", hookGetLastKnownLocation)
        }

        val hookOnLocation = object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.isEmpty() || param.args[0] == null) return

                if (!FakeLoc.enable) return

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("${param.method.name}: injected!")
                }

                when( param.args[0] ) {
                    is Location -> {
                        param.args[0] = injectLocation(param.args[0] as Location)
                    }
                    is List<*> -> {
                        val locations = param.args[0] as List<*>
                        param.args[0] = locations.map { injectLocation(it as Location) }
                    }
                }
            }
        }

        if(cLocationManager.declaredMethods.filter {
                it.name == "requestFlush"
            }.map {
                XposedBridge.hookMethod(it, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null || param.args.size > 1 || param.args[1] == null) return

                        val listener = param.args[1]

                        listener.javaClass.onceHookAllMethod("onLocationChanged", hookOnLocation)
                    }
                })
            }.isEmpty()) {
            Logger.error("Hook requestFlush failed")
        }

        val hookRequestLocationUpdates = object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null || param.args.isEmpty() || param.args[1] == null) return

                param.args.filterIsInstance<android.location.LocationListener>().forEach {
                    it.javaClass.onceHookAllMethod("onLocationChanged", hookOnLocation)
                }
            }
        }
        if(cLocationManager.declaredMethods.filter {
                it.name == "requestLocationUpdates"
            }.map {
                XposedBridge.hookMethod(it, hookRequestLocationUpdates)
            }.isEmpty()) {
            Logger.error("Hook requestLocationUpdates failed")
        }

        if(cLocationManager.declaredMethods.filter {
                it.name == "requestSingleUpdate"
            }.map {
                XposedBridge.hookMethod(it, hookRequestLocationUpdates)
            }.isEmpty()) {
            Logger.error("Hook requestSingleUpdate failed")
        }

        kotlin.runCatching {
            XposedHelpers.findClass("android.location.LocationManager\$GetCurrentLocationTransport", cLocationManager.classLoader)
        }.onSuccess {
            it.onceHookAllMethod("onLocation", hookOnLocation)
        }.onFailure {
            XposedBridge.log(it)
        }

        kotlin.runCatching {
            XposedHelpers.findClass("android.location.LocationManager\$BatchedLocationCallbackWrapper", cLocationManager.classLoader)
        }.onSuccess {
            it.onceHookAllMethod("onLocationChanged", hookOnLocation)
        }

        kotlin.runCatching {
            XposedHelpers.findClass("android.location.LocationManager\$LocationListenerTransport", cLocationManager.classLoader)
        }.onSuccess {
            it.onceHookAllMethod("onLocationChanged", hookOnLocation)
        }.onFailure {
            XposedBridge.log(it)
        }
    }
}