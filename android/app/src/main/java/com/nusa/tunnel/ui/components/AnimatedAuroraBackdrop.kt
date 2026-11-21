package com.nusa.tunnel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nusa.tunnel.ui.theme.AuroraGradient
import com.nusa.tunnel.ui.theme.AuroraGlow
import com.nusa.tunnel.ui.theme.AuroraMintGlow

/** Low-motion ambient aurora; its infinite clocks are removed while the Activity is stopped. */
@Composable
fun AnimatedAuroraBackdrop(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()

    Box(modifier.fillMaxSize().background(AuroraGradient)) {
        if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            AuroraGlowLayers()
        }
    }
}

@Composable
private fun BoxScope.AuroraGlowLayers() {
    val transition = rememberInfiniteTransition(label = "ambient-aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-drift",
    )
    val counterDrift by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-counter-drift",
    )

    Box(
        Modifier
            .align(Alignment.TopEnd)
            .size(460.dp)
            .graphicsLayer {
                translationX = (drift - 0.5f) * size.width * 0.30f
                translationY = (counterDrift - 0.5f) * size.height * 0.16f
                alpha = 0.72f
            }
            .clip(CircleShape)
            .background(AuroraGlow)
    )
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .size(390.dp)
            .graphicsLayer {
                translationX = (0.5f - counterDrift) * size.width * 0.24f
                translationY = (0.5f - drift) * size.height * 0.12f
                alpha = 0.60f
            }
            .clip(CircleShape)
            .background(AuroraMintGlow)
    )
}
