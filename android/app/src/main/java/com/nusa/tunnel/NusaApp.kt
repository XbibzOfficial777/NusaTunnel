package com.nusa.tunnel

import android.app.Application
import com.nusa.tunnel.core.ServerStore
import com.nusa.tunnel.core.SubscriptionManager
import com.nusa.tunnel.core.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NusaApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Engine Xray harus siap sebelum UI jalan (gobind butuh Seq context)
        TunnelManager.initEngine(this)

        // auto-update subscription (jika diaktifkan) di latar belakang
        appScope.launch {
            runCatching { SubscriptionManager.autoUpdate(ServerStore(this@NusaApp)) }
        }
    }
}
