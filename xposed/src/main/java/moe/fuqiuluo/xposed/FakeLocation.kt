@file:Suppress("LocalVariableName", "PrivateApi", "UNCHECKED_CAST")
package moe.fuqiuluo.xposed

import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.fuqiuluo.xposed.hooks.LocationManagerHook
import moe.fuqiuluo.xposed.hooks.LocationServiceHook
import moe.fuqiuluo.xposed.hooks.fused.AndroidFusedLocationProviderHook
import moe.fuqiuluo.xposed.hooks.fused.miui.MiFusedLocationHook
import moe.fuqiuluo.xposed.hooks.telephony.miui.MiuiTelephonyManagerHook
import moe.fuqiuluo.xposed.hooks.sensor.SystemSensorManagerHook
import moe.fuqiuluo.xposed.hooks.telephony.TelephonyHook
import moe.fuqiuluo.xposed.hooks.wlan.WlanHook
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.hookMethodAfter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class FakeLocation: IXposedHookLoadPackage, IXposedHookZygoteInit {
    private lateinit var cServiceManager: Class<*> // android.os.ServiceManager
    private val mServiceManagerCache by lazy {
        kotlin.runCatching { cServiceManager.getDeclaredField("sCache") }.onSuccess {
            it.isAccessible = true
        }.getOrNull()
        // the field is not guaranteed to exist
    }

    /**
     * Called very early during startup of Zygote.
     * @param startupParam Details about the module itself and the started process.
     * @throws Throwable everything is caught, but will prevent further initialization of the module.
     */
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        if(startupParam == null) return

//        // 宇宙安全声明：以下代码仅供学习交流使用，切勿用于非法用途?
//        System.setProperty("portal.enable", "true")
    }

    /**
     * This method is called when an app is loaded. It's called very early, even before
     * [Application.onCreate] is called.
     * Modules can set up their app-specific hooks here.
     *
     * @param lpparam Information about the app.
     * @throws Throwable Everything the callback throws is caught and logged.
     */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam?.packageName != "android" && lpparam?.packageName != "com.android.phone") {
            return
        }

        val systemClassLoader = (kotlin.runCatching {
            lpparam.classLoader.loadClass("android.app.ActivityThread")
                ?: Class.forName("android.app.ActivityThread")
        }.onFailure {
            Logger.error("Failed to find ActivityThread", it)
        }.getOrNull() ?: return)
            .getMethod("currentActivityThread")
            .invoke(null)
            .javaClass
            .getClassLoader()

        if (systemClassLoader == null) {
            Logger.error("Failed to get system class loader")
            return
        }

        if(System.getProperty("portal.injected_${lpparam.packageName}") == "true") {
            return
        } else {
            System.setProperty("portal.injected_${lpparam.packageName}", "true")
        }

        when (lpparam.packageName) {
            "com.android.phone" -> {
                Logger.info("Found com.android.phone")
                TelephonyHook(lpparam.classLoader)
                MiuiTelephonyManagerHook(lpparam.classLoader)
            }
            "android" -> {
                Logger.info("Debug Log Status: ${FakeLoc.enableDebugLog}")
                startFakeLocHook(systemClassLoader)
                TelephonyHook.hookSubOnTransact(lpparam.classLoader)
                WlanHook(systemClassLoader)
                AndroidFusedLocationProviderHook(lpparam.classLoader)
                SystemSensorManagerHook(lpparam.classLoader)
            }
            "com.android.location.fused" -> {
                AndroidFusedLocationProviderHook(lpparam.classLoader)
            }
            "com.xiaomi.location.fused" -> {
                MiFusedLocationHook(lpparam.classLoader)
            }
        }
    }

    private fun startFakeLocHook(classLoader: ClassLoader) {
        cServiceManager = XposedHelpers.findClass("android.os.ServiceManager", classLoader)
        val cLocationManager =
            XposedHelpers.findClass("android.location.LocationManager", classLoader)

        XposedHelpers.findClassIfExists("com.android.server.TelephonyRegistry", classLoader)?.let {
            TelephonyHook.hookTelephonyRegistry(it)
        } // for MUMU emulator

        LocationServiceHook(classLoader)
        LocationManagerHook(cLocationManager)  // intrusive hooks
    }

//        kotlin.runCatching {
//            val hooks = mutableListOf<XC_MethodHook.Unhook>()
//            val hookGetService = object: XC_MethodHook() {
//                override fun beforeHookedMethod(param: MethodHookParam?) {
//                    if (param == null) return
//                    val name = param.args[0] as String
//                    val service = if(param.args.size >= 2) {
//                        param.args[1] as IBinder
//                    } else {
//                        (param.result as? IBinder) ?: return
//                    }
//
//                    kotlin.runCatching {
//                        val cLocationManager = XposedHelpers.findClass("android.location.LocationManager", service.javaClass.classLoader!!)
//                        LocationServiceHook(cLocationManager.classLoader!!)
//                        LocationManagerHook(cLocationManager)  // intrusive hooks
//                    }.onFailure {
//                        XposedBridge.log("[Portal] Failed to hook LocationManager: ${it.stackTraceToString()}")
//                    }
//
////                    // Some strange emulator services don't have a location name, so there's no restriction here
////                    if (name.contains("location", ignoreCase = true) && // 一加系统服务名大写
////                        name != "location_time_zone_manager" // AVOID FUCKING WRONG SERVICE TOO
////                    ) {
////                        XposedBridge.log("[Portal] Add service: $name -> $service")
////                    }
//
//                    for (hook in hooks) {
//                        hook.unhook()
//                    }
//                }
//            }
//            hooks.addAll(XposedBridge.hookAllMethods(cServiceManager, "rawGetService", hookGetService).toList())
//            hooks.addAll(XposedBridge.hookAllMethods(cServiceManager, "addService", hookGetService))
//            hooks.addAll(XposedBridge.hookAllMethods(cServiceManager, "getService", hookGetService))
//            hooks.addAll(XposedBridge.hookAllMethods(cServiceManager, "checkService", hookGetService))
//            hooks.addAll(XposedBridge.hookAllMethods(cServiceManager, "waitForService", hookGetService))
//        }.onSuccess {
//            return
//        }.onFailure {
//            XposedBridge.log("[Portal] Failed to hook ServiceManager.addService: ${it.stackTraceToString()}")
//        }
//
//        kotlin.runCatching { XposedHelpers.findClass("com.android.server.SystemServer", classLoader) }.onSuccess { cSystemServer ->
//            val mSystemServerRun = kotlin.runCatching { cSystemServer.getDeclaredMethod("run") }.getOrNull()
//            val mSystemServerStartOtherServices = kotlin.runCatching { cSystemServer.getDeclaredMethod("startOtherServices") }.getOrNull()
//
//            if (mSystemServerStartOtherServices == null && mSystemServerRun == null) {
//                XposedBridge.log("[Portal] Failed to find SystemServer.run or SystemServer.startOtherServices")
//                return@onSuccess
//            }
//
//            if (mSystemServerRun != null) {
//                XposedBridge.hookMethod(mSystemServerRun, object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam?) {
//                        systemServerInitOver()
//                    }
//                })
//            } else {
//                XposedBridge.hookMethod(mSystemServerStartOtherServices, object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam?) {
//                        systemServerInitOver()
//                    }
//                })
//            }
//        }.onFailure {
//            XposedBridge.log("[Portal] Failed to find SystemServer: ${it.stackTraceToString()}")
//        }

//    private fun systemServerInitOver() {
//        XposedBridge.log("[Portal] SystemServer init over")
//        // Debug: Print ServiceManager.sCache
//        val cCache = mServiceManagerCache?.get(null) as? Map<String, IBinder>
//        if (cCache == null) {
//            XposedBridge.log("[Portal] Failed to get ServiceManager.sCache")
//            return
//        }
//
//        cCache.forEach { (name, service) ->
//            XposedBridge.log("[Portal] Service: $name -> $service")
//        }
//
//        // todo：This method is too chicken, let's realize it in the next life~!
//        // append: wtf?
//        XposedBridge.log("[Portal] unsupported mode: systemServerInitOver")
//    }
}