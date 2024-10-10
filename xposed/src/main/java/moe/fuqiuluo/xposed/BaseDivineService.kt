package moe.fuqiuluo.xposed

import android.content.Context
import android.location.LocationManager
import android.os.Binder
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
}