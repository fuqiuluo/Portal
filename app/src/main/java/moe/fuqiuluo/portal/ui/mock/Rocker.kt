package moe.fuqiuluo.portal.ui.mock

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.widget.RockerView
import moe.fuqiuluo.portal.ext.rockerCoords


@SuppressLint("RtlHardcoded", "ClickableViewAccessibility")
class Rocker(private val activity: Activity): View.OnTouchListener {
    private val root by lazy { LayoutInflater.from(activity).inflate(R.layout.layout_rocker, null)!! }
    private val layoutParams by lazy {
        WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            0, 0, PixelFormat.TRANSPARENT)
    }
    private val windowManager by lazy { activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var startX = 0
    private var startY = 0

    var isStart = false

    init {
        layoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE
        }

        val rockerCoords = activity.rockerCoords
        layoutParams.format = PixelFormat.RGBA_8888
        layoutParams.gravity = Gravity.LEFT or Gravity.TOP
        layoutParams.x = rockerCoords.first
        layoutParams.y = rockerCoords.second

        root.setOnTouchListener(this)
    }

    fun show() {
        windowManager.addView(root, layoutParams)
        isStart = true
    }

    fun hide() {
        val rockerView = root.findViewById<RockerView>(R.id.rocker)
        rockerView.reset()
        windowManager.removeView(root)
        isStart = false
    }

    fun savePosition() {
        activity.rockerCoords = Pair(layoutParams.x, layoutParams.y)
    }

    fun updateView() {
        windowManager.updateViewLayout(root, layoutParams)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v == null || event == null) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX.toInt()
                startY = event.rawY.toInt()

                savePosition()
            }

            MotionEvent.ACTION_MOVE -> {
                val nowX = event.rawX.toInt()
                val nowY = event.rawY.toInt()
                val movedX = nowX - startX
                val movedY = nowY - startY
                startX = nowX
                startY = nowY
                layoutParams.x += movedX
                layoutParams.y += movedY
                windowManager.updateViewLayout(v, layoutParams)
            }

            else -> {}
        }
        return false
    }
}