package nl.medictimer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val Green = Color(0xFF165C4B)
internal val WarmWhite = Color(0xFFF7F8F3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = WarmWhite, surface = Color.White),
                typography = Typography(bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 25.sp),
                    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold))) {
                MedicTimer()
            }
        }
    }
}

@Composable
private fun MedicTimer() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val settings = remember { Preferences(context) }
    var entries by remember { mutableStateOf(store.all()) }
    var showSetup by rememberSaveable { mutableStateOf(!settings.setupComplete) }
    var screen by rememberSaveable { mutableStateOf("Today") }
    var add by remember { mutableStateOf<Kind?>(null) }
    var edit by remember { mutableStateOf<Entry?>(null) }
    var delete by remember { mutableStateOf<Entry?>(null) }
    var mealPicker by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableIntStateOf(7) }
    var exportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var exportPeriod by rememberSaveable { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val routine = remember(revision) { settings.routine() }
    val lifecycle = LocalLifecycleOwner.current
    fun refresh() { entries = store.all(); Reminders.refresh(context); revision++ }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); store.close() }
    }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(60_000); revision++ }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val selectedIds = exportIds.toSet()
            val reportEntries = store.all().filter { it.id in selectedIds }
            exporting = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { Reports.write(context, uri, reportEntries, exportPeriod) }
                    message = "PDF saved. You can share it with your doctor from your Files app."
                } catch (_: Exception) { message = "The PDF could not be saved. Please choose a writable location and try again." }
                finally { exporting = false }
            }
        }
    }
    val enableReminders = rememberEnableReminders(settings, onChanged = { refresh() }, onMessage = { message = it })
    if (showSetup) {
        val firstUse = !settings.setupComplete
        SetupWizard(routine, firstUse, onCancel = { showSetup = false }, onFinish = { updated, enable ->
            settings.saveRoutine(updated)
            if (enable == false) settings.enabled = false
            showSetup = false
            screen = if (firstUse) "Today" else "Settings"
            refresh()
            if (enable == true) enableReminders()
        })
        return
    }
    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = WarmWhite) {
        Column {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("MedicTimer", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Green)
                Text(if (screen == "Today") stamp(System.currentTimeMillis(), "EEEE, d MMMM") else if (screen == "History") "Your readings & records" else "Your daily routine", color = Color(0xFF45564F))
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (screen) {
                    "Today" -> {
                        val now = System.currentTimeMillis() + revision * 0L
                        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val today = entries.filter { it.time >= start }
                        if (!settings.enabled || !Reminders.notificationsAllowed(context)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Reminders are off", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { screen = "Settings" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set up") }
                            }
                        } else if (!Reminders.exactAllowed(context)) {
                            Text("Reminders may be delayed. Enable precise reminders in Settings.", color = Color(0xFF8A3E17))
                        }
                        Text("Today’s care", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val pending = if (routine.glucoseEnabled) pendingMeals(entries).filter { now - it.time < 24 * 60 * 60 * 1000L } else emptyList()
                        if (routine.glucoseEnabled) {
                            CareCard("I’ve eaten", "Log when you actually eat. Blood sugar is due two hours later.") {
                                BigButton("Log a meal", onClick = { mealPicker = true })
                            }
                            pending.forEach { meal ->
                                val due = glucoseDue(meal.time)
                                CareCard(if (due <= now) "Blood sugar is due" else "Blood sugar reminder", "${meal.label} at ${stamp(meal.time, "HH:mm")} · check at ${stamp(due, "HH:mm")}") {
                                    if (due <= now) BigButton("Record blood sugar", onClick = { edit = Entry(kind = Kind.GLUCOSE, mealId = meal.id); add = Kind.GLUCOSE })
                                }
                            }
                            CareCard("Blood sugar", "${today.count { it.kind == Kind.GLUCOSE }} recorded today · mmol/L") {
                                BigButton("Record blood sugar", onClick = { edit = Entry(kind = Kind.GLUCOSE, mealId = pending.lastOrNull { glucoseDue(it.time) <= now }?.id); add = Kind.GLUCOSE })
                            }
                        }
                        if (routine.pressureTimes.isNotEmpty()) CareCard("Blood pressure", "${today.count { it.kind == Kind.PRESSURE }} of ${routine.pressureTimes.size} daily readings recorded") {
                            BigButton("Record blood pressure", onClick = { edit = null; add = Kind.PRESSURE })
                        }
                        if (routine.medications.isNotEmpty()) CareCard("Medication", "Confirm each dose after taking it.") {
                            routine.medications.forEachIndexed { index, slot ->
                                val label = slot.title(index)
                                val time = slot.at(routine)
                                val taken = today.any { it.kind == Kind.MEDICATION && it.reminderKey == slot.id }
                                BigButton(if (taken) "✓ $label ($time) taken" else "Confirm $label ($time) taken", enabled = !taken, onClick = {
                                    edit = Entry(kind = Kind.MEDICATION, label = label, reminderKey = slot.id); add = Kind.MEDICATION
                                })
                            }
                        }
                        if (!routine.glucoseEnabled && routine.pressureTimes.isEmpty() && routine.medications.isEmpty()) {
                            Text("No daily tasks selected. Add the care you need in Settings.")
                            BigButton("Edit daily routine", onClick = { showSetup = true })
                        }
                        Text("Saved on this phone. Export records regularly to keep a copy.", style = MaterialTheme.typography.bodyMedium)
                    }
                    "History" -> {
                        val cutoff = if (period == 0) 0L else LocalDate.now().minusDays(period.toLong() - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val selected = entries.filter { it.time >= cutoff }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 30, 0).forEach { days -> FilterChip(selected = period == days, onClick = { period = days }, label = { Text(if (days == 0) "All time" else "$days days") }) }
                        }
                        BigButton(if (exporting) "Saving PDF…" else "Export PDF for doctor", enabled = selected.isNotEmpty() && !exporting, onClick = {
                            exportIds = selected.map { it.id }.toLongArray()
                            exportPeriod = if (period == 0) "All records through ${stamp(System.currentTimeMillis(), "dd MMM yyyy")}" else "${stamp(cutoff, "dd MMM yyyy")} – ${stamp(System.currentTimeMillis(), "dd MMM yyyy") }"
                            export.launch("MedicTimer-${LocalDate.now()}.pdf")
                        })
                        ReadingGraph(selected, Kind.GLUCOSE)
                        ReadingGraph(selected, Kind.PRESSURE)
                        Text("Records · ${selected.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (selected.isEmpty()) Text("No records yet. Your saved readings and confirmations will appear here.")
                        if (selected.isNotEmpty()) Text("Swipe the table sideways to see all columns.")
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            Row(Modifier.background(Green).padding(12.dp)) {
                                Text("Date & time", Modifier.width(190.dp), color = Color.White)
                                Text("Type", Modifier.width(160.dp), color = Color.White)
                                Text("Reading / event", Modifier.width(230.dp), color = Color.White)
                                Text("Actions", Modifier.width(150.dp), color = Color.White)
                            }
                            selected.forEach { entry ->
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stamp(entry.time), Modifier.width(190.dp))
                                    Text(entry.kind.title, Modifier.width(160.dp))
                                    Text(entry.value(), Modifier.width(230.dp))
                                    Row(Modifier.width(150.dp)) {
                                        TextButton(onClick = { edit = entry; add = entry.kind }) { Text("Edit") }
                                        TextButton(onClick = { delete = entry }) { Text("Delete") }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    else -> {
                        Text("Your daily routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        BigButton("Edit daily routine", onClick = { showSetup = true })
                        CareCard("Reminder permissions", "Android controls whether reminders can appear and arrive on time.") {
                            Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodyMedium)
                            Text(if (Reminders.notificationsAllowed(context)) "Notifications: allowed" else "Notifications: not allowed")
                            Text(if (Reminders.exactAllowed(context)) "Precise timing: allowed" else "Precise timing: not allowed (reminders may arrive late)")
                            BigButton(if (settings.enabled && Reminders.notificationsAllowed(context)) "Turn reminders off" else "Enable reminders", onClick = {
                                if (settings.enabled && Reminders.notificationsAllowed(context)) { settings.enabled = false; refresh() }
                                else enableReminders()
                            })
                            if (Build.VERSION.SDK_INT >= 31 && !Reminders.exactAllowed(context)) {
                                OutlinedButton(onClick = { context.startActivity(preciseSettingsIntent(context)) }) { Text("Allow precise reminders") }
                            }
                            OutlinedButton(onClick = { context.startActivity(notificationSettingsIntent(context)) }) { Text("Android notification settings") }
                            OutlinedButton(onClick = {
                                message = if (Reminders.testNotification(context)) "Test notification sent. Check your notification panel. Its sound depends on Android’s channel and volume settings."
                                    else "Enable reminders and allow notification permission first. If Android’s switch stays disabled, the phone may be restricting access."
                            }) { Text("Test notification") }
                        }
                        RoutineSummary(routine)
                        Text("Storage & privacy", style = MaterialTheme.typography.titleLarge)
                        Text("Records stay on this phone. No account, ads, or internet connection. Uninstalling the app removes its records. PDF exports contain health information; share them only with people you choose.")
                        Text("Reminders use Android notifications and can be affected by silent mode, battery restrictions, or force-stopping the app. Test them on this phone before relying on them.")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider()
            NavigationBar(containerColor = Color.White) {
                listOf("Today" to "●", "History" to "▤", "Settings" to "◷").forEach { (label, symbol) ->
                    NavigationBarItem(selected = screen == label, onClick = { screen = label }, icon = { Text(symbol, fontSize = 24.sp) }, label = { Text(label, fontSize = 16.sp) })
                }
            }
        }
    }
    if (mealPicker) AlertDialog(onDismissRequest = { mealPicker = false }, title = { Text("Which meal did you eat?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The current time is recorded automatically.")
            listOf("Breakfast", "Lunch", "Dinner", "Other meal").forEach { label -> BigButton(label, onClick = {
                store.save(Entry(kind = Kind.MEAL, label = label)); refresh(); mealPicker = false
                message = "Meal saved. Blood sugar is due at ${stamp(glucoseDue(System.currentTimeMillis()), "HH:mm")}." + if (!settings.enabled || !Reminders.notificationsAllowed(context)) " Reminders are off; enable them in Settings." else ""
            }) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { mealPicker = false }) { Text("Cancel") } })
    add?.let { kind ->
        EntryDialog(kind, edit, entries, onDismiss = { add = null; edit = null }, onSave = { entry ->
            store.save(entry)
            if (entry.kind == Kind.GLUCOSE) entry.mealId?.let { Reminders.cancelMeal(context, it) }
            refresh(); add = null; edit = null
        })
    }
    delete?.let { entry -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete this record?") }, text = { Text("${stamp(entry.time)}\n${entry.value()}\nThis cannot be undone.") },
        confirmButton = { TextButton(onClick = { if (entry.kind == Kind.MEAL) Reminders.cancelMeal(context, entry.id); store.delete(entry.id); refresh(); delete = null }) { Text("Delete record") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Keep record") } }) }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("MedicTimer") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}

@Composable
internal fun CareCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Green)
            Text(subtitle)
            content()
        }
    }
}

@Composable
internal fun BigButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp), shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Text(label) }
}

@Composable
private fun EntryDialog(kind: Kind, original: Entry?, entries: List<Entry>, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    val entry = remember { original ?: Entry(kind = kind) }
    var first by remember { mutableStateOf(entry.first?.let { if (kind == Kind.GLUCOSE) decimal(it) else it.toInt().toString() } ?: "") }
    var second by remember { mutableStateOf(entry.second?.toInt()?.toString() ?: "") }
    var pulse by remember { mutableStateOf(entry.pulse?.toString() ?: "") }
    var label by remember { mutableStateOf(entry.label) }
    var mealId by remember { mutableStateOf(entry.mealId) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (entry.id == 0L) if (kind == Kind.MEDICATION) "Confirm medication" else "Record ${kind.title.lowercase()}" else "Edit ${kind.title.lowercase()}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (entry.id == 0L) "The current time is saved automatically." else "Recorded ${stamp(entry.time)}. The original timestamp is kept.")
            if (kind == Kind.MEDICATION) Text("Have you taken ${entry.label.lowercase()}?")
            if (kind == Kind.MEAL) OutlinedTextField(label, { label = it.take(30) }, label = { Text("Meal") }, singleLine = true)
            if (kind == Kind.GLUCOSE || kind == Kind.PRESSURE) {
                OutlinedTextField(first, { first = it; error = null }, label = { Text(if (kind == Kind.GLUCOSE) "Blood sugar (mmol/L)" else "Upper (systolic)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (kind == Kind.PRESSURE) {
                    OutlinedTextField(second, { second = it; error = null }, label = { Text("Lower (diastolic)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(pulse, { pulse = it; error = null }, label = { Text("Pulse (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                } else {
                    Text("For a meal (optional)")
                    val availableIds = pendingMeals(entries).map { it.id }.toSet()
                    val available = entries.filter { it.kind == Kind.MEAL && (it.id == entry.mealId || it.id in availableIds) }.take(6)
                    FilterChip(selected = mealId == null, onClick = { mealId = null }, label = { Text("No meal linked") })
                    available.forEach { meal -> FilterChip(selected = mealId == meal.id, onClick = { mealId = meal.id }, label = { Text("${meal.label} · ${stamp(meal.time, "dd MMM HH:mm")}") }) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        error = if (kind == Kind.GLUCOSE || kind == Kind.PRESSURE) readingError(kind, first, second, pulse) else if (kind == Kind.MEAL && label.isBlank()) "Enter a meal name." else null
        if (error == null) onSave(entry.copy(time = if (entry.id == 0L) System.currentTimeMillis() else entry.time,
            first = parseReading(first), second = parseReading(second), pulse = pulse.toIntOrNull(), label = label, mealId = mealId))
    }) { Text(if (kind == Kind.MEDICATION) "Yes, taken" else "Save reading") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ReadingGraph(entries: List<Entry>, kind: Kind) {
    val data = entries.filter { it.kind == kind }.sortedBy { it.time }
    CareCard(kind.title, if (kind == Kind.GLUCOSE) "mmol/L" else "mmHg · green: upper · brown: lower") {
        if (data.isEmpty()) Text("No readings in this period.")
        else {
            val values = data.flatMap { listOfNotNull(it.first, if (kind == Kind.PRESSURE) it.second else null) }
            val min = (values.minOrNull()!! - if (kind == Kind.GLUCOSE) 1 else 10).coerceAtLeast(0.0)
            val max = values.maxOrNull()!! + if (kind == Kind.GLUCOSE) 1 else 10
            Text("Scale: ${decimal(min)} – ${decimal(max)}", style = MaterialTheme.typography.bodyMedium)
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val left = 8.dp.toPx(); val right = size.width - left; val top = 8.dp.toPx(); val bottom = size.height - top
                drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 2f)
                val start = data.first().time; val duration = (data.last().time - start).coerceAtLeast(1L)
                fun plot(second: Boolean, color: Color) {
                    var previous: Offset? = null
                    data.forEach {
                        val x = if (data.size == 1) size.width / 2 else left + (it.time - start).toFloat() / duration * (right - left)
                        val value = if (second) it.second!! else it.first!!
                        val y = bottom - ((value - min) / (max - min) * (bottom - top)).toFloat()
                        val point = Offset(x, y)
                        previous?.let { p -> drawLine(color, p, point, 3.dp.toPx()) }
                        drawCircle(color, 4.dp.toPx(), point); previous = point
                    }
                }
                plot(false, Green)
                if (kind == Kind.PRESSURE) plot(true, Color(0xFF8E461B))
            }
            Text("${stamp(data.first().time, "dd MMM HH:mm")} → ${stamp(data.last().time, "dd MMM HH:mm")}", style = MaterialTheme.typography.bodyMedium)
            Text("${data.size} readings. Exact values are in the table below.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
