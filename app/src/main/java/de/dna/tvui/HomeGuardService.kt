package de.dna.tvui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Homescreen-Wächter: Sobald der eingebaute Homescreen in den Vordergrund
 * kommt (nach dem Einschalten oder über eine fest verdrahtete Home-Taste),
 * holt er sofort TVUI nach vorne.
 */
class HomeGuardService : AccessibilityService() {

    private var otherLaunchers: Set<String> = emptySet()
    private var lastJump = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        otherLaunchers = findOtherLaunchers()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in otherLaunchers) return
        val now = System.currentTimeMillis()
        if (now - lastJump < 1500) return
        lastJump = now
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    /** Alle anderen Homescreens, ohne Einstellungen/Einrichtung (die dürfen bleiben). */
    private fun findOtherLaunchers(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .filter { pkg ->
                pkg != packageName && pkg != "android" &&
                    listOf("settings", "provision", "setup").none { pkg.contains(it, ignoreCase = true) }
            }
            .toSet()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.startsWith(context.packageName + "/") }
        }
    }
}
