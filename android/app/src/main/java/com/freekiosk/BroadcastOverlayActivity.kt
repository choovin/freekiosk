package com.freekiosk

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen overlay activity that displays fieldtrip broadcast messages.
 * Appears on top of lock screen, auto-dismisses after 10 seconds or on tap.
 */
class BroadcastOverlayActivity : Activity() {

    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val message = intent.getStringExtra("message") ?: ""

        // Build full-screen layout programmatically
        val textView = TextView(this).apply {
            text = message
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val layout = FrameLayout(this).apply {
            addView(textView)
            setOnClickListener { finish() }
        }

        setContentView(layout)

        handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ finish() }, 10_000) // auto-dismiss after 10s

        // Play beep sound
        try {
            val mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            // Sound not available — continue silently
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
