package com.cocoexperiments.tumbler

import android.app.Application
import com.cocoexperiments.tumbler.data.PinRepository
import com.cocoexperiments.tumbler.service.LockState
import kotlin.concurrent.thread

class TumblerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keystore + encrypted prefs initialisation is slow on first use; warm it off the main thread
        // so the lock screen opens instantly, then cache the locked-app list for the guard.
        thread(name = "pin-prefs-warmup") { LockState.reload(this) }
    }
}
