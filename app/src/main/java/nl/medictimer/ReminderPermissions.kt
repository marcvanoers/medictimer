package nl.medictimer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext

internal fun notificationSettingsIntent(context: Context): Intent {
    val blockedChannel = context.getSystemService(android.app.NotificationManager::class.java)
        .getNotificationChannel(Reminders.CHANNEL)?.importance == android.app.NotificationManager.IMPORTANCE_NONE
    val showChannel = blockedChannel && Reminders.notificationAccessAllowed(context)
    return Intent(if (showChannel) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).apply {
            if (showChannel) putExtra(Settings.EXTRA_CHANNEL_ID, Reminders.CHANNEL)
        }
}

@RequiresApi(31)
internal fun preciseSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
    Uri.parse("package:${context.packageName}"))

/** Open OS controls even when permission was previously denied permanently or already granted. */
@Composable
internal fun rememberEnableReminders(settings: Preferences, onChanged: () -> Unit, onMessage: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val changed by rememberUpdatedState(onChanged)
    val message by rememberUpdatedState(onMessage)
    var enabling by rememberSaveable { mutableStateOf(false) }

    fun finish() {
        settings.enabled = Reminders.notificationsAllowed(context)
        enabling = false
        changed()
        if (!settings.enabled) message("Reminders are still off. Allow MedicTimer notifications in Android Settings, then return here.")
        else if (!Reminders.exactAllowed(context)) message("Reminders are enabled, but Android may deliver them late until Alarms & reminders access is allowed.")
    }
    val precise = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (enabling) finish() else changed() }
    fun afterNotifications() {
        if (enabling && Reminders.notificationsAllowed(context) && Build.VERSION.SDK_INT >= 31 && !Reminders.exactAllowed(context)) {
            try { precise.launch(preciseSettingsIntent(context)) }
            catch (_: ActivityNotFoundException) { finish() }
        } else if (enabling) finish() else changed()
    }
    val channel = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterNotifications() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (enabling && Reminders.notificationAccessAllowed(context) && !Reminders.notificationsAllowed(context)) {
            // App-wide access was granted but the reminder channel is still blocked. Open it once;
            // returning from that screen always finishes instead of reopening a denied setting.
            try { channel.launch(notificationSettingsIntent(context)) }
            catch (_: ActivityNotFoundException) { finish() }
        } else afterNotifications()
    }
    return {
        enabling = true
        settings.enabled = false
        Reminders.ensureChannel(context)
        changed()
        try {
            val intent = notificationSettingsIntent(context)
            if (intent.action == Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) channel.launch(intent) else notifications.launch(intent)
        }
        catch (_: ActivityNotFoundException) {
            try { notifications.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            catch (_: ActivityNotFoundException) {
                enabling = false
                message("Android settings could not be opened. Open this phone’s Settings and allow MedicTimer notifications.")
            }
        }
    }
}
