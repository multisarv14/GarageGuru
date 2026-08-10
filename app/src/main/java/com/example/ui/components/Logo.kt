package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun MechanicalGLogo(
    modifier: Modifier = Modifier
) {
    // Sheen animation state
    val infiniteTransition = rememberInfiniteTransition(label = "Sheen")
    val sheenProgress by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SheenAnimation"
    )

    Canvas(modifier = modifier.size(120.dp)) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2, height / 2)
        val radius = width.coerceAtMost(height) / 2.3f

        // 1. Draw Outer Shadow Ring
        drawCircle(
            color = Color(0x3D0F172A),
            radius = radius + 8f,
            center = center
        )

        // 2. Draw Premium Metallic Background (Circular Badge)
        val innerSilverGradient = Brush.radialGradient(
            colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF475569)),
            center = center,
            radius = radius
        )
        drawCircle(
            brush = innerSilverGradient,
            radius = radius,
            center = center
        )

        // 3. Draw Polished Metallic Wrench (Crossed at 45 degree angle)
        rotate(degrees = 45f, pivot = center) {
            val wrenchPath = Path().apply {
                // Main shaft
                moveTo(center.x - 12f, center.y - radius * 0.8f)
                lineTo(center.x + 12f, center.y - radius * 0.8f)
                lineTo(center.x + 12f, center.y + radius * 0.8f)
                lineTo(center.x - 12f, center.y + radius * 0.8f)
                close()

                // Top head outer circle
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center.x - 30f, center.y - radius * 0.95f,
                        center.x + 30f, center.y - radius * 0.55f
                    )
                )

                // Bottom head outer circle
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center.x - 30f, center.y + radius * 0.55f,
                        center.x + 30f, center.y + radius * 0.95f
                    )
                )
            }

            // Draw wrench body
            drawPath(
                path = wrenchPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFFE2E8F0))
                )
            )

            // Draw wrench open jaws (subtracted inside drawing)
            val jawPathTop = Path().apply {
                moveTo(center.x - 12f, center.y - radius * 1.0f)
                lineTo(center.x + 12f, center.y - radius * 1.0f)
                lineTo(center.x, center.y - radius * 0.7f)
                close()
            }
            drawPath(path = jawPathTop, color = Color(0xFF475569)) // background contrast matching innerSilverGradient edge

            val jawPathBottom = Path().apply {
                moveTo(center.x - 12f, center.y + radius * 1.0f)
                lineTo(center.x + 12f, center.y + radius * 1.0f)
                lineTo(center.x, center.y + radius * 0.7f)
                close()
            }
            drawPath(path = jawPathBottom, color = Color(0xFF475569))
        }

        // 4. Draw stylized Matte-Grey Outer "G" wrapped on top
        val gPath = Path().apply {
            // Draw a thick arc that forms "G", leaving right center side open and connecting with cross bar
            addArc(
                oval = androidx.compose.ui.geometry.Rect(
                    center.x - radius * 0.75f, center.y - radius * 0.75f,
                    center.x + radius * 0.75f, center.y + radius * 0.75f
                ),
                startAngleDegrees = -45f,
                sweepAngleDegrees = 270f
            )
        }

        // G outer line
        drawPath(
            path = gPath,
            color = Color(0xFF0F172A),
            style = Stroke(width = 16f, cap = StrokeCap.Round)
        )

        // G horizontal bar line
        val barPath = Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x + radius * 0.65f, center.y)
            lineTo(center.x + radius * 0.65f, center.y + 14f)
            lineTo(center.x, center.y + 14f)
            close()
        }
        drawPath(
            path = barPath,
            color = Color(0xFF0F172A)
        )

        // 5. High-gloss Light Sheen Line overlay (translucent white sliding brush)
        val sheenBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.0f),
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.0f)
            ),
            start = Offset(sheenProgress, -100f),
            end = Offset(sheenProgress + 150f, 400f)
        )

        drawCircle(
            brush = sheenBrush,
            radius = radius - 2f,
            center = center
        )
    }
}
