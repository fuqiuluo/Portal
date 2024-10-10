package moe.fuqiuluo.xposed.hooks.miui

import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.hooks.blindhook.BlindHookLocation

object MiuiBlurLocationProviderHook: BaseLocationHook() {
    operator fun invoke(classLoader: ClassLoader) {
        val cMiuiBlurLocationManagerImpl = XposedHelpers.findClassIfExists("com.android.server.location.MiuiBlurLocationManagerImpl", classLoader)
        if (cMiuiBlurLocationManagerImpl != null) {
            BlindHookLocation(cMiuiBlurLocationManagerImpl)
        }

        val cGnssLocationProviderImpl = XposedHelpers.findClassIfExists("com.android.server.location.gnss.GnssLocationProviderImpl", classLoader)
        if (cGnssLocationProviderImpl != null) {
            BlindHookLocation(cGnssLocationProviderImpl)
        }
    }


}