package com.nusa.tunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.BuildConfig
import com.nusa.tunnel.core.EngineLogLevel
import com.nusa.tunnel.core.LogRetention
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.ui.components.GlassCard
import com.nusa.tunnel.ui.theme.AuroraGradient
import com.nusa.tunnel.ui.theme.Danger
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.FrostDim
import com.nusa.tunnel.ui.theme.Mint
import com.nusa.tunnel.ui.theme.Spruce

@Composable
fun SettingsScreen(
    socksPort: String,
    onSocksPort: (String) -> Unit,
    sniffer: Boolean,
    onSniffer: (Boolean) -> Unit,
    udpEngine: UdpTunnelEngine,
    onUdpEngine: (UdpTunnelEngine) -> Unit,
    logLevel: EngineLogLevel,
    onLogLevel: (EngineLogLevel) -> Unit,
    logRetention: LogRetention,
    onLogRetention: (LogRetention) -> Unit,
    autoPing: Boolean,
    onAutoPing: (Boolean) -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("Pengaturan", style = MaterialTheme.typography.titleLarge, color = Frost)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pengaturan yang disimpan dan dipakai oleh tunnel NusaTunnel.",
                style = MaterialTheme.typography.bodySmall,
                color = FrostDim,
            )
            Spacer(Modifier.height(20.dp))

            SettingsSection("Umum", Icons.Rounded.Info) {
                ChoiceRow(
                    title = "Tingkat log engine",
                    summary = "Detail log Xray dan Hev. Perubahan berlaku pada koneksi berikutnya.",
                    selected = logLevel.label,
                    options = EngineLogLevel.entries,
                    optionLabel = { it.label },
                    onSelected = onLogLevel,
                )
                SettingsDivider()
                ChoiceRow(
                    title = "Riwayat log",
                    summary = "Jumlah entri yang disimpan di memori aplikasi. Mengubah batas langsung merapikan log yang ada.",
                    selected = logRetention.label,
                    options = LogRetention.entries,
                    optionLabel = { it.label },
                    onSelected = onLogRetention,
                )
            }
            Spacer(Modifier.height(16.dp))

            SettingsSection("Koneksi", Icons.Rounded.Lan) {
                SocksPortRow(value = socksPort, onValueChange = onSocksPort)
                SettingsDivider()
                SwitchRow(
                    title = "Ping otomatis",
                    summary = "Cek server terpilih tiap 5 menit hanya saat aplikasi di depan dan tunnel terputus. Nonaktif secara default agar tidak menambah trafik idle.",
                    checked = autoPing,
                    onCheckedChange = onAutoPing,
                )
            }
            Spacer(Modifier.height(16.dp))

            SettingsSection("Tunnel", Icons.Rounded.Settings) {
                SwitchRow(
                    title = "Deteksi tujuan (sniffing)",
                    summary = "Deteksi tujuan HTTP, TLS, dan QUIC untuk routing. Perubahan berlaku pada koneksi berikutnya.",
                    checked = sniffer,
                    onCheckedChange = onSniffer,
                )
                SettingsDivider()
                ChoiceRow(
                    title = "Engine paket UDP",
                    summary = "Pilih HevSocks5Tunnel (Android 10+) atau Xray native TUN. Android 9 ke bawah otomatis memakai Xray; pilihan berlaku pada koneksi berikutnya.",
                    selected = udpEngine.label,
                    options = UdpTunnelEngine.entries,
                    optionLabel = { it.label },
                    onSelected = onUdpEngine,
                )
            }
            Spacer(Modifier.height(16.dp))

            SettingsSection("Tentang", Icons.Rounded.Engineering) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    InfoRow("Versi aplikasi", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    InfoRow("Engine", TunnelManager.engineVersion())
                    InfoRow("Mode", "VPN · TUN seluruh sistem")
                    InfoRow("MTU", "1500")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android mendukung VMess, VLESS, Trojan, dan Shadowsocks melalui Xray-core. Pengaturan di atas tidak mengaktifkan protokol yang belum tersedia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostDim,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Mint)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Frost)
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth(), corner = 18.dp) {
            Column(content = content)
        }
    }
}

@Composable
private fun SocksPortRow(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val valid = TunnelSettings.parseSocksPort(value) != null
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Port SOCKS5 lokal", style = MaterialTheme.typography.titleSmall, color = Frost)
        Spacer(Modifier.height(3.dp))
        Text(
            "127.0.0.1 · dipakai tunnel untuk meneruskan trafik. Perubahan berlaku saat koneksi berikutnya.",
            style = MaterialTheme.typography.bodySmall,
            color = FrostDim,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port (1–65535)") },
            supportingText = {
                Text(
                    if (valid) "Port tersimpan. Nilai ini akan dipakai saat menghubungkan."
                    else "Masukkan angka port yang valid (1–65535).",
                    color = if (valid) FrostDim else Danger,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            isError = !valid,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Spruce,
                unfocusedBorderColor = Color(0x44FFFFFF),
                errorBorderColor = Danger,
                focusedTextColor = Frost,
                unfocusedTextColor = Frost,
                errorTextColor = Frost,
                focusedLabelColor = Mint,
                unfocusedLabelColor = FrostDim,
                errorLabelColor = Danger,
                cursorColor = Spruce,
            ),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Frost)
            Spacer(Modifier.height(3.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = FrostDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Spruce,
                checkedThumbColor = Color(0xFF07131F),
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedThumbColor = FrostDim,
            ),
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    summary: String,
    selected: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Frost)
        Spacer(Modifier.height(3.dp))
        Text(summary, style = MaterialTheme.typography.bodySmall, color = FrostDim)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Frost),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selected, modifier = Modifier.weight(1f), maxLines = 2)
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Pilih")
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Color(0x26FFFFFF)),
    )
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = FrostDim,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Frost)
    }
}
