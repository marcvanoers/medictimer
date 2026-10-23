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
    internal const val CHANNEL = "daily-care"
    private fun alarm(context: Context, key: String, title: String = "", body: String = "", meal: Long = 0): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = key
            putExtra("title", title); putExtra("body", body); putExtra("meal", meal)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun notificationAccessAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun notificationsAllowed(context: Context): Boolean = notificationAccessAllowed(context) &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily care reminders", NotificationManager.IMPORTANCE_HIGH))
    }

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
        val routine = settings.routine()
        val manager = context.getSystemService(AlarmManager::class.java)
        ensureChannel(context)
        // Some phone Settings apps do not return an activity result. Preserve the user's
        // enable request and reconcile actual permission when the app resumes.
        if (settings.enableRequested && notificationsAllowed(context)) settings.enabled = true
        val now = System.currentTimeMillis()
        val previous = settings.scheduledKeys
        previous.forEach { manager.cancel(alarm(context, it)) }
        // The original app had no registry of its five fixed daily alarms.
        repeat(3) { manager.cancel(alarm(context, "MEDICATION:$it")) }
        repeat(2) { manager.cancel(alarm(context, "PRESSURE:$it")) }
        val scheduled = mutableSetOf<String>()
        if (settings.enabled) {
            routine.medications.forEachIndexed { index, slot ->
                val key = "MEDICATION:${slot.id}"
                schedule(context, nextDaily(slot.at(routine), now), alarm(context, key, "Time for medication",
                    "${slot.title(index)}. Open MedicTimer and confirm after taking your medication."))
                scheduled.add(key)
            }
            routine.pressureTimes.forEachIndexed { index, time ->
                val key = "PRESSURE:$index"
                schedule(context, nextDaily(time, now), alarm(context, key, "Time to check blood pressure",
                    "Take a reading, then open MedicTimer to record it."))
                scheduled.add(key)
            }
            if (routine.glucoseEnabled) Meal.entries.forEach { meal ->
                val key = "MEAL:${meal.name}"
                schedule(context, nextDaily(routine.mealTimes[meal.ordinal], now), alarm(context, key,
                    "Have you eaten ${meal.title.lowercase()}?", "Log your actual meal time in MedicTimer. Blood sugar is due two hours after eating."))
                scheduled.add(key)
            }
        }
        previous.filter { it !in scheduled }.forEach { NotificationManagerCompat.from(context).cancel(it, 1) }
        settings.scheduledKeys = scheduled
        Store(context).use { store ->
            val entries = store.all()
            entries.filter { it.kind == Kind.MEAL }.forEach { cancelMeal(context, it.id, dismiss = false) }
            if (!settings.enabled || !routine.glucoseEnabled) entries.filter { it.kind == Kind.MEAL }.forEach { cancelMeal(context, it.id) }
            val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            entries.filter { it.kind == Kind.MEAL && it.time >= todayStart }.forEach { entry ->
                Meal.entries.firstOrNull { it.title == entry.label }?.let { NotificationManagerCompat.from(context).cancel("MEAL:${it.name}", 1) }
            }
            if (settings.enabled && routine.glucoseEnabled) pendingMeals(entries).filter { !it.alerted && now - glucoseDue(it.time) < 24 * 60 * 60 * 1000L }.forEach { meal ->
                schedule(context, maxOf(glucoseDue(meal.time), now + 1000), alarm(context, "meal:${meal.id}",
                    "Time to check blood sugar", "It has been two hours since your ${meal.label.lowercase()}. Record your reading in mmol/L.", meal.id))
            }
        }
    }

    // Lint cannot follow notificationsAllowed's SDK-dependent check. The explicit check and
    // SecurityException handler below cover Android 13+ permission denial and revocation.
    @SuppressLint("MissingPermission", "NotificationPermission")
    fun notify(context: Context, key: String, title: String, body: String, mealId: Long): Boolean {
        if (!Preferences(context).enabled || !notificationsAllowed(context)) return false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val routine = Preferences(context).routine()
        if (key.startsWith("MEDICATION:")) {
            val slotId = key.substringAfter(':')
            if (routine.medications.none { it.id == slotId }) return false
            val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val taken = Store(context).use { store -> store.all().any { it.kind == Kind.MEDICATION && it.reminderKey == slotId && it.time >= dayStart } }
            if (taken) return false
        }
        if (key.startsWith("PRESSURE:") && key.substringAfter(':').toIntOrNull() !in routine.pressureTimes.indices) return false
        if (key.startsWith("MEAL:")) {
            if (!routine.glucoseEnabled) return false
            val meal = Meal.entries.firstOrNull { it.name == key.substringAfter(':') } ?: return false
            val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (Store(context).use { store -> store.all().any { it.kind == Kind.MEAL && it.label == meal.title && it.time >= dayStart } }) return false
        }
        if (mealId != 0L) {
            if (!routine.glucoseEnabled) return false
            val eligible = Store(context).use { store -> pendingMeals(store.all()).any { it.id == mealId && !it.alerted } }
            if (!eligible) return false
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(nl.medictimer.R.drawable.ic_medictimer)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            NotificationManagerCompat.from(context).notify(key, 1, notification)
            if (mealId != 0L) Store(context).use { it.markAlerted(mealId) }
            return true
        } catch (_: SecurityException) { return false }
    }

    fun testNotification(context: Context): Boolean {
        ensureChannel(context)
        return notify(context, "TEST", "MedicTimer test notification",
            "Notifications are working. Sound and vibration follow your Android notification settings.", 0)
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
