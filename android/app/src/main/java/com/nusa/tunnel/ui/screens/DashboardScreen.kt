package com.nusa.tunnel.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.core.SpeedUnit
import com.nusa.tunnel.core.TrafficSpeed
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelState
import com.nusa.tunnel.ui.components.GlassCard
import com.nusa.tunnel.ui.components.PulseConnectButton
import com.nusa.tunnel.ui.theme.AuroraGlow
import com.nusa.tunnel.ui.theme.AuroraGradient
import com.nusa.tunnel.ui.theme.Danger
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.FrostDim
import com.nusa.tunnel.ui.theme.FrostFaint
import com.nusa.tunnel.ui.theme.Mint
import com.nusa.tunnel.ui.theme.Spruce

@Composable
fun DashboardScreen(
    serverName: String,
    speedUnit: SpeedUnit,
    onToggle: () -> Unit,
) {
    val status by TunnelManager.status.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(AuroraGradient)
    ) {
        // glow aurora di belakang tombol
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(420.dp)
                .alpha(0.45f)
                .background(AuroraGlow, CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                "NUSATUNNEL",
                style = MaterialTheme.typography.labelSmall,
                color = FrostFaint,
            )
            Spacer(Modifier.height(18.dp))

            // status pill
            StatusPill(status.state)

            Spacer(Modifier.weight(1f))

            // server aktif
            AnimatedContent(
                targetState = status.serverName.ifBlank { serverName },
                transitionSpec = {
                    (slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(280)))
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "serverName",
            ) { name ->
                Text(
                    text = name.ifBlank { "Belum ada server dipilih" },
                    style = MaterialTheme.typography.titleLarge,
                    color = Frost,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                status.engineVersion,
                style = MaterialTheme.typography.bodySmall,
                color = FrostFaint,
            )

            Spacer(Modifier.height(30.dp))

            PulseConnectButton(
                state = status.state,
                onClick = onToggle,
            )

            Spacer(Modifier.weight(1f))

            // statistik trafik NYATA (counter Xray)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TrafficStat(
                        icon = { Icon(Icons.Rounded.ArrowUpward, null, tint = Mint) },
                        label = "UNGGAH",
                        value = TunnelManager.fmtBytes(status.upBytes),
                        liveSpeed = TrafficSpeed.format(status.uploadBytesPerSecond, speedUnit),
                    )
                    TrafficStat(
                        icon = { Icon(Icons.Rounded.ArrowDownward, null, tint = Spruce) },
                        label = "UNDUH",
                        value = TunnelManager.fmtBytes(status.downBytes),
                        liveSpeed = TrafficSpeed.format(status.downloadBytesPerSecond, speedUnit),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: TunnelState) {
    val (bg, fg, label) = when (state) {
        TunnelState.Idle -> Triple(Color(0x1FFFFFFF), FrostDim, "TIDAK AKTIF")
        TunnelState.Connecting -> Triple(Color(0x33FFC55C), Color(0xFFFFC55C), "MENGHUBUNGKAN…")
        TunnelState.Connected -> Triple(Color(0x3300C27A), Mint, "TERHUBUNG")
        TunnelState.Disconnecting -> Triple(Color(0x33FFC55C), Color(0xFFFFC55C), "MEMUTUSKAN…")
        TunnelState.Error -> Triple(Color(0x33FF6B7A), Danger, "GAGAL")
    }
    GlassCard(corner = 999.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                TunnelState.Connecting, TunnelState.Disconnecting ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = fg,
                    )
                TunnelState.Connected ->
                    Icon(Icons.Rounded.Bolt, null, tint = fg, modifier = Modifier.size(14.dp))
                TunnelState.Error ->
                    Icon(Icons.Rounded.ErrorOutline, null, tint = fg, modifier = Modifier.size(14.dp))
                else ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(fg.copy(alpha = 0.6f))
                    )
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun TrafficStat(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    liveSpeed: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = Frost,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = FrostFaint)
        Spacer(Modifier.height(4.dp))
        Text(liveSpeed, style = MaterialTheme.typography.labelSmall, color = FrostDim)
    }
}
