package moe.fuqiuluo.xposed.hooks.nmea

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import moe.fuqiuluo.xposed.utils.onceHookBefore
import moe.fuqiuluo.xposed.utils.onceHookMethodBefore
import java.util.Collections

object LocationNMEAHook: BaseLocationHook() {
    operator fun invoke(classILocationManager: Class<*>) {
        hookGnssNmea(classILocationManager)

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

        XposedBridge.hookAllMethods(classILocationManager, "addGnssMeasurementsListener", doNothingMethod)
        XposedBridge.hookAllMethods(classILocationManager, "removeGnssMeasurementsListener", doNothingMethod)
        XposedBridge.hookAllMethods(classILocationManager, "addGnssNavigationMessageListener", doNothingMethod)
        XposedBridge.hookAllMethods(classILocationManager, "removeGnssNavigationMessageListener", doNothingMethod)
        XposedBridge.hookAllMethods(classILocationManager, "addGnssAntennaInfoListener", doNothingMethod)
        XposedBridge.hookAllMethods(classILocationManager, "removeGnssAntennaInfoListener", doNothingMethod)
    }

    private fun hookGnssNmea(classILocationManager: Class<*>) {
        val hookedGnssCallback = Collections.synchronizedSet(HashSet<String>())
        val unhooks = classILocationManager.declaredMethods.filter {
            it.name == "registerGnssNmeaCallback" && it.parameterTypes.size > 1
        }.map { method ->
            method.onceHookBefore {
                val cIGnssNmeaCallback = (args[0] ?: return@onceHookBefore).javaClass

                if (hookedGnssCallback.contains(cIGnssNmeaCallback.name)) return@onceHookBefore
                hookedGnssCallback.add(cIGnssNmeaCallback.name)

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("registerGnssNmeaCallback: $cIGnssNmeaCallback")
                }

                cIGnssNmeaCallback.onceHookMethodBefore("onNmeaReceived", Long::class.java, String::class.java) {
                    if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                        result = null // disable
                        return@onceHookMethodBefore
                    }

                    val nmea = args[1] as? String ?: return@onceHookMethodBefore
                    args[1] = injectNMEA(nmea) ?: nmea
                }
            }
        }

        if (FakeLoc.enableDebugLog) {
            Logger.debug("found ${unhooks.size} registerGnssNmeaCallback")
        }
    }
}