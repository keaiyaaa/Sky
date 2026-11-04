package com.example.msdksample.drone

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.msdksample.drone.state.*
import java.util.concurrent.atomic.AtomicBoolean

/** One main-thread render per snapshot; expiry changes presentation, never observation timestamps. */
class TelemetryUiObserver(
    private val provider: DeviceStateProvider,
    private val render: (DeviceStateSnapshot, TelemetryDisplay) -> Unit
) : DeviceStateSubscription {
    private val handler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(true)
    private var lastVersion = -1L
    private val expiry = Runnable { show(provider.currentSnapshot()) }
    private val subscription = provider.subscribe { state ->
        if (active.get()) handler.post { show(state) }
    }

    private fun show(state: DeviceStateSnapshot) {
        if (!active.get() || state.version < lastVersion) return
        lastVersion = state.version
        val now = SystemClock.elapsedRealtime()
        val display = TelemetryDisplay.from(state, now)
        handler.removeCallbacks(expiry)
        render(state, display)
        if (active.get()) display.nextRefreshAtMonotonicMs?.let {
            handler.postDelayed(expiry, (it - now).coerceAtLeast(1))
        }
    }

    override fun cancel() {
        active.set(false)
        subscription.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
