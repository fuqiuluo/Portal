package moe.fuqiuluo.xposed.hooks.gnss

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import java.util.Collections

object GnssManagerServiceHook: BaseLocationHook() {
    operator fun invoke(classLoader: ClassLoader) {
        val cGnssManagerService = XposedHelpers.findClassIfExists("com.android.server.location.GnssManagerService", classLoader) ?: return

        val doNothingMethod = object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null || param.args.isEmpty()) return

                if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("${param.method.name}: disable")
                    }
                    param.result = null
                }
            }
        }

        // AGPS Listener?
        XposedBridge.hookAllMethods(cGnssManagerService, "addGnssAntennaInfoListener", doNothingMethod)
        XposedBridge.hookAllMethods(cGnssManagerService, "addGnssMeasurementsListener", doNothingMethod)
        XposedBridge.hookAllMethods(cGnssManagerService, "addGnssNavigationMessageListener", doNothingMethod)
        XposedBridge.hookAllMethods(cGnssManagerService, "removeGnssAntennaInfoListener", doNothingMethod)
        XposedBridge.hookAllMethods(cGnssManagerService, "removeGnssMeasurementsListener", doNothingMethod)
        XposedBridge.hookAllMethods(cGnssManagerService, "removeGnssNavigationMessageListener", doNothingMethod)

        run {
            val hookedGnssCallback = Collections.synchronizedSet(HashSet<String>())
            val unhooks = cGnssManagerService.declaredMethods.filter {
                it.name == "registerGnssNmeaCallback" && it.parameterTypes.size > 1
            }.map { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null || param.args[0] == null) return
                        val classListener = param.args[0].javaClass

                        if (hookedGnssCallback.contains(classListener.name)) return
                        hookedGnssCallback.add(classListener.name)

                        if (FakeLoc.enableDebugLog)
                            Logger.debug("registerGnssNmeaCallback: $classListener")
                        kotlin.runCatching {
                            XposedHelpers.findAndHookMethod(classListener, "onNmeaReceived", Long::class.java, String::class.java, object: XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                                        if (FakeLoc.enableDebugLog)
                                            Logger.debug("GnssManagerService.onNmeaReceived: disable")
                                        param.result = null
                                        return
                                    }

                                    val nmea = param.args[1] as String
                                    param.args[1] = injectNMEA(nmea) ?: nmea
                                }
                            })
                        }.onFailure {
                            Logger.error("[onNmeaReceived hook failed: ${it.message} from GnssManagerService, please issue?", it)
                        }
                    }
                })
            }

            if (FakeLoc.enableDebugLog)
                Logger.debug("found ${unhooks.size} registerGnssNmeaCallback")
        }
    }
}