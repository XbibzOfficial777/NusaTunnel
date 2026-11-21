package com.nusa.tunnel.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.store by preferencesDataStore("nusatunnel")

/**
 * Penyimpanan server (DataStore Preferences + kotlinx-serialization).
 */
class ServerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY_SERVERS = stringPreferencesKey("servers_json")
    private val KEY_SELECTED = stringPreferencesKey("selected_id")

    val servers: Flow<List<ServerProfile>> =
        context.store.data.map { prefs ->
            prefs[KEY_SERVERS]?.let { runCatching { json.decodeFromString<List<ServerProfile>>(it) }.getOrNull() } ?: emptyList()
        }

    val selectedId: Flow<String?> = context.store.data.map { it[KEY_SELECTED] }

    suspend fun selected(): ServerProfile? {
        val id = context.store.data.first()[KEY_SELECTED] ?: return null
        return servers.first().firstOrNull { it.id == id }
    }

    suspend fun save(list: List<ServerProfile>, selectedId: String? = null) {
        context.store.edit { prefs ->
            prefs[KEY_SERVERS] = json.encodeToString(list)
            if (selectedId != null) prefs[KEY_SELECTED] = selectedId
        }
    }

    suspend fun add(vararg profiles: ServerProfile) {
        val cur = servers.first()
        save(cur + profiles, selectedId = profiles.firstOrNull()?.id ?: context.store.data.first()[KEY_SELECTED])
    }

    suspend fun remove(id: String) {
        val cur = servers.first()
        val curSel = context.store.data.first()[KEY_SELECTED]
        save(cur.filterNot { it.id == id }, selectedId = if (curSel == id) null else curSel)
    }

    suspend fun select(id: String) {
        context.store.edit { it[KEY_SELECTED] = id }
    }

    suspend fun updateLatency(id: String, latencyMs: Int) {
        val cur = servers.first()
        val idx = cur.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val copy = cur.toMutableList().also { it[idx] = it[idx].copy(latencyMs = latencyMs) }
            save(copy)
        }
    }
}
