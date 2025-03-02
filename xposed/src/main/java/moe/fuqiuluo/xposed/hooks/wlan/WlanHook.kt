@file:Suppress("UNCHECKED_CAST", "PrivateApi")
package moe.fuqiuluo.xposed.hooks.wlan

import android.net.wifi.WifiInfo
import android.os.Build
import android.util.ArrayMap
import dalvik.system.PathClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.afterHook
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.hookMethodAfter
import moe.fuqiuluo.xposed.utils.toClass

object WlanHook {
    operator fun invoke(classLoader: ClassLoader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cSystemServerClassLoaderFactory = XposedHelpers.findClassIfExists("com.android.internal.os.SystemServerClassLoaderFactory", classLoader)
            if (cSystemServerClassLoaderFactory == null) {
                Logger.warn("Failed to find SystemServerClassLoaderFactory")
                return
            }
            val sLoadedPaths = XposedHelpers.getStaticObjectField(cSystemServerClassLoaderFactory, "sLoadedPaths") as ArrayMap<String, PathClassLoader>
            val wifiClassLoader = sLoadedPaths.firstNotNullOfOrNull {
                if (it.key.contains("service-wifi.jar")) it.value else null
            }
            if (wifiClassLoader == null) {
                Logger.warn("Failed to find wifiClassLoader")
                return
            }
            val wifiClazz = "com.android.server.wifi.WifiServiceImpl".toClass(wifiClassLoader)
            if (wifiClazz == null) {
                Logger.warn("Failed to find WifiServiceImpl class")
                return
            }
            hookWifiServiceImpl(wifiClazz)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cSystemServiceManager = XposedHelpers.findClassIfExists("com.android.server.SystemServiceManager", classLoader)
            if (cSystemServiceManager == null) {
                Logger.warn("Failed to find SystemServiceManager")
                return
            }
            cSystemServiceManager.hookAllMethods("loadClassFromLoader", afterHook {
                if (args[0] == "com.android.server.wifi.WifiService") {
                    kotlin.runCatching {
                        val classloader = args[1] as PathClassLoader
                        val wifiClazz = classloader.loadClass("com.android.server.wifi.WifiServiceImpl")
                        hookWifiServiceImpl(wifiClazz)
                    }.onFailure {
                        Logger.error("Failed to hook WifiService", it)
                    }
                }
            })
        }
    }

    private fun hookWifiServiceImpl(wifiClazz: Class<*>) {
        if (!FakeLoc.hookWifi) return

        wifiClazz.hookAllMethods("getConnectionInfo", beforeHook {
            val packageName = args[0] as String
            if (FakeLoc.enableDebugLog)
                Logger.debug("In getConnectionInfo with caller: $packageName, state: ${FakeLoc.enable}")

            if (FakeLoc.enable && !BinderUtils.isSystemPackages(packageName)) {
                val wifiInfo = WifiInfo::class.java.getConstructor().newInstance()
                XposedHelpers.callMethod(wifiInfo, "setMacAddress", "02:00:00:00:00:00")
                XposedHelpers.callMethod(wifiInfo, "setBSSID", "02:00:00:00:00:00")
                result = wifiInfo
            }
        })

        wifiClazz.hookAllMethods("getScanResults", afterHook {
            val packageName = args[0] as? String
            if (packageName.isNullOrEmpty()) {
                return@afterHook
            }

            if (FakeLoc.enableDebugLog)
                Logger.debug("In getScanResults with caller: $packageName, state: ${FakeLoc.enable}")

            if(FakeLoc.enable) {
                if (result is List<*>) {
                    result = arrayListOf<Any>()
                    return@afterHook
                } // 针对小米系列机型的wifi扫描返回

                if (result is Array<*>) {
                    result = arrayOf<Any>()
                    return@afterHook
                } // 针对一加系列机型的wifi扫描返回


                // 在高于安卓10的版本，Google 引入了 APEX（Android Pony EXpress）文件格式来封装系统组件，包括系统服务~！
                // 上面的代码在高版本将无效导致应用可以通过网络AGPS到正常的位置（现象就是位置拉回）
                // 这里针对一个普通的版本进行一个修复

                val resultClass = result.javaClass
                if (resultClass.name.contains("ParceledListSlice")) runCatching {
                    val constructor = resultClass.getConstructor(List::class.java)
                    if (!constructor.isAccessible) {
                        constructor.isAccessible = true
                    }
                    result = constructor.newInstance(emptyList<Any>())
                    return@afterHook
                }.onFailure {
                    Logger.error("getScanResults: ParceledListSlice failed", it)
                }

                if (FakeLoc.enableDebugLog) {
                    Logger.error("getScanResults: Unknown return type: ${result?.javaClass?.name}")
                }
            }
        })
    }
}