package nl.medictimer

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Reminders {
    private const val CHANNEL = "daily-care"
    private fun alarm(context: Context, key: String, title: String = "", body: String = "", meal: Long = 0): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = key
            putExtra("title", title); putExtra("body", body); putExtra("meal", meal)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    // Permission is checked immediately below; also tolerate revocation between check and call.
    @SuppressLint("MissingPermission")
    private fun schedule(context: Context, at: Long, pending: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return }
            catch (_: SecurityException) { /* Permission may have changed since the check. */ }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun cancelMeal(context: Context, id: Long, dismiss: Boolean = true) {
        context.getSystemService(AlarmManager::class.java).cancel(alarm(context, "meal:$id"))
        if (dismiss) NotificationManagerCompat.from(context).cancel("meal:$id", 1)
    }

    fun refresh(context: Context) {
        val settings = Preferences(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily care reminders", NotificationManager.IMPORTANCE_HIGH))
        val now = System.currentTimeMillis()
        for (kind in listOf(Kind.MEDICATION, Kind.PRESSURE)) {
            settings.times(kind).forEachIndexed { index, time ->
                val pending = alarm(context, "${kind.name}:$index", if (kind == Kind.MEDICATION) "Time for medication" else "Time to check blood pressure",
                    if (kind == Kind.MEDICATION) "Open MedicTimer and confirm after taking your medication." else "Take a reading, then open MedicTimer to record it.")
                manager.cancel(pending)
                if (settings.enabled) schedule(context, nextDaily(time, now), pending)
            }
        }
        Store(context).use { store ->
            val entries = store.all()
            entries.filter { it.kind == Kind.MEAL }.forEach { cancelMeal(context, it.id, dismiss = false) }
            if (settings.enabled) pendingMeals(entries).filter { !it.alerted && now - glucoseDue(it.time) < 24 * 60 * 60 * 1000L }.forEach { meal ->
                schedule(context, maxOf(glucoseDue(meal.time), now + 1000), alarm(context, "meal:${meal.id}",
                    "Time to check blood sugar", "It has been two hours since your ${meal.label.lowercase()}. Record your reading in mmol/L.", meal.id))
            }
        }
    }

    // Lint cannot follow notificationsAllowed's SDK-dependent check. The explicit check and
    // SecurityException handler below cover Android 13+ permission denial and revocation.
    @SuppressLint("MissingPermission", "NotificationPermission")
    fun notify(context: Context, key: String, title: String, body: String, mealId: Long) {
        if (!Preferences(context).enabled || !notificationsAllowed(context)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (key.startsWith("MEDICATION:")) {
            val slot = key.substringAfter(':').toIntOrNull() ?: return
            val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val taken = Store(context).use { store -> store.all().any { it.kind == Kind.MEDICATION && it.label == "Dose ${slot + 1}" && it.time >= dayStart } }
            if (taken) return
        }
        if (mealId != 0L) {
            val eligible = Store(context).use { store -> pendingMeals(store.all()).any { it.id == mealId && !it.alerted } }
            if (!eligible) return
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(nl.medictimer.R.drawable.ic_medictimer)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            NotificationManagerCompat.from(context).notify(key, 1, notification)
            if (mealId != 0L) Store(context).use { it.markAlerted(mealId) }
        } catch (_: SecurityException) { /* User revoked notification access. */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notify(context, intent.action ?: "care", intent.getStringExtra("title") ?: "MedicTimer",
            intent.getStringExtra("body") ?: "Open your daily care plan.", intent.getLongExtra("meal", 0))
        Reminders.refresh(context)
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) Reminders.refresh(context)
    }
}
