package com.nusa.tunnel

import android.app.Application
import com.nusa.tunnel.core.TunnelManager

class NusaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Engine Xray harus siap sebelum UI jalan (gobind butuh Seq context)
        TunnelManager.initEngine(this)
    }
}
