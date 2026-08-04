package com.vydra.app.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    waveColor: Color = MaterialTheme.colorScheme.primaryContainer,
    heightDp: Int = 20
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 100f) / 100f,
        animationSpec = tween(durationMillis = 500, easing = EaseInOut),
        label = "progress_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(heightDp / 2))
    ) {
        val width = size.width
        val height = size.height
        val barHeight = height * 0.65f
        val barY = (height - barHeight) / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barY),
            size = Size(width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2)
        )

        if (animatedProgress > 0f) {
            val progressWidth = width * animatedProgress

            val brush = Brush.horizontalGradient(
                colors = listOf(
                    progressColor.copy(alpha = 0.7f),
                    progressColor,
                    progressColor.copy(alpha = 0.9f)
                ),
                startX = 0f,
                endX = progressWidth
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, barY),
                size = Size(progressWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2)
            )

            val waveAmplitude = height * 0.15f
            val waveFrequency = 0.03f

            val wavePath = androidx.compose.ui.graphics.Path().apply {
                val startX = 0f
                val endX = progressWidth
                val midY = barY + barHeight / 2f

                moveTo(startX, midY)

                var x = startX
                while (x <= endX) {
                    val waveOffset = sin((x * waveFrequency) + wavePhase) * waveAmplitude
                    lineTo(x, midY + waveOffset)
                    x += 2f
                }

                lineTo(endX, barY + barHeight)
                lineTo(startX, barY + barHeight)
                close()
            }

            drawPath(
                path = wavePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        waveColor.copy(alpha = 0.3f),
                        waveColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = progressWidth
                )
            )

            val shimmerX = shimmerPhase * progressWidth
            if (shimmerX in 0f..progressWidth) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        center = Offset(shimmerX, barY + barHeight / 2f),
                        radius = barHeight
                    ),
                    radius = barHeight,
                    center = Offset(shimmerX, barY + barHeight / 2f)
                )
            }

            drawRoundRect(
                color = progressColor,
                topLeft = Offset(progressWidth - 3.dp.toPx(), barY),
                size = Size(6.dp.toPx(), barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}

@Composable
fun WavyDownloadCard(
    title: String,
    progress: Float,
    speed: String = "",
    status: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (speed.isNotEmpty()) {
                Text(
                    text = speed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        WavyProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
