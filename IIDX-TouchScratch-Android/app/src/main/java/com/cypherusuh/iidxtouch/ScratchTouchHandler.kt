package com.cypherusuh.iidxtouch

import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt
import android.util.Log
class ScratchTouchHandler(
    private val onSample: (ScratchSample) -> Unit,
    private val onRelease: (Long) -> Unit
) {
    companion object {
        private const val TAG = "TouchScratch"
        private const val MIN_RADIUS_RATIO = 0.05f
    }

    private data class TouchPoint(
        var angle: Float,
        var radius: Float,
        var valid: Boolean
    )

    private val activePointers = mutableMapOf<Int, TouchPoint>()

    private var touchWidth = 0f
    private var touchHeight = 0f


    fun updateSize(width: Int, height: Int) {
        touchWidth = width.toFloat()
        touchHeight = height.toFloat()
    }

    fun handleTouchEvent(event: MotionEvent) {
        Log.d(
            TAG,
            "Touch event: action=${event.actionMasked}, " +
                    "pointers=${event.pointerCount}, " +
                    "size=${touchWidth}x${touchHeight}"
        )
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Recover from any stale pointers left by an interrupted gesture.
                if (activePointers.isNotEmpty()) {
                    release(event.eventTime)
                }

                activePointers.clear()
                syncPointers(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                processSample(event)
                syncPointers(event)
            }

            MotionEvent.ACTION_MOVE -> {
                // Process historical samples to preserve fast movement.
                for (historyIndex in 0 until event.historySize) {
                    processSample(event, historyIndex)
                }

                processSample(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                processSample(event)

                val liftedId = event.getPointerId(event.actionIndex)
                activePointers.remove(liftedId)

                syncPointers(event, event.actionIndex)

                if (activePointers.isEmpty()) {
                    release(event.eventTime)
                }
            }

            MotionEvent.ACTION_UP -> {
                processSample(event)
                release(event.eventTime)
            }

            MotionEvent.ACTION_CANCEL -> {
                release(event.eventTime)
            }
        }
    }

    private fun release(timestamp: Long) {
        // Always notify the backend, even if the final pointer
        // was already removed by ACTION_POINTER_UP.
        Log.d(TAG, "Touch released: timestamp=$timestamp")
        activePointers.clear()
        onRelease(timestamp)
    }

    private fun processSample(
        event: MotionEvent,
        historicalPosition: Int = -1
    ) {
        val platterRadius = minOf(touchWidth, touchHeight) / 2f
        if (platterRadius <= 0f) return

        val centerX = touchWidth / 2f
        val centerY = touchHeight / 2f

        var totalDeltaAngle = 0f
        var totalRadius = 0f
        var firstTouchAngle: Float? = null
        var validFingerCount = 0

        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val previous = activePointers[pointerId] ?: continue

            val x = if (historicalPosition >= 0) {
                event.getHistoricalX(i, historicalPosition)
            } else {
                event.getX(i)
            }

            val y = if (historicalPosition >= 0) {
                event.getHistoricalY(i, historicalPosition)
            } else {
                event.getY(i)
            }

            val dx = x - centerX
            val dy = y - centerY

            val distance = sqrt(dx * dx + dy * dy)
            val radius = distance / platterRadius
            val angle = atan2(dy, dx)

            if (radius < MIN_RADIUS_RATIO) {
                previous.valid = false
                previous.radius = radius
                continue
            }

            if (previous.valid) {
                var deltaAngle = angle - previous.angle

                if (deltaAngle > PI) {
                    deltaAngle -= 2f * PI.toFloat()
                } else if (deltaAngle < -PI) {
                    deltaAngle += 2f * PI.toFloat()
                }

                totalDeltaAngle += deltaAngle
                totalRadius += radius
                validFingerCount++

                if (firstTouchAngle == null) {
                    firstTouchAngle = angle
                }
            }

            previous.angle = angle
            previous.radius = radius
            previous.valid = true
        }

        if (validFingerCount == 0) return

        val timestamp = if (historicalPosition >= 0) {
            event.getHistoricalEventTime(historicalPosition)
        } else {
            event.eventTime
        }
        Log.d(
            TAG,
            "Touch sample: delta=${totalDeltaAngle / validFingerCount}, " +
                    "radius=${totalRadius / validFingerCount}, " +
                    "fingers=$validFingerCount"
        )
        onSample(
            ScratchSample(
                deltaAngle = totalDeltaAngle / validFingerCount,
                averageRadius = totalRadius / validFingerCount,
                touchAngle = firstTouchAngle ?: 0f,
                timestamp = timestamp,
                fingerCount = validFingerCount
            )
        )
    }

    private fun syncPointers(
        event: MotionEvent,
        excludedIndex: Int = -1
    ) {
        val platterRadius = minOf(touchWidth, touchHeight) / 2f
        if (platterRadius <= 0f) return

        val centerX = touchWidth / 2f
        val centerY = touchHeight / 2f

        for (i in 0 until event.pointerCount) {
            if (i == excludedIndex) continue

            val pointerId = event.getPointerId(i)

            val dx = event.getX(i) - centerX
            val dy = event.getY(i) - centerY

            val distance = sqrt(dx * dx + dy * dy)
            val radius = distance / platterRadius
            val angle = atan2(dy, dx)

            activePointers[pointerId] = TouchPoint(
                angle = angle,
                radius = radius,
                valid = radius >= MIN_RADIUS_RATIO
            )
        }
    }
}