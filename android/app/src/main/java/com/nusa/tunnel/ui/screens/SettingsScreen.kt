package com.nusa.tunnel.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
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
import com.nusa.tunnel.core.ConnectionCheckSettings
import com.nusa.tunnel.core.ConnectionCheckSettingsValidator
import com.nusa.tunnel.core.EngineLogLevel
import com.nusa.tunnel.core.SpeedUnit
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.core.VpnAppInfo
import com.nusa.tunnel.core.VpnAppRoutingMode
import com.nusa.tunnel.core.VpnAppRoutingSettings
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
) {
    var subPage by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = subPage.isNotEmpty()) { subPage = "" }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuroraGradient)
    ) {
        if (subPage == "per_app") {
            PerAppVpnPage(
                settings = vpnAppRouting,
                apps = availableVpnApps,
                onSave = onVpnAppRouting,
                onBack = { subPage = "" },
            )
        } else if (subPage == "connection_check") {
            ConnectionCheckPage(
                settings = connectionCheck,
                onSave = onConnectionCheck,
                onBack = { subPage = "" },
            )
        } else {
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
                    LogEntryLimitRow(
                        value = logEntryLimit,
                        onSave = onLogEntryLimit,
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = "Speed unit",
                        summary = "Mengubah satuan laju unggah/unduh langsung di Beranda; total sesi tetap dalam byte.",
                        selected = speedUnit.label,
                        options = SpeedUnit.entries,
                        optionLabel = { it.label },
                        onSelected = onSpeedUnit,
                        testTag = "speed_unit_choice",
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
                    SettingsActionRow(
                        title = "Per-app VPN",
                        summary = "${vpnAppRouting.mode.label} · ${vpnAppRouting.selectedPackages.size} selected · applies next connection",
                        onClick = { subPage = "per_app" },
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "Connection check",
                        summary = if (connectionCheck.enabled) {
                            "${connectionCheck.url} · every ${connectionCheck.intervalSeconds}s"
                        } else {
                            "${connectionCheck.url} · disabled"
                        },
                        onClick = { subPage = "connection_check" },
                        testTag = "connection_check_row",
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Auto Ping",
                        summary = "Ping server terpilih setiap lima menit hanya saat aplikasi terlihat dan VPN terputus.",
                        checked = autoPing,
                        onCheckedChange = onAutoPing,
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Keep CPU awake",
                        summary = "Gunakan Android PARTIAL_WAKE_LOCK hanya selama interface VPN aktif; langsung diperbarui saat terhubung dan dilepas saat disconnect.",
                        checked = keepCpuAwake,
                        onCheckedChange = onKeepCpuAwake,
                        testTag = "keep_cpu_awake_switch",
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "Battery usage",
                        summary = "Buka info aplikasi Android; kontrol baterai yang tersedia bergantung pada perangkat.",
                        onClick = onOpenBatterySettings,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "Always-on VPN",
                        summary = "Buka daftar VPN Android; Always-on dan lockdown bergantung pada versi perangkat.",
                        onClick = onOpenAlwaysOnVpnSettings,
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
}

@Composable
private fun PerAppVpnPage(
    settings: VpnAppRoutingSettings,
    apps: List<VpnAppInfo>,
    onSave: (VpnAppRoutingSettings) -> Unit,
    onBack: () -> Unit,
) {
    var modeValue by rememberSaveable(settings.mode.preferenceValue) {
        mutableStateOf(settings.mode.preferenceValue)
    }
    val mode = VpnAppRoutingMode.fromPreference(modeValue)
    val initialPackages = settings.selectedPackages.sorted().joinToString("\n")
    var selectedPackagesText by rememberSaveable(initialPackages) {
        mutableStateOf(initialPackages)
    }
    var search by rememberSaveable { mutableStateOf("") }
    val selectedPackages = selectedPackagesText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    val filteredApps = apps.filter { app ->
        search.isBlank() || app.label.contains(search, ignoreCase = true) ||
            app.packageName.contains(search, ignoreCase = true)
    }

    fun togglePackage(packageName: String, checked: Boolean) {
        val updated = selectedPackages.toMutableSet().apply {
            if (checked) add(packageName) else remove(packageName)
        }
        selectedPackagesText = updated.sorted().joinToString("\n")
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = Frost)
            }
            Column {
                Text("Per-app VPN", style = MaterialTheme.typography.titleLarge, color = Frost)
                Text("Berlaku pada koneksi berikutnya", style = MaterialTheme.typography.bodySmall, color = FrostDim)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChoiceRow(
            title = "Application routing",
            summary = "Atur aplikasi yang memakai VPN Android. NusaTunnel sendiri selalu dikecualikan agar koneksi engine tidak berputar ke TUN.",
            selected = mode.label,
            options = VpnAppRoutingMode.entries,
            optionLabel = { it.label },
            onSelected = { modeValue = it.preferenceValue },
            testTag = "vpn_app_mode_choice",
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().testTag("vpn_app_search"),
            label = { Text("Cari aplikasi") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${selectedPackages.size} dipilih · ${apps.size} aplikasi dengan ikon peluncur",
            style = MaterialTheme.typography.bodySmall,
            color = FrostDim,
        )
        Spacer(Modifier.height(6.dp))
        if (filteredApps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (apps.isEmpty()) "Tidak ada aplikasi peluncur yang dapat dipilih."
                    else "Aplikasi tidak ditemukan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FrostDim,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("vpn_app_list"),
            ) {
                items(filteredApps, key = VpnAppInfo::packageName) { app ->
                    val checked = app.packageName in selectedPackages
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { togglePackage(app.packageName, !checked) }
                            .testTag("vpn_app_${app.packageName}")
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { togglePackage(app.packageName, it) },
                            modifier = Modifier.testTag("vpn_app_check_${app.packageName}"),
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall, color = Frost)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = FrostDim)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                onSave(VpnAppRoutingSettings(mode, selectedPackages))
                onBack()
            },
            enabled = mode != VpnAppRoutingMode.SelectedOnly || selectedPackages.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("save_vpn_app_routing"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Frost),
        ) {
            Text("Save · applies next connection")
        }
    }
}

@Composable
private fun ConnectionCheckPage(
    settings: ConnectionCheckSettings,
    onSave: (ConnectionCheckSettings) -> Unit,
    onBack: () -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var url by rememberSaveable { mutableStateOf(settings.url) }
    var intervalText by rememberSaveable { mutableStateOf(settings.intervalSeconds.toString()) }
    var timeoutText by rememberSaveable { mutableStateOf(settings.timeoutSeconds.toString()) }
    var attemptsText by rememberSaveable { mutableStateOf(settings.maxAttempts.toString()) }
    var stopOnFailure by rememberSaveable { mutableStateOf(settings.stopOnFailure) }

    val interval = intervalText.toIntOrNull()?.takeIf {
        it in ConnectionCheckSettings.MIN_INTERVAL_SECONDS..ConnectionCheckSettings.MAX_INTERVAL_SECONDS
    }
    val timeout = timeoutText.toIntOrNull()?.takeIf {
        it in ConnectionCheckSettings.MIN_TIMEOUT_SECONDS..ConnectionCheckSettings.MAX_TIMEOUT_SECONDS
    }
    val attempts = attemptsText.toIntOrNull()?.takeIf {
        it in ConnectionCheckSettings.MIN_MAX_ATTEMPTS..ConnectionCheckSettings.MAX_MAX_ATTEMPTS
    }
    val urlValid = ConnectionCheckSettingsValidator.isValidUrl(url)
    val formValid = urlValid && interval != null && timeout != null && attempts != null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = Frost)
            }
            Text("Connection check", style = MaterialTheme.typography.titleLarge, color = Frost)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pemeriksaan mengirim GET melalui SOCKS5 lokal Xray yang aktif, bukan koneksi langsung Android. " +
                "Respons HTTP 2xx/3xx dianggap berhasil. Pengaturan dapat diubah saat tunnel tersambung.",
            style = MaterialTheme.typography.bodySmall,
            color = FrostDim,
        )
        Spacer(Modifier.height(16.dp))

        SettingsSection("Probe", Icons.Rounded.Lan) {
            SwitchRow(
                title = "Enable connection check",
                summary = "Jalankan pemeriksaan berkala hanya ketika tunnel berstatus connected.",
                checked = enabled,
                onCheckedChange = { enabled = it },
                testTag = "connection_check_enabled",
            )
            SettingsDivider()
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.take(2_048) },
                modifier = Modifier.fillMaxWidth().testTag("connection_check_url"),
                label = { Text("HTTP(S) URL") },
                isError = !urlValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = {
                    if (!urlValid) Text(
                        "Gunakan URL absolut HTTP/HTTPS tanpa user/password atau fragment.",
                        color = Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                shape = RoundedCornerShape(14.dp),
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
            SettingsDivider()
            NumericOrIpField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { character -> character.isDigit() }.take(4) },
                label = "Interval (${ConnectionCheckSettings.MIN_INTERVAL_SECONDS}–${ConnectionCheckSettings.MAX_INTERVAL_SECONDS}s)",
                valid = interval != null,
                error = "Interval harus ${ConnectionCheckSettings.MIN_INTERVAL_SECONDS}–${ConnectionCheckSettings.MAX_INTERVAL_SECONDS} detik.",
                testTag = "connection_check_interval",
                keyboardType = KeyboardType.Number,
            )
            SettingsDivider()
            NumericOrIpField(
                value = timeoutText,
                onValueChange = { timeoutText = it.filter { character -> character.isDigit() }.take(2) },
                label = "Timeout (${ConnectionCheckSettings.MIN_TIMEOUT_SECONDS}–${ConnectionCheckSettings.MAX_TIMEOUT_SECONDS}s)",
                valid = timeout != null,
                error = "Timeout harus ${ConnectionCheckSettings.MIN_TIMEOUT_SECONDS}–${ConnectionCheckSettings.MAX_TIMEOUT_SECONDS} detik.",
                testTag = "connection_check_timeout",
                keyboardType = KeyboardType.Number,
            )
            SettingsDivider()
            NumericOrIpField(
                value = attemptsText,
                onValueChange = { attemptsText = it.filter { character -> character.isDigit() }.take(2) },
                label = "Maximum attempts (${ConnectionCheckSettings.MIN_MAX_ATTEMPTS}–${ConnectionCheckSettings.MAX_MAX_ATTEMPTS})",
                valid = attempts != null,
                error = "Jumlah percobaan harus ${ConnectionCheckSettings.MIN_MAX_ATTEMPTS}–${ConnectionCheckSettings.MAX_MAX_ATTEMPTS}.",
                testTag = "connection_check_attempts",
                keyboardType = KeyboardType.Number,
            )
            SettingsDivider()
            SwitchRow(
                title = "Stop VPN on failure",
                summary = "Putuskan tunnel jika seluruh percobaan pada satu siklus gagal.",
                checked = stopOnFailure,
                onCheckedChange = { stopOnFailure = it },
                testTag = "connection_check_stop_on_failure",
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    enabled = false
                    url = ConnectionCheckSettings.DEFAULT_URL
                    intervalText = ConnectionCheckSettings.DEFAULT_INTERVAL_SECONDS.toString()
                    timeoutText = ConnectionCheckSettings.DEFAULT_TIMEOUT_SECONDS.toString()
                    attemptsText = ConnectionCheckSettings.DEFAULT_MAX_ATTEMPTS.toString()
                    stopOnFailure = false
                },
                modifier = Modifier.weight(1f).testTag("reset_connection_check"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Frost),
            ) { Text("Reset") }
            OutlinedButton(
                onClick = {
                    onSave(
                        ConnectionCheckSettings(
                            enabled = enabled,
                            url = url.trim(),
                            intervalSeconds = requireNotNull(interval),
                            timeoutSeconds = requireNotNull(timeout),
                            maxAttempts = requireNotNull(attempts),
                            stopOnFailure = stopOnFailure,
                        ),
                    )
                    onBack()
                },
                enabled = formValid,
                modifier = Modifier.weight(1f).testTag("save_connection_check"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Frost),
            ) { Text("Save") }
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
    testTag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
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
private fun LogEntryLimitRow(
    value: Int,
    onSave: (Int) -> Unit,
) {
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(value.toString()) }
    val parsedLimit = TunnelSettings.parseLogEntryLimit(draft)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                draft = value.toString()
                dialogVisible = true
            }
            .testTag("log_entry_limit_row")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text("Log entry limit", style = MaterialTheme.typography.titleSmall, color = Frost)
            Spacer(Modifier.height(3.dp))
            Text(
                "$value entries · older entries are trimmed immediately",
                style = MaterialTheme.typography.bodySmall,
                color = FrostDim,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FrostDim)
    }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text("Log entry limit") },
            text = {
                Column {
                    Text(
                        "Keep 100 to 10000 entries. Older entries beyond this limit are removed. " +
                            "One entry can span several screen lines.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth().testTag("log_entry_limit_input"),
                        label = { Text("Entries") },
                        isError = parsedLimit == null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            if (parsedLimit == null) {
                                Text("Enter a whole number from 100 to 10,000.")
                            }
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
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { draft = TunnelSettings.DEFAULT_LOG_ENTRY_LIMIT.toString() },
                        modifier = Modifier.testTag("reset_log_entry_limit"),
                    ) { Text("Reset") }
                    TextButton(
                        onClick = { dialogVisible = false },
                        modifier = Modifier.testTag("cancel_log_entry_limit"),
                    ) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            parsedLimit?.let { limit ->
                                onSave(limit)
                                dialogVisible = false
                            }
                        },
                        enabled = parsedLimit != null,
                        modifier = Modifier.testTag("save_log_entry_limit"),
                    ) { Text("Save") }
                }
            },
            containerColor = Color(0xFF262932),
            titleContentColor = Frost,
            textContentColor = FrostDim,
        )
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
    testTag: String? = null,
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
            modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier,
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
    testTag: String? = null,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
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
