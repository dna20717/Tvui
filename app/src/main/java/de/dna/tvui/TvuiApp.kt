package de.dna.tvui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper

/**
 * Wenn der Fernseher aus dem Standby aufwacht, startet Philips seinen eigenen
 * Homescreen. Wir warten kurz und holen dann TVUI wieder nach vorne.
 */
class TvuiApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private val bringToFront = Runnable { showHome(this) }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(bringToFront)
            handler.postDelayed(bringToFront, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    companion object {
        fun showHome(context: Context) {
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            } catch (_: Exception) {}
        }
    }
}

/** Nach einem kompletten Neustart: TVUI öffnen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).postDelayed({ TvuiApp.showHome(context.applicationContext) }, 3000)
    }
}
