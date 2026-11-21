package com.nusa.tunnel.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.BuildConfig
import com.nusa.tunnel.core.EngineLogLevel
import com.nusa.tunnel.core.LogRetention
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.core.VpnBehaviorSettings
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
    vpnBehavior: VpnBehaviorSettings,
    onVpnBehavior: (VpnBehaviorSettings) -> Unit,
) {
    var subPage by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = subPage.isNotEmpty()) { subPage = "" }

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
            if (subPage == "vpn") {
                VpnBehaviorPage(
                    settings = vpnBehavior,
                    onSave = onVpnBehavior,
                    onBack = { subPage = "" },
                )
            } else {
                Text("Settings", style = MaterialTheme.typography.titleLarge, color = Frost)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pengaturan yang tersimpan dan dipakai oleh engine Android NusaTunnel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostDim,
                )
                Spacer(Modifier.height(20.dp))

                SettingsSection("General", Icons.Rounded.Info) {
                    ChoiceRow(
                        title = "Log filter",
                        summary = "Mengatur tingkat detail log dari Xray dan Hev. Berlaku pada koneksi berikutnya.",
                        selected = logLevel.label,
                        options = EngineLogLevel.entries,
                        optionLabel = { it.label },
                        onSelected = onLogLevel,
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = "Log entry limit",
                        summary = "Jumlah entri log yang dipertahankan di memori. Perubahan langsung merapikan entri lama.",
                        selected = logRetention.label,
                        options = LogRetention.entries,
                        optionLabel = { it.label },
                        onSelected = onLogRetention,
                    )
                }
                Spacer(Modifier.height(16.dp))

                SettingsSection("Connection", Icons.Rounded.Lan) {
                    SocksPortRow(value = socksPort, onValueChange = onSocksPort)
                    SettingsDivider()
                    SettingsActionRow(
                        title = "VPN behavior",
                        summary = "DNS ${vpnBehavior.primaryDns}, ${vpnBehavior.fallbackDns} · MTU ${vpnBehavior.mtu}",
                        onClick = { subPage = "vpn" },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Auto Ping",
                        summary = "Ping server terpilih setiap lima menit hanya saat aplikasi terlihat dan VPN terputus.",
                        checked = autoPing,
                        onCheckedChange = onAutoPing,
                    )
                }
                Spacer(Modifier.height(16.dp))

                SettingsSection("Tunnel", Icons.Rounded.Settings) {
                    SwitchRow(
                        title = "Destination detection",
                        summary = "Deteksi tujuan HTTP, TLS, dan QUIC melalui konfigurasi sniffing Xray. Berlaku pada koneksi berikutnya.",
                        checked = sniffer,
                        onCheckedChange = onSniffer,
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = "TUN packet engine",
                        summary = "HevSocks5Tunnel (Android 10+) atau Xray native TUN. Android 9 ke bawah memakai Xray native.",
                        selected = udpEngine.label,
                        options = UdpTunnelEngine.entries,
                        optionLabel = { it.label },
                        onSelected = onUdpEngine,
                    )
                }
                Spacer(Modifier.height(16.dp))

                SettingsSection("About", Icons.Rounded.Engineering) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        InfoRow("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        InfoRow("Engine", TunnelManager.engineVersion())
                        InfoRow("Mode", "Android VPN · TUN")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Saat ini Android benar-benar mendukung VMess, VLESS, Trojan, dan Shadowsocks melalui Xray-core. SSH, SlowDNS, HCR, Psiphon, OpenVPN, dan fitur lanjutan lain belum diaktifkan sebagai kontrol palsu; implementasinya dicatat di roadmap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostDim,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VpnBehaviorPage(
    settings: VpnBehaviorSettings,
    onSave: (VpnBehaviorSettings) -> Unit,
    onBack: () -> Unit,
) {
    var primaryDns by rememberSaveable(settings.primaryDns) { mutableStateOf(settings.primaryDns) }
    var fallbackDns by rememberSaveable(settings.fallbackDns) { mutableStateOf(settings.fallbackDns) }
    var mtuText by rememberSaveable(settings.mtu) { mutableStateOf(settings.mtu.toString()) }
    val mtu = TunnelSettings.parseVpnMtu(mtuText)
    val dnsValid = TunnelSettings.isValidIpv4Address(primaryDns) &&
        TunnelSettings.isValidIpv4Address(fallbackDns)

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = Frost)
        }
        Text("VPN behavior", style = MaterialTheme.typography.titleLarge, color = Frost)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "DNS dan MTU ini dipakai Android VpnService, Xray, dan Hev pada koneksi berikutnya. DNS harus berupa alamat IPv4.",
        style = MaterialTheme.typography.bodySmall,
        color = FrostDim,
    )
    Spacer(Modifier.height(16.dp))

    SettingsSection("DNS", Icons.Rounded.Lan) {
        NumericOrIpField(
            value = primaryDns,
            onValueChange = { primaryDns = it },
            label = "Primary DNS",
            testTag = "vpn_primary_dns",
            valid = TunnelSettings.isValidIpv4Address(primaryDns),
            error = "Masukkan alamat IPv4 yang valid, misalnya 8.8.8.8.",
        )
        SettingsDivider()
        NumericOrIpField(
            value = fallbackDns,
            onValueChange = { fallbackDns = it },
            label = "Fallback DNS",
            testTag = "vpn_fallback_dns",
            valid = TunnelSettings.isValidIpv4Address(fallbackDns),
            error = "Masukkan alamat IPv4 yang valid, misalnya 1.1.1.1.",
        )
    }
    Spacer(Modifier.height(16.dp))

    SettingsSection("MTU", Icons.Rounded.Settings) {
        NumericOrIpField(
            value = mtuText,
            onValueChange = { mtuText = it.filter { character -> character.isDigit() }.take(4) },
            label = "MTU (1280–9000)",
            testTag = "vpn_mtu",
            valid = mtu != null,
            error = "Masukkan MTU antara ${VpnBehaviorSettings.MIN_MTU} dan ${VpnBehaviorSettings.MAX_MTU}.",
            keyboardType = KeyboardType.Number,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "IPv6 saat ini tetap dirutekan melalui VPN agar trafik tidak keluar langsung di luar tunnel. Kontrol untuk mematikan IPv6 baru akan ditambahkan setelah jalur pemblokiran kebocorannya tervalidasi.",
        style = MaterialTheme.typography.bodySmall,
        color = FrostDim,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = {
            onSave(
                VpnBehaviorSettings(
                    primaryDns = primaryDns.trim(),
                    fallbackDns = fallbackDns.trim(),
                    mtu = mtu ?: settings.mtu,
                ),
            )
        },
        enabled = dnsValid && mtu != null,
        modifier = Modifier.fillMaxWidth().testTag("save_vpn_behavior"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Frost),
    ) {
        Text("Save · applies next connection")
    }
}

@Composable
private fun NumericOrIpField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    valid: Boolean,
    error: String,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Ascii,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            label = { Text(label) },
            isError = !valid,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            supportingText = {
                if (!valid) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
            },
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
private fun SettingsActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Frost)
            Spacer(Modifier.height(3.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = FrostDim)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FrostDim)
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
