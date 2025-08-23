package com.scrollguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Minimal BOOT_COMPLETED receiver to satisfy manifest and avoid startup crashes.
 * You can extend this later to restore services on boot if desired.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Timber.d("BootReceiver: device boot completed")
            // No-op for now. Optionally schedule work or start services here.
        }
    }
}

