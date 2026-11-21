package com.nusa.tunnel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.core.ServerProfile
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelState
import com.nusa.tunnel.ui.components.GlassCard
import com.nusa.tunnel.ui.theme.AuroraGradient
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.FrostDim
import com.nusa.tunnel.ui.theme.FrostFaint
import com.nusa.tunnel.ui.theme.Mint
import com.nusa.tunnel.ui.theme.Spruce
import com.nusa.tunnel.ui.theme.Warn
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 15

@Composable
fun ServersScreen(
    servers: List<ServerProfile>,
    selectedId: String?,
    pingBusy: Set<String>,
    onAdd: suspend (links: String) -> Pair<Int, String?>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPing: (ServerProfile) -> Unit,
    subUrl: String,
    onSubUrl: (String) -> Unit,
    subAuto: Boolean,
    onSubAuto: (Boolean) -> Unit,
    subBusy: Boolean,
    onRefreshSub: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // reset paginasi hanya saat pencarian berubah atau jumlah server berubah
    // (update latency tidak me-reset halaman)
    var shown by remember(query, servers.size) { mutableIntStateOf(PAGE_SIZE) }
    val scope = rememberCoroutineScope()

    val filtered = remember(servers, query) {
        if (query.isBlank()) servers
        else servers.filter {
            it.remarks.contains(query, true) || it.host.contains(query, true)
        }
    }
    val visible = filtered.take(shown)

    Box(
        Modifier
            .fillMaxSize()
            .background(AuroraGradient)
    ) {
        Column(Modifier.fillMaxSize()) {

            // header + pencarian
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VpnKey, null, tint = Mint)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Server (${servers.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Frost,
                )
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                corner = 14.dp,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Cari nama / host…", style = MaterialTheme.typography.bodyMedium)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Spruce,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Frost,
                        unfocusedTextColor = Frost,
                        cursorColor = Spruce,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---- panel subscription URL ----
            SubscriptionCard(
                subUrl = subUrl,
                onSubUrl = onSubUrl,
                subAuto = subAuto,
                onSubAuto = onSubAuto,
                subBusy = subBusy,
                onRefreshSub = onRefreshSub,
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(visible, key = { _, s -> s.id }) { _, server ->
                    ServerRow(
                        server = server,
                        selected = server.id == selectedId,
                        pinging = server.id in pingBusy,
                        onSelect = { onSelect(server.id) },
                        onDelete = { onDelete(server.id) },
                        onPing = { onPing(server) },
                    )
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    if (visible.size < filtered.size) {
                        // MUAT LAGI: +15 per klik, tidak menampilkan semua
                        Button(
                            onClick = { shown += PAGE_SIZE },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x1FFFFFFF),
                                contentColor = Frost,
                            ),
                        ) {
                            Text("MUAT LAGI (+${PAGE_SIZE})")
                        }
                    }
                    Text(
                        "Menampilkan ${visible.size} dari ${filtered.size} server",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        // tombol import melayang
        var showAdd by remember { mutableStateOf(false) }
        var importBusy by remember { mutableStateOf(false) }
        AddFab(onClick = { showAdd = true })

        if (showAdd) {
            AddDialog(
                busy = importBusy,
                onDismiss = { if (!importBusy) showAdd = false },
                onAdd = { links ->
                    scope.launch {
                        importBusy = true
                        val (n, err) = onAdd(links)
                        importBusy = false
                        if (err == null) {
                            showAdd = false
                            TunnelManager.log("Import: $n server baru")
                        }
                    }
                },
            )
        }
    }
}

/** Klik tanpa ripple (biar bersih di kartu custom). */
private fun Modifier.androidClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )

@Composable
private fun ServerRow(
    server: ServerProfile,
    selected: Boolean,
    pinging: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onPing: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .androidClickable(onSelect),
        corner = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // indikator terpilih
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> Mint
                            else -> Color(0x33FFFFFF)
                        }
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.remarks,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) Mint else Frost,
                    maxLines = 1,
                )
                Text(
                    "${server.proto} · ${server.host}:${server.port} · ${server.network}${if (server.tls) " + TLS" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostDim,
                    maxLines = 1,
                )
            }
            // ping nyata
            if (pinging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp,
                    color = Spruce,
                )
                Spacer(Modifier.width(10.dp))
            } else if (server.latencyMs >= 0) {
                Text(
                    "${server.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        server.latencyMs < 150 -> Mint
                        server.latencyMs < 400 -> Warn
                        else -> Color(0xFFFF6B7A)
                    },
                )
                Spacer(Modifier.width(10.dp))
            }
            IconButton(onClick = onPing) {
                Icon(
                    Icons.Rounded.Speed, contentDescription = "Ping",
                    tint = FrostDim, modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline, contentDescription = "Hapus",
                    tint = FrostDim, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Panel subscription URL — pasang, refresh manual, auto-update. */
@Composable
private fun SubscriptionCard(
    subUrl: String,
    onSubUrl: (String) -> Unit,
    subAuto: Boolean,
    onSubAuto: (Boolean) -> Unit,
    subBusy: Boolean,
    onRefreshSub: () -> Unit,
) {
    var url by remember(subUrl) { mutableStateOf(subUrl) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        corner = 14.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudSync, null, tint = Mint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Subscription",
                    style = MaterialTheme.typography.titleSmall,
                    color = Frost,
                )
                Spacer(Modifier.weight(1f))
                if (subBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = Spruce,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("https://contoh.com/sub?token=…", style = MaterialTheme.typography.bodyMedium)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Spruce,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Frost,
                    unfocusedTextColor = Frost,
                    cursorColor = Spruce,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Auto-update",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostDim,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = subAuto,
                    onCheckedChange = onSubAuto,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Spruce,
                        checkedThumbColor = Color(0xFF07131F),
                        uncheckedTrackColor = Color(0x22FFFFFF),
                        uncheckedThumbColor = FrostDim,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onSubUrl(url) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x1FFFFFFF),
                        contentColor = Frost,
                    ),
                ) {
                    Text("SIMPAN URL", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        onSubUrl(url)
                        onRefreshSub()
                    },
                    enabled = url.isNotBlank() && !subBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Spruce,
                        contentColor = Color(0xFF07131F),
                    ),
                ) {
                    Text("MUAT SERVER", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AddFab(onClick: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Spruce, contentColor = Color(0xFF07131F),
            ),
        ) {
            Icon(Icons.Rounded.AddLink, null)
            Spacer(Modifier.width(8.dp))
            Text("IMPORT LINK", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AddDialog(busy: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            corner = 24.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Import Server",
                    style = MaterialTheme.typography.titleLarge,
                    color = Frost,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tempel APA PUN — dideteksi otomatis:\n" +
                        "• link vmess:// vless:// trojan:// ss://\n" +
                        "• URL subscription http(s)\n" +
                        "• blob base64\n" +
                        "• config JSON Xray",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostDim,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("vmess://eyJ2IjoiMiIs… / https://… / { \"outbounds\": … }") },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Spruce,
                        unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedTextColor = Frost,
                        unfocusedTextColor = Frost,
                        cursorColor = Spruce,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("BATAL", color = FrostDim) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(text) },
                        enabled = text.isNotBlank() && !busy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Spruce, contentColor = Color(0xFF07131F),
                        ),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF07131F),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("MEMPROSES…")
                        } else {
                            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("IMPORT")
                        }
                    }
                }
            }
        }
    }
}

