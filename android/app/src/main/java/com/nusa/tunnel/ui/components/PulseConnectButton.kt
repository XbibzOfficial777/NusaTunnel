package com.nusa.tunnel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nusa.tunnel.core.TunnelState
import com.nusa.tunnel.ui.theme.Aurora0
import com.nusa.tunnel.ui.theme.ButtonGradient
import com.nusa.tunnel.ui.theme.Mint
import com.nusa.tunnel.ui.theme.Spruce

/** Main tunnel action with low-latency press feedback and an active-only aurora pulse. */
@Composable
fun PulseConnectButton(
    state: TunnelState,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    onClick: () -> Unit,
) {
    val active = state == TunnelState.Connecting || state == TunnelState.Connected
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "connect-button-press",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (active && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            ActivePulseRings()
        }

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(Spruce, Color(0xFF00986A)))
                    else ButtonGradient
                )
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                AnimatedContent(
                    targetState = state == TunnelState.Connected,
                    transitionSpec = {
                        (fadeIn(tween(190, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.82f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "connect-icon",
                ) { connected ->
                    Icon(
                        imageVector = if (connected) Icons.Rounded.Bolt
                        else Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = Aurora0,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = state.buttonLabel(),
                    transitionSpec = {
                        (slideInVertically(
                            initialOffsetY = { height -> height / 3 },
                            animationSpec = tween(210, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(180)))
                            .togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { height -> -height / 4 },
                                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                                ) + fadeOut(tween(130)),
                            )
                    },
                    label = "connect-label",
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Aurora0,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun TunnelState.buttonLabel(): String = when (this) {
    TunnelState.Connecting -> "MENGHUBUNGKAN…"
    TunnelState.Connected -> "TERHUBUNG"
    TunnelState.Disconnecting -> "MEMUTUSKAN…"
    else -> "HUBUNGKAN"
}

@Composable
private fun BoxScope.ActivePulseRings() {
    val transition = rememberInfiniteTransition(label = "connect-pulse")
    val firstScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-ring-one-scale",
    )
    val firstAlpha by transition.animateFloat(
        initialValue = 0.44f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-ring-one-alpha",
    )
    val secondScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, delayMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-ring-two-scale",
    )
    val secondAlpha by transition.animateFloat(
        initialValue = 0.44f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, delayMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-ring-two-alpha",
    )

    PulseRing(firstScale, firstAlpha)
    PulseRing(secondScale, secondAlpha)
}

@Composable
private fun BoxScope.PulseRing(scale: Float, alpha: Float) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .border(2.dp, Mint.copy(alpha = 0.82f), CircleShape)
    )
}
