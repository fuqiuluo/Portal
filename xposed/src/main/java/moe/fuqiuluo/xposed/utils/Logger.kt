package moe.fuqiuluo.xposed.utils

import de.robv.android.xposed.XposedBridge

object Logger {
    private fun isEnableLog(): Boolean {
        return FakeLoc.enable
    }

    fun info(msg: String) {
        if (isEnableLog()) {
            XposedBridge.log("[Portal] $msg")
        }
    }

    fun info(msg: String, throwable: Throwable) {
        if (isEnableLog()) {
            XposedBridge.log("[Portal] $msg: ${throwable.stackTraceToString()}")
        }
    }

    fun debug(msg: String) {
        XposedBridge.log("[Portal][DEBUG] $msg")
    }

    fun debug(msg: String, throwable: Throwable) {
        XposedBridge.log("[Portal][DEBUG] $msg: ${throwable.stackTraceToString()}")
    }

    fun error(msg: String) {
        XposedBridge.log("[Portal][ERROR] $msg")
    }

    fun error(msg: String, throwable: Throwable) {
        XposedBridge.log("[Portal][ERROR] $msg: ${throwable.stackTraceToString()}")
    }

    fun warn(msg: String) {
        XposedBridge.log("[Portal][WARN] $msg")
    }

    fun warn(msg: String, throwable: Throwable) {
        XposedBridge.log("[Portal][WARN] $msg: ${throwable.stackTraceToString()}")
    }
}