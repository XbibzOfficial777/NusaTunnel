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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.ui.components.GlassCard
import com.nusa.tunnel.ui.theme.Aurora0
import com.nusa.tunnel.ui.theme.AuroraGradient
import com.nusa.tunnel.ui.theme.Frost
import com.nusa.tunnel.ui.theme.FrostDim
import com.nusa.tunnel.ui.theme.Mint

@Composable
fun LogsScreen() {
    val logs by TunnelManager.logs.collectAsState()
    val listState = rememberLazyListState()

    // auto-scroll ke baris terbaru
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuroraGradient)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = Mint)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Log (${logs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Frost,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { TunnelManager.clearLogs() }) {
                    Icon(Icons.Rounded.DeleteSweep, "Bersihkan", tint = FrostDim)
                }
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                corner = 16.dp,
            ) {
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada log.\nHubungkan tunnel untuk mulai merekam aktivitas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FrostDim,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x0A000000))
                            .padding(12.dp),
                    ) {
                        items(logs) { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = when {
                                    line.contains("TERHUBUNG", true) -> Mint
                                    line.contains("Error", true) || line.contains("gagal", true) ->
                                        Color(0xFFFF8A96)
                                    else -> FrostDim
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
