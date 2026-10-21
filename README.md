# MedicTimer

A native Android companion for a simple daily routine: medication three times a day,
blood pressure twice a day, and blood sugar two hours after a logged meal.

## First version

- Large buttons and readable text, with Today, History, and Settings screens.
- One-tap breakfast/lunch/dinner logging with an automatic timestamp.
- Blood sugar in **mmol/L**, blood pressure in **mmHg**, and optional pulse.
- Three medication confirmation slots, with configurable daily reminder times.
- Two configurable blood pressure reminder times and meal-based glucose reminders.
- Local SQLite records, history editing/deletion, 7-day/30-day/all-time views.
- Glucose and blood pressure graphs, a horizontally scrollable history table,
  and an Android document-picker PDF export with graphs and complete selected history.
- No account, internet permission, analytics, ads, or cloud service.

## Build

Use Android Studio with JDK 17 or 21, Android SDK Platform 35 and Build Tools 35.0.0.
Set `ANDROID_HOME` or create ignored `local.properties` containing `sdk.dir=/path/to/sdk`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: 8.0 (API 26). Kotlin 2.1.20, Compose, AGP 8.9.2,
Gradle 8.13. The PDF export uses Android's own `PdfDocument`; graphs use Compose Canvas.

In this managed cloud workspace, use:

```sh
python3 scripts/cloud_setup.py
python3 scripts/cloud_gradle.py assembleDebug testDebugUnitTest lintDebug
```

These helpers keep the SDK, JDK, caches, and Java proxy trust under
`/workspace/tooling`, retaining TLS and download-checksum verification.

## First use

1. In Settings, adjust the three medication and two blood pressure times to the
   existing prescribed routine. Defaults are placeholders, not treatment advice.
2. Enable reminders and grant notification permission. On Android 12+, allow
   precise reminders under Alarms & reminders if offered. Check that the app's
   notification channel has sound enabled in Android settings.
3. Test a reminder on the actual phone with the screen off. Device power-saving,
   Do Not Disturb, channel settings, and force-stop can affect delivery.
4. Log a meal when it happens. The glucose reminder is scheduled two hours later;
   a glucose reading can be linked to that meal or recorded independently.
5. Use History → Export PDF for doctor to save a report, then share it using Files.

Reminders restart after reboot and clock/time-zone changes. Without precise-alarm
permission, Android's inexact alarm fallback can be delayed. Notifications prompt
the user to open the app; they do not imply that a dose was taken. Medication is
only marked taken after confirmation. Entries retain their original timestamps
when corrected. Meal names and measurement values can be edited in History.

## Data and limitations

Data is private to the app on this device; Android automatic backup is disabled.
Uninstalling or clearing app storage deletes the records. Export PDFs regularly:
they are reports, **not restorable database backups**. Exported files contain health
information and should be shared intentionally. No remote caregiver access or
automatic meter integration is implemented.

This first version records observations and reminders. It does not interpret
readings, adjust doses, or supply treatment thresholds. Broad numeric input guards
only catch common entry mistakes. Confirm reminder reliability and usability on
the intended phone before using it as part of the daily routine.

## Open-source references

[MedTimer](https://github.com/Futsch1/medTimer) (MIT) informed the feature research:
offline medication reminders and confirmation history. No source code or assets
from MedTimer have been copied into this implementation. Third-party dependencies
are declared in `app/build.gradle.kts`. This app currently uses original chart and
PDF code rather than Vico or a third-party PDF library.

See [manual validation](docs/VALIDATION.md) for the device acceptance checklist.
