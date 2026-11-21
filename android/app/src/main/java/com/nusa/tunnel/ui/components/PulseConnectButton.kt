package com.nusa.tunnel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import com.nusa.tunnel.core.TunnelState
import com.nusa.tunnel.ui.theme.Aurora0
import com.nusa.tunnel.ui.theme.ButtonGradient
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.GlassStroke
import com.nusa.tunnel.ui.theme.Mint
import com.nusa.tunnel.ui.theme.Spruce

/**
 * Tombol hubungkan besar — cincin aurora berdenyut saat aktif.
 * Animasi infinite: lingkaran glow mengembang + memudar.
 */
@Composable
fun PulseConnectButton(
    state: TunnelState,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    onClick: () -> Unit,
) {
    val active = state == TunnelState.Connecting || state == TunnelState.Connected

    val transition = rememberInfiniteTransition(label = "pulse")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringScale",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha",
    )
    val coreScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "core",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (active) {
            // cincin denyut (2 lapis)
            repeat(2) { i ->
                val delay = i * 0.5f
                Box(
                    modifier = Modifier
                        .size(size)
                        .scale(ringScale - delay * 0.2f)
                        .alpha(ringAlpha)
                        .clip(CircleShape)
                        .border(2.dp, Spruce.copy(alpha = 0.7f), CircleShape)
                )
            }
        }
        // inti tombol
        Box(
            modifier = Modifier
                .size(size)
                .scale(if (active) coreScale else 1f)
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(Spruce, Color(0xFF00986A)))
                    else ButtonGradient
                )
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                .androidClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (state == TunnelState.Connected) Icons.Rounded.Bolt
                                  else Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = Aurora0,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (state) {
                        TunnelState.Connecting -> "MENGHUBUNGKAN"
                        TunnelState.Connected -> "TERHUBUNG"
                        TunnelState.Disconnecting -> "MEMUTUSKAN"
                        else -> "HUBUNGKAN"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Aurora0,
                )
            }
        }
    }
}

/** Klik tanpa ripple (biar bersih di tombol custom). */
private fun Modifier.androidClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )
