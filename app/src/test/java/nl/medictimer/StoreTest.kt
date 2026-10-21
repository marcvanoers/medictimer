package nl.medictimer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clean() { context.deleteDatabase("medictimer.db") }

    @Test fun `records survive closing and reopening with timestamps and decimals intact`() {
        val meal: Long
        Store(context).use { store ->
            meal = store.save(Entry(kind = Kind.MEAL, time = 1000, label = "Breakfast"))
            store.save(Entry(kind = Kind.GLUCOSE, time = 7201000, first = 6.4, mealId = meal))
            store.save(Entry(kind = Kind.PRESSURE, time = 8000000, first = 122.0, second = 81.0, pulse = 64))
        }
        Store(context).use { store ->
            val all = store.all()
            assertEquals(3, all.size)
            assertEquals(64, all.first().pulse)
            assertEquals(6.4, all[1].first!!, 0.001)
            assertEquals(meal, all[1].mealId)
            assertEquals(7201000L, all[1].time)
            assertTrue(pendingMeals(all).isEmpty())
        }
    }

    @Test fun `editing preserves identity and deleting meal preserves its reading`() {
        Store(context).use { store ->
            val meal = store.save(Entry(kind = Kind.MEAL, time = 1000, label = "Breakfast"))
            val reading = store.save(Entry(kind = Kind.GLUCOSE, time = 7201000, first = 6.4, mealId = meal))
            store.save(store.all().first { it.id == reading }.copy(first = 6.2))
            assertEquals(2, store.all().size)
            store.delete(meal)
            val remaining = store.all().single()
            assertEquals(reading, remaining.id)
            assertEquals(6.2, remaining.first!!, 0.001)
            assertNull(remaining.mealId)
        }
    }
}
