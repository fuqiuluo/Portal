package moe.fuqiuluo.portal.android.rom

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object RomUtils {
    fun isMiui(): Boolean {
        return !getProp("ro.miui.ui.version.name").isNullOrBlank()
    }

    fun isEmui(): Boolean {
        return getProp("ro.build.version.emui") != null
    }

    private fun getProp(name: String): String? {
        return try {
            val p = Runtime.getRuntime().exec("getprop $name")
            val `in` = BufferedReader(InputStreamReader(p.inputStream))
            val value = `in`.readLine()
            `in`.close()
            value
        } catch (e: IOException) {
            null
        }
    }
}