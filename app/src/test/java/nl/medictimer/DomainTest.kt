package nl.medictimer

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class DomainTest {
    @Test fun `meal reminder is exactly two hours after logging including midnight`() {
        val logged = Instant.parse("2026-10-21T23:15:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-22T01:15:00Z").toEpochMilli(), glucoseDue(logged))
    }
    @Test fun `daily reminders roll forward after their scheduled time`() {
        val now = Instant.parse("2026-10-21T08:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-22T08:00:00Z").toEpochMilli(), nextDaily(LocalTime.of(8, 0), now, ZoneId.of("UTC")))
    }
    @Test fun `daily reminders preserve local time across daylight saving change`() {
        val now = Instant.parse("2026-10-24T18:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-25T07:00:00Z").toEpochMilli(), nextDaily(LocalTime.of(8, 0), now, ZoneId.of("Europe/Amsterdam")))
    }
    @Test fun `comma decimal readings work and invalid inputs are rejected`() {
        assertEquals(6.4, parseReading("6,4")!!, 0.001)
        assertNull(readingError(Kind.GLUCOSE, "6,4", "", ""))
        for (input in listOf("", "NaN", "Infinity", "-1", "61", "hello")) assertNotNull(readingError(Kind.GLUCOSE, input, "", ""))
    }
    @Test fun `pressure guards check upper lower order whole numbers and pulse`() {
        assertNull(readingError(Kind.PRESSURE, "120", "80", ""))
        assertNull(readingError(Kind.PRESSURE, "120", "80", "65"))
        assertNotNull(readingError(Kind.PRESSURE, "80", "120", "65"))
        assertNotNull(readingError(Kind.PRESSURE, "120.5", "80", "65"))
        assertNotNull(readingError(Kind.PRESSURE, "120", "80", "abc"))
    }
    @Test fun `only explicitly linked glucose readings complete a meal`() {
        val breakfast = Entry(id = 1, kind = Kind.MEAL, time = 1000)
        val lunch = Entry(id = 2, kind = Kind.MEAL, time = 2000)
        val unrelated = Entry(id = 3, kind = Kind.GLUCOSE, first = 6.4)
        val linked = Entry(id = 4, kind = Kind.GLUCOSE, first = 6.1, mealId = 1)
        assertEquals(listOf(breakfast, lunch), pendingMeals(listOf(lunch, unrelated, breakfast)))
        assertEquals(listOf(lunch), pendingMeals(listOf(lunch, unrelated, breakfast, linked)))
    }
}
