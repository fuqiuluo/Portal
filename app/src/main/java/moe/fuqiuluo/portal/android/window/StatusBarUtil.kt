package moe.fuqiuluo.portal.android.window

import android.app.Activity
import android.graphics.Color
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt

object StatusBarUtil {
    fun transparentStatusBar(activity: Activity, isDark: Boolean) {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        activity.window.statusBarColor = Color.TRANSPARENT
    }

    fun fullScreen(activity: Activity) {
        activity.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    fun setStatusBarColor(activity: Activity, @ColorInt color: Int) {
        val window: Window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color
    }
}