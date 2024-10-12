package moe.fuqiuluo.portal.android.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import moe.fuqiuluo.portal.R
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt


// https://github.com/lqxue/RockerView/blob/master/app/src/main/java/com/zcsj/rockerview/RockerView.java
class RockerView(context: Context, attributeSet: AttributeSet): View(context, attributeSet) {
    private var mAreaBackgroundPaint: Paint
    private var mRockerPaint: Paint
    private var mRockerPosition: Point
    private var directionPaint = Paint()
    private var mCenterPoint: Point
    private val trianglePaint = Paint()
    private val trianglePaint2 = Paint()

    private var mAreaRadius = 0.0f
    private var mRockerInnerCircleRadius = 0.0f
    private var mRockerColor = 0
    private var mAreaColor = 0
    var listener: OnMoveListener? = null

    init {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.RockerView)
        val areaBackground = typedArray.getDrawable(R.styleable.RockerView_areaBackground)
        if (null != areaBackground) {
            if (areaBackground is ColorDrawable) {
                mAreaColor = areaBackground.color
            } else {
                throw IllegalArgumentException("areaBackground only support ColorDrawable")
            }
        }
        val rockerBackground = typedArray.getDrawable(R.styleable.RockerView_rockerBackground)
        if (null != rockerBackground) {
            if (rockerBackground is ColorDrawable) {
                mRockerColor = rockerBackground.color
            } else {
                throw IllegalArgumentException("rockerBackground only support ColorDrawable")
            }
        }
        typedArray.recycle()

        mAreaBackgroundPaint = Paint()
        mAreaBackgroundPaint.isAntiAlias = true

        mRockerPaint = Paint()
        mRockerPaint.isAntiAlias = true
        mRockerPaint.color = mRockerColor

        mCenterPoint = Point()
        mRockerPosition = Point()

        val white = resources.getColor(R.color.white)
        directionPaint.textAlign = Paint.Align.CENTER
        directionPaint.color = white

        trianglePaint.color = resources.getColor(R.color.red500)
        trianglePaint.style = Paint.Style.FILL

        trianglePaint2.color = white
        trianglePaint2.style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getMySize(widthMeasureSpec)
        val height = getMySize(heightMeasureSpec)
        val min = width.coerceAtMost(height)
        setMeasuredDimension(min, min)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom
        val width = width - paddingLeft - paddingRight - DEFAULT_PADDING_SIZE
        val height = height - paddingBottom - paddingTop - DEFAULT_PADDING_SIZE
        val cx = width / 2 + paddingLeft + DEFAULT_PADDING_SIZE / 2
        val cy = height / 2 + paddingTop + DEFAULT_PADDING_SIZE / 2
        mCenterPoint[cx] = cy
        mAreaRadius = (min(width.toDouble(), height.toDouble()) / 2).toFloat()


        if (0 == mRockerPosition.x || 0 == mRockerPosition.y) {
            mRockerPosition[mCenterPoint.x] = mCenterPoint.y
        }

        mAreaBackgroundPaint.color = mAreaColor
        canvas.drawCircle(mCenterPoint.x.toFloat(), mCenterPoint.y.toFloat(), mAreaRadius, mAreaBackgroundPaint)

        drawDirection(canvas)

        mRockerInnerCircleRadius = mAreaRadius * 0.2f
        canvas.drawCircle(
            mRockerPosition.x.toFloat(),
            mRockerPosition.y.toFloat(), mRockerInnerCircleRadius, mRockerPaint
        )
    }

    private fun drawDirection(canvas: Canvas, circleRadius: Float = mAreaRadius, circleX: Float = mCenterPoint.x.toFloat(), circleY: Float = mCenterPoint.y.toFloat()) {
        val triangleBase = circleRadius / 8.0f
        directionPaint.textSize = circleRadius / 8.0f

        run {
            canvas.drawText("N", circleX, circleY - circleRadius * 0.56f, directionPaint)
            val trianglePath = Path()
            trianglePath.moveTo(circleX, circleY - circleRadius * 0.8f)
            trianglePath.lineTo(circleX - triangleBase / 2, circleY - circleRadius * 0.7f)
            trianglePath.lineTo(circleX + triangleBase / 2, circleY - circleRadius * 0.7f)
            trianglePath.close()
            canvas.drawPath(trianglePath, trianglePaint)
        }
        run {
            canvas.drawText("W", circleX - circleRadius * 0.56f, circleY + circleRadius * 0.05f, directionPaint)
            val trianglePath = Path()
            trianglePath.moveTo(circleX - circleRadius * 0.8f, circleY)
            trianglePath.lineTo(circleX - circleRadius * 0.7f, circleY + triangleBase / 2)
            trianglePath.lineTo(circleX - circleRadius * 0.7f, circleY - triangleBase / 2)
            trianglePath.close()
            canvas.drawPath(trianglePath, trianglePaint2)
        }
        run {
            canvas.drawText("E", circleX + circleRadius * 0.56f, circleY + circleRadius * 0.05f, directionPaint)
            val trianglePath = Path()
            trianglePath.moveTo(circleX + circleRadius * 0.8f, circleY)
            trianglePath.lineTo(circleX + circleRadius * 0.7f, circleY + triangleBase / 2)
            trianglePath.lineTo(circleX + circleRadius * 0.7f, circleY - triangleBase / 2)
            trianglePath.close()
            canvas.drawPath(trianglePath, trianglePaint2)
        }

        run {
            canvas.drawText("S", circleX, circleY + circleRadius * 0.6f, directionPaint)
            val trianglePath = Path()
            trianglePath.moveTo(circleX, circleY + circleRadius * 0.8f)
            trianglePath.lineTo(circleX - triangleBase / 2, circleY + circleRadius * 0.7f)
            trianglePath.lineTo(circleX + triangleBase / 2, circleY + circleRadius * 0.7f)
            trianglePath.close()
            canvas.drawPath(trianglePath, trianglePaint2)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                listener?.onStarted()

                val moveX = event.x
                val moveY = event.y

                mRockerPosition = getRockerPositionPoint(mCenterPoint, Point(moveX.toInt(), moveY.toInt()), mAreaRadius, mRockerInnerCircleRadius)
                moveRocker(mRockerPosition.x, mRockerPosition.y)
            }

            MotionEvent.ACTION_MOVE -> {
                val moveX = event.x
                val moveY = event.y
                mRockerPosition = getRockerPositionPoint(
                    mCenterPoint,
                    Point(moveX.toInt(), moveY.toInt()),
                    mAreaRadius,
                    mRockerInnerCircleRadius
                )
                moveRocker(mRockerPosition.x, mRockerPosition.y)
            }

            MotionEvent.ACTION_UP -> {
                listener?.onAngle(0.0)
                listener?.onFinished()
                moveRocker(mCenterPoint.x, mCenterPoint.y)
            }

            MotionEvent.ACTION_CANCEL -> {
                listener?.onFinished()
                moveRocker(mCenterPoint.x, mCenterPoint.y)
            }
        }
        return true
    }

    private fun moveRocker(x: Int, y: Int) {
        mRockerPosition[x] = y
        invalidate()
    }

    private fun getMySize(measureSpec: Int): Int {
        val result: Int
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        result = when (specMode) {
            MeasureSpec.EXACTLY -> specSize
            MeasureSpec.AT_MOST -> DEFAULT_SIZE.coerceAtMost(specSize)
            else -> DEFAULT_SIZE
        }
        return result
    }

    private fun getRockerPositionPoint(
        centerPoint: Point,
        touchPoint: Point,
        regionRadius: Float,
        rockerRadius: Float
    ): Point {
        val lenX = (touchPoint.x - centerPoint.x).toFloat()
        val lenY = (touchPoint.y - centerPoint.y).toFloat()
        val lenXY = sqrt((lenX * lenX + lenY * lenY).toDouble()).toFloat()
        val radian = acos((lenX / lenXY).toDouble()) * (if (touchPoint.y < centerPoint.y) -1 else 1)
        val tmp = Math.round(radian / Math.PI * 180).toDouble()
        val angle = if (tmp >= 0) tmp else 360 + tmp
        if (lenXY + rockerRadius <= regionRadius) {
            listener?.onAngle(angle)
            return touchPoint
        } else {
            val showPointX = (centerPoint.x + (regionRadius - rockerRadius) * cos(radian)).toInt()
            val showPointY = (centerPoint.y + (regionRadius - rockerRadius) * sin(radian)).toInt()
            listener?.onAngle(angle)
            return Point(showPointX, showPointY)
        }
    }

    companion object {
        const val DEFAULT_SIZE: Int = 500
        const val DEFAULT_PADDING_SIZE: Int = 200

        interface OnMoveListener {
            fun onStarted()

            fun onAngle(angle: Double)

            fun onFinished()
        }
    }
}