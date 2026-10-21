package nl.medictimer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutineMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clean() {
        context.deleteDatabase("medictimer.db")
        context.getSharedPreferences("schedule", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `legacy reminder times and enablement survive opening and completing setup`() {
        context.getSharedPreferences("schedule", Context.MODE_PRIVATE).edit()
            .putString("MEDICATION0", "07:15").putString("MEDICATION2", "21:30")
            .putString("PRESSURE0", "09:45").putBoolean("enabled", true).commit()
        val preferences = Preferences(context)
        val original = preferences.routine()
        assertEquals(LocalTime.of(7, 15), original.medications[0].time)
        assertEquals(LocalTime.of(21, 30), original.medications[2].time)
        assertEquals(LocalTime.of(9, 45), original.pressureTimes[0])
        assertFalse(preferences.setupComplete)
        preferences.saveRoutine(original)
        val reopened = Preferences(context)
        assertTrue(reopened.setupComplete)
        assertTrue(reopened.enabled)
        assertEquals(original, reopened.routine())
    }

    @Test fun `roundtrip retains selected meal slots optional features and stable dose identifiers`() {
        val routine = Routine(medications = listOf(MedicationSlot("dinner-dose", LocalTime.NOON, Meal.DINNER)),
            pressureTimes = emptyList(), glucoseEnabled = true)
        Preferences(context).saveRoutine(routine)
        assertEquals(routine, Preferences(context).routine())
        assertEquals(routine.mealTimes[Meal.DINNER.ordinal], Preferences(context).routine().medications.single().at(routine))
        Preferences(context).saveRoutine(routine.copy(glucoseEnabled = false))
        assertTrue(Preferences(context).routine().pressureTimes.isEmpty())
        assertFalse(Preferences(context).routine().glucoseEnabled)
    }

    @Test fun `v01 database upgrade preserves readings timestamps and medication confirmations`() {
        val file = context.getDatabasePath("medictimer.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("""CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, timestamp INTEGER NOT NULL,
                first REAL, second REAL, pulse INTEGER, label TEXT NOT NULL,
                meal_id INTEGER, alerted INTEGER NOT NULL DEFAULT 0)""")
            db.execSQL("INSERT INTO entries(kind,timestamp,first,label) VALUES ('GLUCOSE',123456,6.4,'')")
            db.execSQL("INSERT INTO entries(kind,timestamp,label) VALUES ('MEDICATION',123457,'Dose 1')")
            db.version = 1
        }
        Store(context).use { store ->
            val all = store.all()
            assertEquals(2, all.size)
            assertEquals("dose-1", all.first().reminderKey)
            assertEquals(6.4, all.last().first!!, 0.001)
            assertEquals(123456L, all.last().time)
            val settings = Preferences(context)
            settings.saveRoutine(settings.routine().copy(pressureTimes = emptyList(), glucoseEnabled = false))
            assertEquals(all, store.all())
        }
    }
}
