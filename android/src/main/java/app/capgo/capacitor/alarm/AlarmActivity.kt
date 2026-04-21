package app.capgo.capacitor.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen alarm UI shown over the lock screen when an alarm fires.
 */
class AlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally empty — user must tap Dismiss
            }
        })

        setupWindowFlags()

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        setContentView(buildLayout(label))
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun buildLayout(label: String): LinearLayout {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#F7F2EE"))

            addView(TextView(ctx).apply {
                text = label
                textSize = 28f
                setTextColor(android.graphics.Color.parseColor("#1C1816"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            })

            addView(Button(ctx).apply {
                text = "Dismiss"
                setBackgroundColor(android.graphics.Color.parseColor("#B07468"))
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener { dismissAlarm() }
            })
        }
    }

    private fun dismissAlarm() {
        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_DISMISS
        }
        // Use ContextCompat.startForegroundService — guards against
        // IllegalStateException on API 26+ when the service is no longer
        // running as a foreground service (e.g. OOM-killed while activity
        // was visible).
        try {
            ContextCompat.startForegroundService(this, stopIntent)
        } catch (_: Exception) {
            // Service may already be gone; activity close is still the right outcome.
        }
        finish()
    }
}
