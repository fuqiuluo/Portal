package moe.fuqiuluo.portal.android.widget

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SatelliteData(
    val prn: Int,
    val snr: Float,
    val elevation: Float,
    val azimuth: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    var screenX: Float = 0f,
    var screenY: Float = 0f
)

class SatelliteRadarView(context: Context, attributeSet: AttributeSet): View(context, attributeSet), SensorEventListener {
    private var satellites = emptyList<SatelliteData>()
    private val radarPoint = Paint()
    private val satellitePaint = Paint()
    private var centerX: Int = 0
    private var centerY: Int = 0
    private var radius: Float = 0f

    // 磁场传感器
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var magneticSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null

    // 缓存的传感器数据
    private var rotationVector = FloatArray(3)
    private var accelerometerValues = FloatArray(3)
    private var magneticFieldValues = FloatArray(3)

    // 当前设备朝向角度（方位角）
    private var currentAzimuth: Float = 0f

    // 是否使用设备朝向
    private var useDeviceOrientation: Boolean = false

    init {
        initSensors(context)

        radarPoint.style = Paint.Style.STROKE
        radarPoint.color = Color.BLACK

        satellitePaint.style = Paint.Style.FILL
        satellitePaint.color = 0xFF2196F3.toInt() // 蓝色
    }

    private fun initSensors(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 尝试使用旋转矢量传感器（更准确）
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // 如果没有旋转矢量传感器，使用加速度和磁场传感器的组合
        if (rotationSensor == null) {
            accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerSensors()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSensors()
    }

    private fun registerSensors() {
        if (rotationSensor != null) {
            sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            if (accelerometerSensor != null) {
                sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (magneticSensor != null) {
                sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2
        centerY = h / 2
        radius = w.coerceAtMost(h) * 0.4f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        val size = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(widthSize, (200 * resources.displayMetrics.density).toInt())
            else -> (200 * resources.displayMetrics.density).toInt() // 默认200dp
        }

        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制雷达圆环 - 不随设备方向变化
        radarPoint.style = Paint.Style.STROKE
        for (i in 1..3) {
            val circleRadius = radius * i / 3
            canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), circleRadius, radarPoint)
        }

        // 绘制雷达线 - 不随设备方向变化
        for (angle in 0 until 360 step 90) {
            val x = centerX + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centerY + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
            canvas.drawLine(centerX.toFloat(), centerY.toFloat(), x, y, radarPoint)
        }

        // 绘制卫星点 - 根据设备朝向调整
        for (satellite in satellites) {
            // 根据设备朝向调整卫星方位角
            val adjustedAzimuth = if (useDeviceOrientation) {
                // 这里是关键 - 从卫星的方位角中减去设备的方位角
                val angle = (satellite.azimuth - currentAzimuth) % 360
                if (angle < 0) angle + 360 else angle
            } else {
                satellite.azimuth
            }

            val satelliteRadius = radius * (1 - satellite.elevation / 90f)
            val x = centerX + satelliteRadius * sin(Math.toRadians(adjustedAzimuth.toDouble())).toFloat()
            val y = centerY - satelliteRadius * cos(Math.toRadians(adjustedAzimuth.toDouble())).toFloat()
            satellite.screenX = x
            satellite.screenY = y

            // 设置卫星点大小和颜色
            val pointSize = 5f + (satellite.snr / 20f).coerceAtMost(10f)

            // 根据是否用于定位改变颜色
            if (satellite.usedInFix) {
                satellitePaint.color = 0xFF4CAF50.toInt() // 绿色表示用于定位
            } else {
                satellitePaint.color = 0xFF2196F3.toInt() // 蓝色表示未用于定位
            }

            canvas.drawCircle(x, y, pointSize, satellitePaint)

            // 画卫星PRN编号
            // 注释掉的代码可以取消注释以显示卫星编号
            // val textWidth = satellitePaint.measureText(satellite.prn.toString())
            // val textX = x - textWidth / 2
            // canvas.drawText(satellite.prn.toString(), textX, y + 20, satellitePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)

        val touchX = event.x
        val touchY = event.y

        setUseDeviceOrientation(!useDeviceOrientation)

        for (satellite in satellites) {
            if (distance(touchX, touchY, satellite.screenX, satellite.screenY) < 20) {
                Log.d("SatelliteRadarView", "Touched satellite $satellite")
                return true
            }
        }

        return false
    }

    private fun distance(x: Float, y: Float, x1: Float, y1: Float): Double {
        return sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1).toDouble())
    }

    fun setSatellites(satellites: List<SatelliteData>) {
        this.satellites = satellites
        invalidate()
    }

    // 设置是否使用设备朝向
    fun setUseDeviceOrientation(use: Boolean) {
        this.useDeviceOrientation = use
        invalidate()
    }

    // SensorEventListener接口实现
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // 使用旋转矢量传感器
                rotationVector = event.values.clone()
                updateOrientation()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // 使用加速度传感器
                accelerometerValues = event.values.clone()
                updateOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // 使用磁场传感器
                magneticFieldValues = event.values.clone()
                updateOrientation()
            }
        }
    }

    private fun updateOrientation() {
        if (rotationSensor != null) {
            // 使用旋转矢量计算方位角
            val rotationMatrix = FloatArray(9)
            val orientationValues = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            SensorManager.getOrientation(rotationMatrix, orientationValues)

            // 将弧度转换为角度并调整为0-360度范围
            val azimuthInRadians = orientationValues[0]
            var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            if (azimuthInDegrees < 0) {
                azimuthInDegrees += 360f
            }

            // 更新当前方位角
            if (Math.abs(azimuthInDegrees - currentAzimuth) > 1) {
                currentAzimuth = azimuthInDegrees
                invalidate()
            }
        } else if (accelerometerSensor != null && magneticSensor != null) {
            // 使用加速度和磁场传感器计算方位角
            val rotationMatrix = FloatArray(9)
            val orientationValues = FloatArray(3)

            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerValues,
                magneticFieldValues
            )

            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                // 将弧度转换为角度并调整为0-360度范围
                val azimuthInRadians = orientationValues[0]
                var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                if (azimuthInDegrees < 0) {
                    azimuthInDegrees += 360f
                }

                // 更新当前方位角（添加一定阈值避免频繁刷新）
                if (abs(azimuthInDegrees - currentAzimuth) > 1) {
                    currentAzimuth = azimuthInDegrees
                    invalidate()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 可以根据传感器精度变化采取相应措施
    }
}