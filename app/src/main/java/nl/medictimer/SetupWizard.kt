package nl.medictimer

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.util.UUID

private val RoutineSaver = Saver<Routine, String>(save = { it.toJson() }, restore = { Routine.fromJson(it) })

@Composable
internal fun SetupWizard(initial: Routine, firstUse: Boolean, onCancel: () -> Unit, onFinish: (Routine, Boolean?) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var draft by rememberSaveable(stateSaver = RoutineSaver) { mutableStateOf(initial) }
    var medicationEnabled by rememberSaveable { mutableStateOf(initial.medications.isNotEmpty()) }
    var pressureEnabled by rememberSaveable { mutableStateOf(initial.pressureTimes.isNotEmpty()) }
    var withMeals by rememberSaveable { mutableStateOf(initial.medications.any { it.meal != null }) }
    val effective = draft.copy(medications = if (medicationEnabled) draft.medications else emptyList(),
        pressureTimes = if (pressureEnabled) draft.pressureTimes else emptyList())
    val valid = !medicationEnabled || draft.medications.isNotEmpty()
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    BackHandler(enabled = step > 0 || !firstUse) { if (step > 0) step-- else onCancel() }

    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = WarmWhite) {
        Column {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (firstUse) "Welcome to MedicTimer" else "Edit your routine", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Green)
                Text("Step ${step + 1} of 6", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { (step + 1) / 6f }, modifier = Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (step) {
                    0 -> {
                        Text("Let’s set up your day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("We’ll ask about meals, medication, blood pressure, and blood sugar. Choose only what you need.")
                        Text("You can do this together with a family member. Everything can be changed later in Settings.")
                        Text("Use your existing medication and measurement routine. Your saved records will be kept.")
                    }
                    1 -> {
                        Text("When do you usually eat?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("If you track blood sugar, these times remind you to log your meals. The actual logged meal starts the two-hour timer.")
                        Meal.entries.forEach { meal -> TimeButton(meal.title, draft.mealTimes[meal.ordinal]) { time ->
                            draft = draft.copy(mealTimes = draft.mealTimes.toMutableList().apply { set(meal.ordinal, time) })
                        } }
                    }
                    2 -> {
                        Text("When do you take medication?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        RoutineToggle("I take medication", medicationEnabled) {
                            medicationEnabled = it
                            if (it && draft.medications.isEmpty()) draft = draft.copy(medications = listOf(MedicationSlot("dose-${UUID.randomUUID()}", LocalTime.of(8, 0))))
                        }
                        if (medicationEnabled) {
                            Column {
                                FilterChip(withMeals, onClick = {
                                    if (!withMeals) draft = draft.copy(medications = Meal.entries.map { meal -> MedicationSlot("with-${meal.name}", meal.usualTime, meal) })
                                    withMeals = true
                                }, label = { Text("With meals") })
                                FilterChip(!withMeals, onClick = {
                                    draft = draft.copy(medications = draft.medications.map { it.copy(time = it.at(draft), meal = null) }.ifEmpty { listOf(MedicationSlot("dose-${UUID.randomUUID()}", LocalTime.of(8, 0))) })
                                    withMeals = false
                                }, label = { Text("At specific times") })
                            }
                            if (withMeals) {
                                Text("Select the meals when you take pills. You can choose just lunch, just dinner, or any combination.")
                                Meal.entries.forEach { meal ->
                                    val selected = draft.medications.any { it.meal == meal }
                                    MealChoice(meal, draft.mealTimes[meal.ordinal], selected) { checked ->
                                        draft = draft.copy(medications = if (checked) (draft.medications + MedicationSlot("with-${meal.name}", meal.usualTime, meal)).sortedBy { it.meal!!.ordinal }
                                            else draft.medications.filterNot { it.meal == meal })
                                    }
                                }
                                if (!valid) Text("Choose at least one meal, or switch medication off.", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("${draft.medications.size} medication reminders each day")
                                draft.medications.forEachIndexed { index, slot ->
                                    TimeRow("Dose ${index + 1}", slot.time, draft.medications.size > 1, onTime = { time ->
                                        draft = draft.copy(medications = draft.medications.map { if (it.id == slot.id) it.copy(time = time) else it })
                                    }, onRemove = { draft = draft.copy(medications = draft.medications.filterNot { it.id == slot.id }) })
                                }
                                if (draft.medications.size < MAX_DAILY_TIMES) OutlinedButton(onClick = {
                                    draft = draft.copy(medications = draft.medications + MedicationSlot("dose-${UUID.randomUUID()}", additionalTime(draft.medications.map { it.time })))
                                }) { Text("Add medication time") }
                            }
                            Text("The app records a dose only when you confirm you have taken it.")
                        }
                    }
                    3 -> {
                        Text("Do you check blood pressure?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        RoutineToggle("I measure blood pressure", pressureEnabled) {
                            pressureEnabled = it
                            if (it && draft.pressureTimes.isEmpty()) draft = draft.copy(pressureTimes = listOf(LocalTime.of(8, 0)))
                        }
                        if (pressureEnabled) {
                            Text("${draft.pressureTimes.size} readings each day. Add or remove times to match your routine.")
                            draft.pressureTimes.forEachIndexed { index, time ->
                                TimeRow("Reading ${index + 1}", time, draft.pressureTimes.size > 1, onTime = { updated ->
                                    draft = draft.copy(pressureTimes = draft.pressureTimes.toMutableList().apply { set(index, updated) })
                                }, onRemove = { draft = draft.copy(pressureTimes = draft.pressureTimes.filterIndexed { i, _ -> i != index }) })
                            }
                            if (draft.pressureTimes.size < MAX_DAILY_TIMES) OutlinedButton(onClick = {
                                draft = draft.copy(pressureTimes = draft.pressureTimes + additionalTime(draft.pressureTimes))
                            }) { Text("Add blood pressure time") }
                        }
                    }
                    4 -> {
                        Text("Do you check blood sugar?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        RoutineToggle("I measure blood sugar", draft.glucoseEnabled) { draft = draft.copy(glucoseEnabled = it) }
                        if (draft.glucoseEnabled) {
                            Text("After eating, tap “Log a meal”. A blood sugar reminder is automatically scheduled exactly two hours after the recorded meal time.")
                            Text("The two-hour interval is fixed. It follows when you actually eat, even if that is earlier or later than usual.")
                            Text("Readings are recorded in mmol/L. Blood sugar tracking is independent of blood pressure tracking.")
                        }
                    }
                    5 -> {
                        Text("Your routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        RoutineSummary(effective)
                        if (firstUse) Text("Next, Android’s notification settings will open. Allow notifications and return here. If needed, we’ll then open Alarms & reminders for precise timing.")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider()
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (step < 5) BigButton(if (step == 0) "Get started" else "Next", enabled = step != 2 || valid, onClick = { step++ })
                else {
                    BigButton(if (firstUse) "Save and enable reminders" else "Save changes", enabled = valid, onClick = { onFinish(effective, if (firstUse) true else null) })
                    if (firstUse) TextButton(onClick = { onFinish(effective, false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save without reminders") }
                }
                if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back") }
                else if (!firstUse) TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
            }
        }
    }
}

@Composable
internal fun RoutineSummary(routine: Routine) {
    CareCard("Usual meal times", if (routine.glucoseEnabled) "A prompt to log each meal. Your actual meal time starts the blood sugar timer." else "Used for any medication linked to meals.") {
        Meal.entries.forEach { Text("${it.title} · ${routine.mealTimes[it.ordinal]}") }
    }
    CareCard("Medication", if (routine.medications.isEmpty()) "No medication reminders" else "${routine.medications.size} reminders each day") {
        routine.medications.forEachIndexed { index, slot -> Text("${slot.title(index)} · ${slot.at(routine)}") }
    }
    CareCard("Blood pressure", if (routine.pressureTimes.isEmpty()) "Not part of your routine" else "${routine.pressureTimes.size} readings each day") {
        routine.pressureTimes.forEach { Text(it.toString()) }
    }
    CareCard("Blood sugar", if (routine.glucoseEnabled) "Two hours after each logged meal · mmol/L" else "Not part of your routine") {}
}

@Composable
private fun RoutineToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun MealChoice(meal: Meal, time: LocalTime, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text("${meal.title} · $time")
    }
}

@Composable
private fun TimeButton(label: String, time: LocalTime, modifier: Modifier = Modifier, onTime: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> onTime(LocalTime.of(hour, minute)) }, time.hour, time.minute, true).show() },
        modifier = modifier.fillMaxWidth().heightIn(min = 62.dp)) { Text("$label · $time") }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, removable: Boolean, onTime: (LocalTime) -> Unit, onRemove: () -> Unit) {
    Column {
        TimeButton(label, time, onTime = onTime)
        if (removable) TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).semantics { contentDescription = "Remove $label" }) { Text("Remove") }
    }
}

private fun additionalTime(existing: List<LocalTime>): LocalTime =
    listOf(8, 12, 18, 20, 6, 10, 14, 16, 22, 7, 9, 11, 13).map { LocalTime.of(it, 0) }.first { it !in existing }
