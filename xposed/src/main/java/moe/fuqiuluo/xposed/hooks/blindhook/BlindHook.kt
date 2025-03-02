package moe.fuqiuluo.xposed.hooks.blindhook

import android.location.Location
import de.robv.android.xposed.XC_MethodHook
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.onceHook
import java.lang.reflect.Member

object BlindHook {
    operator fun <T> invoke(clazz: Class<*>, classLoader: ClassLoader, handler: (Member, T?) -> T?): Int {
        var count = 0
        clazz.declaredMethods.forEach {
            if (it.returnType == Location::class.java) {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("BlindHookV2 ${it.name}: ${it.parameterTypes.joinToString()}")
                }

                it.onceHook(BlindHookForRETLocation(false, false, handler))
                count++
                return@forEach
            }

            it.parameterTypes.forEachIndexed { index, type ->
                if (type == Location::class.java) {
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("BlindHookV2 ${it.name}: ${it.parameterTypes.joinToString()}")
                    }

                    it.onceHook(BlindHookForLocation(index, false, false, handler))
                    count++
                    return@forEach
                }
            }
        }
        return count
    }

    @Suppress("UNCHECKED_CAST")
    private class BlindHookForLocation<T>(
        val index: Int,
        val isList: Boolean,
        val isArray: Boolean,
        val handler: (Member, T?) -> T?
    ): XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val data = param.args[index] as? T ?: return

            param.args[index] = handler(param.method, data)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class BlindHookForRETLocation<T>(
        val isList: Boolean,
        val isArray: Boolean,
        val handler: (Member, T?) -> T?
    ): XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val data = param.result as? T ?: return

            param.result = handler(param.method, data)
        }
    }
}