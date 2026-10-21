package nl.medictimer

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

const val MAX_DAILY_TIMES = 12

enum class Meal(val title: String, val usualTime: LocalTime) {
    BREAKFAST("Breakfast", LocalTime.of(8, 0)),
    LUNCH("Lunch", LocalTime.of(13, 0)),
    DINNER("Dinner", LocalTime.of(18, 0));
}

data class MedicationSlot(
    val id: String,
    val time: LocalTime,
    val meal: Meal? = null,
) {
    fun at(routine: Routine): LocalTime = meal?.let { routine.mealTimes[it.ordinal] } ?: time
    fun title(index: Int): String = meal?.let { "With ${it.title.lowercase()}" } ?: "Dose ${index + 1}"
}

data class Routine(
    val mealTimes: List<LocalTime> = Meal.entries.map { it.usualTime },
    val medications: List<MedicationSlot> = listOf("08:00", "13:00", "20:00").mapIndexed { i, time -> MedicationSlot("dose-${i + 1}", LocalTime.parse(time)) },
    val pressureTimes: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
    val glucoseEnabled: Boolean = true,
) {
    fun toJson(): String = JSONObject().apply {
        put("meals", JSONArray(mealTimes.map { it.toString() }))
        put("medications", JSONArray().apply {
            medications.forEach { slot -> put(JSONObject().apply {
                put("id", slot.id); put("time", slot.time.toString()); put("meal", slot.meal?.name ?: "")
            }) }
        })
        put("pressure", JSONArray(pressureTimes.map { it.toString() }))
        put("glucose", glucoseEnabled)
    }.toString()

    companion object {
        fun fromJson(json: String): Routine {
            val value = JSONObject(json)
            val meals = value.getJSONArray("meals")
            val medication = value.getJSONArray("medications")
            val pressure = value.getJSONArray("pressure")
            require(meals.length() == Meal.entries.size)
            require(medication.length() in 0..MAX_DAILY_TIMES && pressure.length() in 0..MAX_DAILY_TIMES)
            val slots = (0 until medication.length()).map { i ->
                val slot = medication.getJSONObject(i)
                MedicationSlot(slot.getString("id"), LocalTime.parse(slot.getString("time")),
                    slot.getString("meal").takeIf { it.isNotEmpty() }?.let { Meal.valueOf(it) })
            }
            require(slots.all { it.id.isNotBlank() } && slots.map { it.id }.distinct().size == slots.size)
            return Routine(
                mealTimes = (0 until meals.length()).map { LocalTime.parse(meals.getString(it)) },
                medications = slots,
                pressureTimes = (0 until pressure.length()).map { LocalTime.parse(pressure.getString(it)) },
                glucoseEnabled = value.getBoolean("glucose"),
            )
        }
    }
}
