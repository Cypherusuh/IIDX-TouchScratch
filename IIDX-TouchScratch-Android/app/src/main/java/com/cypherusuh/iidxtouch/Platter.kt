
package com.cypherusuh.iidxtouch

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Platter(
    rotation: Float,
    touchAngle: Float,
    touchRadius: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f

        rotate(rotation, pivot = center) {
            drawCircle(
                color = Color(0xFF292929),
                radius = radius,
                center = center
            )

            drawCircle(
                color = Color(0xFF555555),
                radius = radius * 0.86f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = Color(0xFF444444),
                radius = radius * 0.55f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            for (index in 0 until 60) {
                val major = index % 5 == 0

                rotate(index * 6f, pivot = center) {
                    drawLine(
                        color = if (major) {
                            Color(0xFFAAAAAA)
                        } else {
                            Color(0xFF666666)
                        },
                        start = Offset(
                            center.x,
                            center.y - radius * 0.91f
                        ),
                        end = Offset(
                            center.x,
                            center.y - radius * if (major) 0.84f else 0.88f
                        ),
                        strokeWidth = if (major) {
                            3.dp.toPx()
                        } else {
                            1.dp.toPx()
                        }
                    )
                }
            }

            drawCircle(
                color = Color(0xFF777777),
                radius = radius * 0.18f,
                center = center
            )

            drawCircle(
                color = Color(0xFF222222),
                radius = radius * 0.10f,
                center = center
            )
        }

        // Touch marker remains fixed relative to the screen.
        if (touchRadius > 0f) {
            val markerDistance = radius * touchRadius

            val marker = Offset(
                x = center.x + cos(touchAngle) * markerDistance,
                y = center.y + sin(touchAngle) * markerDistance
            )

            drawCircle(
                color = Color(0xFF4CAF50),
                radius = 9.dp.toPx(),
                center = marker
            )

            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = marker,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        drawCircle(
            color = Color(0xFFBBBBBB),
            radius = 4.dp.toPx(),
            center = center
        )
    }
}
