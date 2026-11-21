package com.nusa.tunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.ui.components.GlassCard
import com.nusa.tunnel.ui.theme.AuroraGradient
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.FrostDim
import com.nusa.tunnel.ui.theme.FrostFaint
import com.nusa.tunnel.ui.theme.Mint
import com.nusa.tunnel.ui.theme.Spruce

@Composable
fun SettingsScreen(
    socksPort: String,
    onSocksPort: (String) -> Unit,
    sniffer: Boolean,
    onSniffer: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AuroraGradient)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text("Pengaturan", style = MaterialTheme.typography.titleLarge, color = Frost)
            Spacer(Modifier.height(16.dp))

            // ---- koneksi lokal ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lan, null, tint = Mint)
                        Spacer(Modifier.width(10.dp))
                        Text("Proxy Lokal", style = MaterialTheme.typography.titleMedium, color = Frost)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "SOCKS5 127.0.0.1:<port> — dipakai aplikasi lain saat mode proxy",
                        style = MaterialTheme.typography.bodySmall, color = FrostDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = socksPort,
                        onValueChange = { v -> onSocksPort(v.filter { it.isDigit() }.take(5)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Port SOCKS5") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Spruce,
                            unfocusedBorderColor = Color(0x22FFFFFF),
                            focusedTextColor = Frost, unfocusedTextColor = Frost,
                            focusedLabelColor = Mint, unfocusedLabelColor = FrostDim,
                            cursorColor = Spruce,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- sniffer ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, null, tint = Mint)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sniffing Trafik", style = MaterialTheme.typography.titleMedium, color = Frost)
                            Text(
                                "Deteksi domain asli dari koneksi (http/tls/quic) untuk routing akurat",
                                style = MaterialTheme.typography.bodySmall, color = FrostDim,
                            )
                        }
                        Switch(
                            checked = sniffer,
                            onCheckedChange = onSniffer,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Spruce,
                                checkedThumbColor = Frost,
                                uncheckedTrackColor = Color(0x33FFFFFF),
                                uncheckedThumbColor = FrostDim,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- info engine ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Engineering, null, tint = Mint)
                        Spacer(Modifier.width(10.dp))
                        Text("Engine", style = MaterialTheme.typography.titleMedium, color = Frost)
                    }
                    Spacer(Modifier.height(10.dp))
                    InfoRow("Versi aplikasi", "2.2.0 (1)")
                    InfoRow("Engine", com.nusa.tunnel.core.TunnelManager.engineVersion())
                    InfoRow("Mode", "VPN (TUN seluruh sistem)")
                    InfoRow("MTU", "1500")
                }
            }
            Spacer(Modifier.height(12.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Info, null, tint = FrostDim)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("NusaTunnel", style = MaterialTheme.typography.titleMedium, color = Frost)
                        Text(
                            "Developer: Xbibz Official\nEngine: Xray-core (XTLS) — koneksi nyata",
                            style = MaterialTheme.typography.bodySmall, color = FrostFaint,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier
        .fillMaxWidth()
        .padding(vertical = 3.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = FrostDim,
            modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyMedium, color = Frost)
    }
}
