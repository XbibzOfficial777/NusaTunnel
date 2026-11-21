package com.nusa.tunnel

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.nusa.tunnel.core.LinkParser
import com.nusa.tunnel.core.ServerStore
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelState
import com.nusa.tunnel.ui.AppRoot
import com.nusa.tunnel.ui.Dest
import com.nusa.tunnel.ui.theme.NusaTunnelTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_VPN = 2026
    }

    private lateinit var store: ServerStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ServerStore(this)

        setContent {
            NusaTunnelTheme {
                val scope = rememberCoroutineScope()
                var dest by rememberSaveable { mutableStateOf(Dest.Dashboard) }
                val servers by store.servers.collectAsState(initial = emptyList())
                val selectedId by store.selectedId.collectAsState(initial = null)
                val status by TunnelManager.status.collectAsState()
                var pingBusy by androidx.compose.runtime.remember {
                    mutableStateOf(setOf<String>())
                }
                var socksPort by androidx.compose.runtime.remember {
                    mutableStateOf("10808")
                }
                var sniffer by androidx.compose.runtime.remember { mutableStateOf(true) }

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
                                        prepareVpnThenConnect(
                                            profile,
                                            socksPort.toIntOrNull() ?: 10808,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    servers = servers,
                    selectedId = selectedId,
                    pingBusy = pingBusy,
                    onAddServers = { links ->
                        val parsed = LinkParser.parseMany(links)
                        if (parsed.isNotEmpty()) scope.launch { store.add(*parsed.toTypedArray()) }
                        parsed.size
                    },
                    onSelectServer = { id -> scope.launch { store.select(id) } },
                    onDeleteServer = { id -> scope.launch { store.remove(id) } },
                    onPingServer = { p ->
                        pingBusy = pingBusy + p.id
                        scope.launch {
                            val ms = TunnelManager.measureLatency(p)
                            store.updateLatency(p.id, ms)
                            TunnelManager.log("Ping ${p.remarks}: ${if (ms >= 0) "$ms ms" else "gagal"}")
                            pingBusy = pingBusy - p.id
                        }
                    },
                    socksPort = socksPort,
                    onSocksPort = { socksPort = it },
                    sniffer = sniffer,
                    onSniffer = { sniffer = it },
                )
            }
        }
    }

    private fun prepareVpnThenConnect(profile: com.nusa.tunnel.core.ServerProfile, port: Int) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_VPN)
        } else {
            TunnelManager.connect(this, profile, port)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            lifecycleScope.launch {
                val profile = store.selected() ?: return@launch
                if (resultCode == Activity.RESULT_OK) {
                    TunnelManager.connect(this@MainActivity, profile, 10808)
                } else {
                    TunnelManager.log("Izin VPN ditolak pengguna")
                }
            }
        }
    }
}
