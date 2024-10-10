package moe.fuqiuluo.xposed.hooks.blindhook

import android.location.Location
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.onceHook
import java.lang.reflect.Member

object BlindHook {
    operator fun <T> invoke(clazz: Class<*>, handler: (Member, T?) -> T?): Int {
        var count = 0
        clazz.declaredMethods.forEach {
            if (it.returnType == Location::class.java) {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("BlindHookV2 ${it.name}: ${it.parameterTypes.joinToString()}")
                }

                it.onceHook(BlindHookForRETLocation(handler))
                count++
            } else if (it.parameterTypes.contains(Location::class.java)) {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("BlindHookV1 ${it.name}: ${it.parameterTypes.joinToString()}")
                }

                it.onceHook(BlindHookForLocation(it.parameterTypes.indexOf(Location::class.java), handler))
                count++
            }
        }
        return count
    }

    @Suppress("UNCHECKED_CAST")
    private class BlindHookForLocation<T>(
        private val index: Int,
        private val handler: (Member, T?) -> T?
    ): XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val data = param.args[index] as? T ?: return

            param.args[index] = handler(param.method, data)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class BlindHookForRETLocation<T>(
        private val handler: (Member, T?) -> T?
    ): XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val data = param.result as? T ?: return

            param.result = handler(param.method, data)
        }
    }
}