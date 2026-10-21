package nl.medictimer

import android.Manifest
import android.app.Application
import android.app.AlarmManager
import android.app.NotificationManager
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
        assertEquals(6, manager.scheduledAlarms.size)
        Store(context).use { it.save(Entry(kind = Kind.GLUCOSE, first = 6.4, mealId = meal)) }
        Reminders.refresh(context)
        assertEquals(5, manager.scheduledAlarms.size)
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
        assertEquals(5, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
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
}
