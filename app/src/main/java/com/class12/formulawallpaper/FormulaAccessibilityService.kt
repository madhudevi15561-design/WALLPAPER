package com.class12.formulawallpaper

import android.accessibilityservice.AccessibilityService
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class FormulaAccessibilityService : AccessibilityService() {

    private val executor = Executors.newSingleThreadExecutor()
    private var isReceiverRegistered = false
    private var lastSwapElapsedMs = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                swapHomeWallpaper()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!isReceiverRegistered) {
            ContextCompat.registerReceiver(
                applicationContext,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterScreenOffReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenOffReceiver()
        executor.shutdown()
        super.onDestroy()
    }

    private fun unregisterScreenOffReceiver() {
        if (isReceiverRegistered) {
            try {
                applicationContext.unregisterReceiver(screenOffReceiver)
            } catch (_: IllegalArgumentException) {
            }
            isReceiverRegistered = false
        }
    }

    fun swapHomeWallpaper() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSwapElapsedMs < 800L) return
        lastSwapElapsedMs = now

        executor.execute {
            try {
                val card = FormulaRepository.popNextCard(applicationContext)
                val bitmap = WallpaperRenderer.render(applicationContext, card)
                WallpaperManager.getInstance(applicationContext).setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM
                )
                bitmap.recycle()
            } catch (_: Exception) {
            }
        }
    }
}
