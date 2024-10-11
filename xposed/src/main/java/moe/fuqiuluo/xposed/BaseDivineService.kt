package moe.fuqiuluo.xposed

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger

abstract class BaseDivineService {
    /**
     * if the hook is TelephonyService? or other service?
     * this service may not be in the same space as the `system_server`,
     * so a binder is used to talk.
     */
    protected fun initDivineService(from: String, retryCount: Int = 0): Boolean {
        fun tryFetchLocationManager(): LocationManager? {
            var locationManager = BinderUtils.getSystemContext()?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            var count = 0
            while (locationManager == null) {
                Thread.sleep(1000)
                if (count++ > 10) {
                    break
                }
                locationManager = BinderUtils.getSystemContext()?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            }
            return locationManager
        }

        val locationManager = tryFetchLocationManager()
        if (locationManager == null) {
            Logger.error("LocationManager not found in $from")
            return false
        }

        if (!locationManager.isProviderEnabled("portal")) {
            if (retryCount > 10) {
                return false
            }
            Thread.sleep(100)
            return initDivineService(from, retryCount + 1)
        }

        var randomKey = ""
        val rely = Bundle()
        if(locationManager.sendExtraCommand("portal", "exchange_key", rely)) {
            rely.getString("key")?.let {
                randomKey = it
            }
        }

        if (randomKey.isEmpty()){
            Logger.error("Failed to init service in $from")
            return false
        }

        syncConfig(locationManager, randomKey)

        rely.putBinder("proxy", object: Binder() {
            override fun getInterfaceDescriptor(): String {
                return "moe.fuqiuluo.portal.service.${from}Helper"
            }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == 1) {
                    val bundle = data.readBundle(javaClass.classLoader)!!
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("ProxyBinder($from): $bundle")
                    }
                    if(!RemoteCommandHandler.handleInstruction(randomKey, bundle, emptyList())) {
                        Logger.error("Failed to handle instruction in $from")
                    }
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        })
        rely.putString("command_id", "set_proxy")
        if (!locationManager.sendExtraCommand("portal", randomKey, rely)) {
            Logger.error("Failed to init service proxy in $from")
            return false
        }
        return true
    }

    /**
     * Synchronize configurations in different processes
     */
    private fun syncConfig(locationManager: LocationManager, randomKey: String) {
        val rely = Bundle()
        rely.putString("command_id", "sync_config")
        if(locationManager.sendExtraCommand("portal", randomKey, rely)) {
            FakeLoc.enable = rely.getBoolean("enable", FakeLoc.enable)
            FakeLoc.latitude = rely.getDouble("latitude", FakeLoc.latitude)
            FakeLoc.longitude = rely.getDouble("longitude", FakeLoc.longitude)
            FakeLoc.altitude = rely.getDouble("altitude", FakeLoc.altitude)
            FakeLoc.speed = rely.getDouble("speed", FakeLoc.speed)
            FakeLoc.speedAmplitude = rely.getDouble("speed_amplitude", FakeLoc.speedAmplitude)
            FakeLoc.hasBearings = rely.getBoolean("has_bearings", FakeLoc.hasBearings)
            FakeLoc.bearing = rely.getDouble("bearing", FakeLoc.bearing)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                FakeLoc.lastLocation = rely.getParcelable("last_location", Location::class.java)
            } else {
                FakeLoc.lastLocation = rely.getParcelable("last_location")
            }
            FakeLoc.enableLog = rely.getBoolean("enable_log", FakeLoc.enableLog)
            FakeLoc.enableDebugLog = rely.getBoolean("enable_debug_log", FakeLoc.enableDebugLog)
            FakeLoc.disableGetCurrentLocation = rely.getBoolean("disable_get_current_location", FakeLoc.disableGetCurrentLocation)
            FakeLoc.disableRegisterLocationListener = rely.getBoolean("disable_register_location_listener", FakeLoc.disableRegisterLocationListener)
            FakeLoc.disableFusedLocation = rely.getBoolean("disable_fused_location", FakeLoc.disableFusedLocation)
            FakeLoc.enableAGPS = rely.getBoolean("enable_agps", FakeLoc.enableAGPS)
            FakeLoc.enableNMEA = rely.getBoolean("enable_nmea", FakeLoc.enableNMEA)
            FakeLoc.hideMock = rely.getBoolean("hide_mock", FakeLoc.hideMock)
            FakeLoc.autoRemoveUselessLocListener = rely.getBoolean("auto_remove_useless_loc_listener", FakeLoc.autoRemoveUselessLocListener)
            FakeLoc.hookWifi = rely.getBoolean("hook_wifi", FakeLoc.hookWifi)
            FakeLoc.needDowngradeTo2G = rely.getBoolean("need_downgrade_to_2g", FakeLoc.needDowngradeTo2G)
            FakeLoc.updateInterval = rely.getLong("update_interval", FakeLoc.updateInterval)
            Logger.debug("Synced config for DivineService")
        } else {
            Logger.error("Failed to sync config for DivineService")
        }
    }
}