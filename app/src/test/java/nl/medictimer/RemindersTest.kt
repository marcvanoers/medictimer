package nl.medictimer

import android.Manifest
import android.app.Application
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.NotificationChannel
import android.provider.Settings
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clean() {
        context.deleteDatabase("medictimer.db")
        context.getSharedPreferences("schedule", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun `meal alarms complete independently and disabling reminders cancels all future alarms`() {
        Preferences(context).enabled = true
        val meal = Store(context).use { it.save(Entry(kind = Kind.MEAL, label = "Breakfast")) }
        Reminders.refresh(context)
        val manager = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(9, manager.scheduledAlarms.size)
        Store(context).use { it.save(Entry(kind = Kind.GLUCOSE, first = 6.4, mealId = meal)) }
        Reminders.refresh(context)
        assertEquals(8, manager.scheduledAlarms.size)
        Preferences(context).enabled = false
        Reminders.refresh(context)
        assertTrue(manager.scheduledAlarms.isEmpty())
    }
    @Test fun `delivering a meal reminder does not erase its notification or schedule a duplicate`() {
        Preferences(context).enabled = true
        val meal = Store(context).use { it.save(Entry(kind = Kind.MEAL, label = "Breakfast", time = System.currentTimeMillis() - 7200000L)) }
        Reminders.refresh(context)
        Reminders.notify(context, "meal:$meal", "Blood sugar", "Record a reading", meal)
        Reminders.refresh(context)
        assertEquals(1, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
        assertEquals(8, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        assertTrue(Store(context).use { it.all().single().alerted })
    }
    @Test
    @Config(sdk = [34])
    fun `denied notification permission prevents posting on Android 13 and later`() {
        Preferences(context).enabled = true
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(Reminders.notificationsAllowed(context))
        Reminders.refresh(context)
        Reminders.notify(context, "PRESSURE:0", "Blood pressure", "Record a reading", 0)
        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }

    @Test fun `changing routine removes old daily and glucose alarms`() {
        val settings = Preferences(context)
        settings.enabled = true
        Store(context).use { it.save(Entry(kind = Kind.MEAL, label = "Breakfast")) }
        Reminders.refresh(context)
        settings.saveRoutine(Routine(medications = emptyList(), pressureTimes = listOf(java.time.LocalTime.NOON), glucoseEnabled = false))
        Reminders.refresh(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals("PRESSURE:0", shadowOf(alarms.single().operation).savedIntent.action)
        // Even a previously delivered PendingIntent cannot post for an opted-out feature.
        Reminders.notify(context, "MEDICATION:dose-1", "Medication", "Old dose", 0)
        Reminders.notify(context, "MEAL:BREAKFAST", "Meal", "Old meal", 0)
        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }

    @Test fun `blood sugar uses actual meal time and follows no blood pressure schedule`() {
        val settings = Preferences(context)
        settings.enabled = true
        settings.saveRoutine(Routine(medications = emptyList(), pressureTimes = emptyList()))
        val actual = System.currentTimeMillis() - 15 * 60 * 1000L
        val meal = Store(context).use { it.save(Entry(kind = Kind.MEAL, label = "Lunch", time = actual)) }
        Reminders.refresh(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        val glucose = alarms.single { shadowOf(it.operation).savedIntent.action == "meal:$meal" }
        assertEquals(glucoseDue(actual), glucose.triggerAtTime)
        assertTrue(alarms.none { shadowOf(it.operation).savedIntent.action!!.startsWith("PRESSURE:") })
    }

    @Test fun `medication tied to lunch follows a changed lunch time`() {
        val settings = Preferences(context)
        settings.enabled = true
        val lunch = java.time.LocalTime.of(14, 15)
        val routine = Routine(mealTimes = listOf(Meal.BREAKFAST.usualTime, lunch, Meal.DINNER.usualTime),
            medications = listOf(MedicationSlot("lunch-only", java.time.LocalTime.MIDNIGHT, Meal.LUNCH)), pressureTimes = emptyList(), glucoseEnabled = false)
        settings.saveRoutine(routine)
        Reminders.refresh(context)
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals("MEDICATION:lunch-only", shadowOf(alarm.operation).savedIntent.action)
        assertEquals(nextDaily(lunch, System.currentTimeMillis()), alarm.triggerAtTime)
    }

    @Test fun `blocked channel opens its own OS controls when app notification access is allowed`() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Reminders.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_NONE))
        assertFalse(Reminders.notificationsAllowed(context))
        val intent = notificationSettingsIntent(context)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(Reminders.CHANNEL, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test @Config(sdk = [34])
    fun `app-wide permission must be granted before a blocked channel can be enabled`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Reminders.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_NONE))
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notificationSettingsIntent(context).action)
    }
}
