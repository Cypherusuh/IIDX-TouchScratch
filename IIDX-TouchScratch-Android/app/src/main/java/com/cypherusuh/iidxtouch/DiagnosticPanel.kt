
package com.cypherusuh.iidxtouch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun ConnectionAndVelocityPanel(
    connected: Boolean,
    angularVelocity: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = if (connected) "BACKEND CONNECTED" else "CONNECTING...",
            color = if (connected) Color(0xFF4CAF50) else Color(0xFFFFB74D),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = String.format(Locale.US, "%.2f rad/s", angularVelocity),
            color = Color.White,
            fontSize = 30.sp
        )

        Text(
            text = when {
                angularVelocity > 0.05f -> "CLOCKWISE"
                angularVelocity < -0.05f -> "COUNTERCLOCKWISE"
                else -> "STOPPED"
            },
            color = Color.LightGray,
            fontSize = 13.sp
        )
    }
}

@Composable
fun DiagnosticPanel(
    averageRadius: Float,
    touchAngle: Float,
    fingerCount: Int,
    platterRotation: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC151515))
            .padding(16.dp)
    ) {
        DiagnosticRow("Touch radius", "%.2f".format(averageRadius))
        DiagnosticRow("Touch angle", "%.2f rad".format(touchAngle))
        DiagnosticRow("Active fingers", fingerCount.toString())
        DiagnosticRow("Platter rotation", "%.1f°".format(platterRotation))
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}
