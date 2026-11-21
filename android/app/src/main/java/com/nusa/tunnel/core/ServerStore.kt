package com.nusa.tunnel.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val KEY_SUB_URL = stringPreferencesKey("sub_url")
    private val KEY_SUB_AUTO = booleanPreferencesKey("sub_auto")

    val servers: Flow<List<ServerProfile>> =
        context.store.data.map { prefs ->
            prefs[KEY_SERVERS]?.let { runCatching { json.decodeFromString<List<ServerProfile>>(it) }.getOrNull() } ?: emptyList()
        }

    val selectedId: Flow<String?> = context.store.data.map { it[KEY_SELECTED] }

    /** URL subscription aktif (null/blank = belum dipasang). */
    val subscriptionUrl: Flow<String?> = context.store.data.map { it[KEY_SUB_URL] }

    /** Auto-update subscription tiap aplikasi dibuka. */
    val subscriptionAuto: Flow<Boolean> = context.store.data.map { it[KEY_SUB_AUTO] ?: false }

    suspend fun setSubscription(url: String?) {
        context.store.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(KEY_SUB_URL) else prefs[KEY_SUB_URL] = url.trim()
        }
    }

    suspend fun setSubscriptionAuto(enabled: Boolean) {
        context.store.edit { it[KEY_SUB_AUTO] = enabled }
    }

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

    /**
     * Ganti seluruh server milik satu subscription (subTag == subUrl) dengan daftar baru,
     * server manual / subscription lain tidak tersentuh. Pilihan tetap dipertahankan bila masih ada.
     */
    suspend fun replaceSubscriptionServers(subUrl: String, fresh: List<ServerProfile>) {
        val cur = servers.first()
        val curSel = context.store.data.first()[KEY_SELECTED]
        val selStillExists = cur.any { it.id == curSel && it.subTag != subUrl } ||
                fresh.any { it.id == curSel }
        val merged = cur.filter { it.subTag != subUrl } + fresh
        save(merged, selectedId = if (selStillExists) curSel else fresh.firstOrNull()?.id)
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
