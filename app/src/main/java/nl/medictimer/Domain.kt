package nl.medictimer

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class Kind(val title: String) {
    MEAL("Meal"), GLUCOSE("Blood sugar"), PRESSURE("Blood pressure"), MEDICATION("Medication")
}

data class Entry(
    val id: Long = 0,
    val kind: Kind,
    val time: Long = System.currentTimeMillis(),
    val first: Double? = null,
    val second: Double? = null,
    val pulse: Int? = null,
    val label: String = "",
    val mealId: Long? = null,
    val alerted: Boolean = false,
) {
    fun value(): String = when (kind) {
        Kind.GLUCOSE -> "${decimal(first!!)} mmol/L"
        Kind.PRESSURE -> "${first!!.toInt()}/${second!!.toInt()} mmHg" + (pulse?.let { " · $it bpm" } ?: "")
        Kind.MEAL -> label.ifBlank { "Meal logged" }
        Kind.MEDICATION -> "$label · taken"
    }
}

fun decimal(value: Double): String = String.format(java.util.Locale.getDefault(), "%.1f", value)
fun parseReading(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Broad input guards, not clinical target ranges or medical advice. */
fun readingError(kind: Kind, first: String, second: String, pulse: String): String? {
    val a = parseReading(first)
    if (kind == Kind.GLUCOSE) {
        return if (a == null || a !in 0.1..60.0) "Enter a blood sugar reading from 0.1 to 60.0 mmol/L." else null
    }
    val b = parseReading(second)
    if (a == null || b == null || a % 1.0 != 0.0 || b % 1.0 != 0.0 || a !in 30.0..300.0 || b !in 20.0..200.0)
        return "Enter whole numbers: upper 30–300 and lower 20–200 mmHg."
    if (a <= b) return "The upper reading must be higher than the lower reading."
    if (pulse.isNotBlank() && (pulse.toIntOrNull()?.let { it in 20..250 } != true))
        return "Enter a pulse from 20 to 250, or leave it empty."
    return null
}

fun glucoseDue(mealTime: Long): Long = mealTime + 2 * 60 * 60 * 1000L

fun nextDaily(time: LocalTime, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val current = Instant.ofEpochMilli(now).atZone(zone)
    val candidate = current.toLocalDate().atTime(time).atZone(zone)
    return (if (candidate.toInstant().toEpochMilli() > now) candidate else current.toLocalDate().plusDays(1).atTime(time).atZone(zone))
        .toInstant().toEpochMilli()
}

fun pendingMeals(entries: List<Entry>): List<Entry> {
    val recorded = entries.filter { it.kind == Kind.GLUCOSE }.mapNotNull { it.mealId }.toSet()
    return entries.filter { it.kind == Kind.MEAL && it.id !in recorded }.sortedBy { it.time }
}
