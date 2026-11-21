package com.nusa.tunnel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nusa.tunnel.core.EngineLogLevel
import com.nusa.tunnel.core.LinkParser
import com.nusa.tunnel.core.LogRetention
import com.nusa.tunnel.core.ServerStore
import com.nusa.tunnel.core.SmartImporter
import com.nusa.tunnel.core.SubscriptionManager
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.TunnelState
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.core.VpnBehaviorSettings
import com.nusa.tunnel.ui.AppRoot
import com.nusa.tunnel.ui.Dest
import com.nusa.tunnel.ui.theme.NusaTunnelTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_VPN = 2026
    }

    private lateinit var store: ServerStore
    private var pendingSocksPort = TunnelSettings.DEFAULT_SOCKS_PORT

    /** Navigasi tertunda dari intent import (state agar memicu recomposisi). */
    private var pendingNav by mutableStateOf<Dest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ServerStore(this)

        // intent import saat app dibuka dari link / share
        handleImportIntent(intent)
        // intent import saat app sudah berjalan (singleTask)
        addOnNewIntentListener { i -> handleImportIntent(i) }

        setContent {
            NusaTunnelTheme {
                val scope = rememberCoroutineScope()
                var dest by rememberSaveable { mutableStateOf(Dest.Dashboard) }
                val servers by store.servers.collectAsState(initial = emptyList())
                val selectedId by store.selectedId.collectAsState(initial = null)
                val subUrl by store.subscriptionUrl.collectAsState(initial = null)
                val subAuto by store.subscriptionAuto.collectAsState(initial = false)
                val status by TunnelManager.status.collectAsState()
                var pingBusy by androidx.compose.runtime.remember {
                    mutableStateOf(setOf<String>())
                }
                var socksPort by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.socksPort(this@MainActivity).toString())
                }
                var sniffer by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.snifferEnabled(this@MainActivity))
                }
                var udpEngine by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.udpTunnelEngine(this@MainActivity))
                }
                var logLevel by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.logLevel(this@MainActivity))
                }
                var logRetention by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.logRetention(this@MainActivity))
                }
                var autoPing by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.autoPingEnabled(this@MainActivity))
                }
                var vpnBehavior by androidx.compose.runtime.remember {
                    mutableStateOf(TunnelSettings.vpnBehavior(this@MainActivity))
                }
                var subBusy by androidx.compose.runtime.remember { mutableStateOf(false) }

                val latestServers by rememberUpdatedState(servers)
                val latestTunnelState by rememberUpdatedState(status.state)
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(autoPing, selectedId, lifecycleOwner) {
                    if (!autoPing) return@LaunchedEffect
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isActive) {
                            val profile = latestServers.firstOrNull { it.id == selectedId }
                            if (profile != null && latestTunnelState == TunnelState.Idle) {
                                val ms = withContext(Dispatchers.IO) {
                                    TunnelManager.measureLatency(profile)
                                }
                                store.updateLatency(profile.id, ms)
                                TunnelManager.log(
                                    "Auto-ping ${profile.remarks}: ${if (ms >= 0) "$ms ms" else "gagal"}"
                                )
                            }
                            delay(5 * 60 * 1000L)
                        }
                    }
                }

                // jalankan navigasi tertunda dari intent
                pendingNav?.let { dest = it; pendingNav = null }

                AppRoot(
                    dest = dest,
                    onDest = { dest = it },
                    dashboardToggle = {
                        when (status.state) {
                            TunnelState.Connected, TunnelState.Connecting ->
                                TunnelManager.disconnect(this)
                            else -> {
                                scope.launch {
                                    val profile = store.selected()
                                    if (profile == null) {
                                        TunnelManager.log("Tidak ada server — tambahkan lewat menu Server")
                                        dest = Dest.Servers
                                    } else {
                                        val port = TunnelSettings.parseSocksPort(socksPort)
                                        if (port == null) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Port SOCKS harus berada di rentang 1–65535",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            dest = Dest.Settings
                                        } else {
                                            prepareVpnThenConnect(profile, port)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    servers = servers,
                    selectedId = selectedId,
                    pingBusy = pingBusy,
                    onAddServers = { text ->
                        // import pintar: link / banyak baris / base64 / JSON / URL subscription
                        val res = withContext(Dispatchers.IO) { SmartImporter.importText(text) }
                        if (res.error != null) {
                            TunnelManager.log("Import gagal: ${res.error}")
                            0 to res.error
                        } else {
                            if (res.subscriptionUrl != null) {
                                store.setSubscription(res.subscriptionUrl)
                                store.replaceSubscriptionServers(res.subscriptionUrl, res.servers)
                                TunnelManager.log("Subscription dipasang: ${res.servers.size} server")
                            } else {
                                store.add(*res.servers.toTypedArray())
                                TunnelManager.log("Import: ${res.servers.size} server baru")
                            }
                            res.servers.size to null
                        }
                    },
                    onSelectServer = { id -> scope.launch { store.select(id) } },
                    onDeleteServer = { id -> scope.launch { store.remove(id) } },
                    onPingServer = { p ->
                        pingBusy = pingBusy + p.id
                        scope.launch {
                            val ms = withContext(Dispatchers.IO) {
                                TunnelManager.measureLatency(p)
                            }
                            store.updateLatency(p.id, ms)
                            TunnelManager.log("Ping ${p.remarks}: ${if (ms >= 0) "$ms ms" else "gagal"}")
                            pingBusy = pingBusy - p.id
                        }
                    },
                    socksPort = socksPort,
                    onSocksPort = { value ->
                        val next = value.filter { it.isDigit() }.take(5)
                        socksPort = next
                        TunnelSettings.parseSocksPort(next)?.let { port ->
                            TunnelSettings.setSocksPort(this@MainActivity, port)
                        }
                    },
                    sniffer = sniffer,
                    onSniffer = { enabled ->
                        sniffer = enabled
                        TunnelSettings.setSnifferEnabled(this@MainActivity, enabled)
                    },
                    udpEngine = udpEngine,
                    onUdpEngine = {
                        udpEngine = it
                        TunnelSettings.setUdpTunnelEngine(this@MainActivity, it)
                    },
                    logLevel = logLevel,
                    onLogLevel = {
                        logLevel = it
                        TunnelSettings.setLogLevel(this@MainActivity, it)
                    },
                    logRetention = logRetention,
                    onLogRetention = { retention ->
                        logRetention = retention
                        TunnelSettings.setLogRetention(this@MainActivity, retention)
                        TunnelManager.setLogRetention(retention)
                    },
                    autoPing = autoPing,
                    onAutoPing = {
                        autoPing = it
                        TunnelSettings.setAutoPingEnabled(this@MainActivity, it)
                    },
                    vpnBehavior = vpnBehavior,
                    onVpnBehavior = { next ->
                        if (TunnelSettings.setVpnBehavior(this@MainActivity, next)) {
                            vpnBehavior = next
                            TunnelManager.log(
                                "Pengaturan VPN disimpan: MTU ${next.mtu}, DNS ${next.dnsServers.joinToString()}; berlaku pada koneksi berikutnya",
                            )
                            Toast.makeText(
                                this@MainActivity,
                                "Pengaturan VPN disimpan",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    subUrl = subUrl ?: "",
                    onSubUrl = { url -> scope.launch { store.setSubscription(url.ifBlank { null }) } },
                    subAuto = subAuto,
                    onSubAuto = { on -> scope.launch { store.setSubscriptionAuto(on) } },
                    subBusy = subBusy,
                    onRefreshSub = {
                        val url = subUrl
                        if (url.isNullOrBlank()) {
                            Toast.makeText(this, "Isi URL subscription dulu", Toast.LENGTH_SHORT).show()
                        } else {
                            subBusy = true
                            scope.launch {
                                val res = SubscriptionManager.update(store, url)
                                subBusy = false
                                if (res.error != null) {
                                    TunnelManager.log("Subscription gagal: ${res.error}")
                                    Toast.makeText(this@MainActivity, "Subscription gagal: ${res.error}", Toast.LENGTH_LONG).show()
                                } else {
                                    TunnelManager.log("Subscription diperbarui: ${res.servers.size} server")
                                    Toast.makeText(this@MainActivity, "Berhasil: ${res.servers.size} server", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------- import intent

    /** Tangani ACTION_VIEW (vmess/vless/trojan/ss/nusatunnel://) dan ACTION_SEND. */
    private fun handleImportIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::handleViewUri)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::handleSharedText)
        }
    }

    private fun handleViewUri(uri: Uri) {
        when (uri.scheme?.lowercase()) {
            "vmess", "vless", "trojan", "ss" -> {
                val p = LinkParser.parse(uri.toString())
                if (p == null) {
                    Toast.makeText(this, "Link tidak valid", Toast.LENGTH_SHORT).show()
                    return
                }
                lifecycleScope.launch {
                    store.add(p)
                    TunnelManager.log("Import dari link: ${p.remarks}")
                    Toast.makeText(this@MainActivity, "Server ditambahkan: ${p.remarks}", Toast.LENGTH_SHORT).show()
                    pendingNav = Dest.Servers
                }
            }
            "nusatunnel" -> {
                val host = uri.host?.lowercase()   // sub | add
                val q = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
                val url = q["url"]
                val text = q["text"]
                when (host) {
                    "sub" -> if (!url.isNullOrBlank()) {
                        lifecycleScope.launch {
                            store.setSubscription(url)
                            val res = SubscriptionManager.update(store, url)
                            Toast.makeText(
                                this@MainActivity,
                                if (res.error == null) "Subscription dipasang: ${res.servers.size} server"
                                else "Gagal: ${res.error}", Toast.LENGTH_LONG,
                            ).show()
                            pendingNav = Dest.Servers
                        }
                    }
                    "add" -> when {
                        !url.isNullOrBlank() -> handleSharedText(url)
                        !text.isNullOrBlank() -> handleSharedText(text)
                    }
                }
            }
        }
    }

    private fun handleSharedText(text: String) {
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { SmartImporter.importText(text) }
            if (res.error != null || res.servers.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    res.error ?: "Tidak ada server terdeteksi", Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            if (res.subscriptionUrl != null) {
                store.setSubscription(res.subscriptionUrl)
                store.replaceSubscriptionServers(res.subscriptionUrl, res.servers)
            } else {
                store.add(*res.servers.toTypedArray())
            }
            TunnelManager.log("Import share: ${res.servers.size} server")
            Toast.makeText(this@MainActivity, "Berhasil import ${res.servers.size} server", Toast.LENGTH_SHORT).show()
            pendingNav = Dest.Servers
        }
    }

    // ---------------------------------------------------------- vpn

    private fun prepareVpnThenConnect(profile: com.nusa.tunnel.core.ServerProfile, port: Int) {
        pendingSocksPort = port.takeIf { it in 1..65_535 } ?: TunnelSettings.DEFAULT_SOCKS_PORT
        val intent = VpnService.prepare(this)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_VPN)
        } else {
            TunnelManager.connect(this, profile, pendingSocksPort)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            lifecycleScope.launch {
                val profile = store.selected() ?: return@launch
                if (resultCode == Activity.RESULT_OK) {
                    TunnelManager.connect(this@MainActivity, profile, pendingSocksPort)
                } else {
                    TunnelManager.log("Izin VPN ditolak pengguna")
                }
            }
        }
    }
}
