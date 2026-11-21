package com.nusa.tunnel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.core.ConnectionCheckSettings
import com.nusa.tunnel.core.EngineLogLevel
import com.nusa.tunnel.core.SpeedUnit
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.core.VpnAppInfo
import com.nusa.tunnel.core.VpnAppRoutingSettings
import com.nusa.tunnel.core.VpnBehaviorSettings
import com.nusa.tunnel.ui.components.AnimatedAuroraBackdrop
import com.nusa.tunnel.ui.screens.DashboardScreen
import com.nusa.tunnel.ui.screens.LogsScreen
import com.nusa.tunnel.ui.screens.ServersScreen
import com.nusa.tunnel.ui.screens.SettingsScreen
import com.nusa.tunnel.ui.theme.Aurora1
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.FrostDim
import com.nusa.tunnel.ui.theme.Spruce

enum class Dest(
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("Beranda", Icons.Rounded.Bolt),
    Servers("Server", Icons.Rounded.VpnKey),
    Settings("Atur", Icons.Rounded.Settings),
    Logs("Log", Icons.AutoMirrored.Rounded.ReceiptLong),
}

@Composable
private fun AnimatedNavIcon(icon: ImageVector, label: String, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navigation-icon-scale",
    )
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

@Composable
fun AppRoot(
    dest: Dest,
    onDest: (Dest) -> Unit,
    dashboardToggle: () -> Unit,
    servers: List<com.nusa.tunnel.core.ServerProfile>,
    selectedId: String?,
    pingBusy: Set<String>,
    onAddServers: suspend (String) -> Pair<Int, String?>,
    onSelectServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onPingServer: (com.nusa.tunnel.core.ServerProfile) -> Unit,
    socksPort: String,
    onSocksPort: (String) -> Unit,
    sniffer: Boolean,
    onSniffer: (Boolean) -> Unit,
    udpEngine: UdpTunnelEngine,
    onUdpEngine: (UdpTunnelEngine) -> Unit,
    logLevel: EngineLogLevel,
    onLogLevel: (EngineLogLevel) -> Unit,
    logEntryLimit: Int,
    onLogEntryLimit: (Int) -> Unit,
    speedUnit: SpeedUnit,
    onSpeedUnit: (SpeedUnit) -> Unit,
    autoPing: Boolean,
    onAutoPing: (Boolean) -> Unit,
    keepCpuAwake: Boolean,
    onKeepCpuAwake: (Boolean) -> Unit,
    connectionCheck: ConnectionCheckSettings,
    onConnectionCheck: (ConnectionCheckSettings) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAlwaysOnVpnSettings: () -> Unit,
    vpnBehavior: VpnBehaviorSettings,
    onVpnBehavior: (VpnBehaviorSettings) -> Unit,
    vpnAppRouting: VpnAppRoutingSettings,
    availableVpnApps: List<VpnAppInfo>,
    onVpnAppRouting: (VpnAppRoutingSettings) -> Unit,
    subUrl: String,
    onSubUrl: (String) -> Unit,
    subAuto: Boolean,
    onSubAuto: (Boolean) -> Unit,
    subBusy: Boolean,
    onRefreshSub: () -> Unit,
) {
    Scaffold(
        containerColor = Aurora1,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xF2050B16),
                contentColor = Frost,
            ) {
                Dest.entries.forEach { d ->
                    val selected = dest == d
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onDest(d) },
                        icon = { AnimatedNavIcon(d.icon, d.label, selected) },
                        label = { Text(d.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Spruce,
                            selectedTextColor = Spruce,
                            unselectedIconColor = FrostDim,
                            unselectedTextColor = FrostDim,
                            indicatorColor = Color(0x2600C27A),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedAuroraBackdrop(Modifier.fillMaxSize())
            AnimatedContent(
                targetState = dest,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    val enter = slideInHorizontally(
                        initialOffsetX = { width -> width / 10 * direction },
                        animationSpec = spring(
                            dampingRatio = 0.92f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    )
                    val exit = slideOutHorizontally(
                        targetOffsetX = { width -> -width / 18 * direction },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(170))
                    enter.togetherWith(exit)
                },
                label = "screen",
            ) { d ->
                when (d) {
                    Dest.Dashboard -> DashboardScreen(
                        serverName = servers.firstOrNull { it.id == selectedId }?.remarks ?: "",
                        speedUnit = speedUnit,
                        onToggle = dashboardToggle,
                    )
                    Dest.Servers -> ServersScreen(
                        servers = servers,
                        selectedId = selectedId,
                        pingBusy = pingBusy,
                        onAdd = onAddServers,
                        onSelect = onSelectServer,
                        onDelete = onDeleteServer,
                        onPing = onPingServer,
                        subUrl = subUrl,
                        onSubUrl = onSubUrl,
                        subAuto = subAuto,
                        onSubAuto = onSubAuto,
                        subBusy = subBusy,
                        onRefreshSub = onRefreshSub,
                    )
                    Dest.Settings -> SettingsScreen(
                        socksPort = socksPort,
                        onSocksPort = onSocksPort,
                        sniffer = sniffer,
                        onSniffer = onSniffer,
                        udpEngine = udpEngine,
                        onUdpEngine = onUdpEngine,
                        logLevel = logLevel,
                        onLogLevel = onLogLevel,
                        logEntryLimit = logEntryLimit,
                        onLogEntryLimit = onLogEntryLimit,
                        speedUnit = speedUnit,
                        onSpeedUnit = onSpeedUnit,
                        autoPing = autoPing,
                        onAutoPing = onAutoPing,
                        keepCpuAwake = keepCpuAwake,
                        onKeepCpuAwake = onKeepCpuAwake,
                        connectionCheck = connectionCheck,
                        onConnectionCheck = onConnectionCheck,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenAlwaysOnVpnSettings = onOpenAlwaysOnVpnSettings,
                        vpnBehavior = vpnBehavior,
                        onVpnBehavior = onVpnBehavior,
                        vpnAppRouting = vpnAppRouting,
                        availableVpnApps = availableVpnApps,
                        onVpnAppRouting = onVpnAppRouting,
                    )
                    Dest.Logs -> LogsScreen()
                }
            }
        }
    }
}
