# MedicTimer

A native Android companion with a personal routine for medication, blood pressure,
and blood sugar two hours after a logged meal.

## Download for Android

[Download MedicTimer 0.2.0 APK](https://github.com/marcvanoers/medictimer/raw/refs/heads/main/downloads/MedicTimer-0.2.0-debug.apk)

Requires Android 8.0 or newer. This is a debug-signed test build. Open the APK on
your phone and, if prompted, allow installation from the browser or Files app.
After installation, configure reminder times and notification permissions in Settings.
Check reminders on the intended phone before relying on them.

The APK contains no patient records. Its SHA-256 checksum is recorded in
[downloads/SHA256SUMS](downloads/SHA256SUMS).

Updating from 0.1: install 0.2 over the existing app. **Do not uninstall first**;
your records are retained, and the setup wizard lets you review your routine.

## Features

- Guided setup on first use, and an editable daily routine in Settings.
- Usual breakfast, lunch, and dinner times, with prompts to log actual meals when
  blood sugar tracking is enabled.
- Large buttons and readable text, with Today, History, and Settings screens.
- One-tap breakfast/lunch/dinner logging with an automatic timestamp.
- Blood sugar in **mmol/L**, blood pressure in **mmHg**, and optional pulse.
- Medication with selected meals (for example lunch and dinner only), or at
  specific times; custom medication and blood pressure schedules support up to
  twelve daily times. Either feature can be turned off.
- Optional blood sugar tracking, independent of blood pressure, with a fixed
  two-hour reminder after each actual logged meal.
- “Enable reminders” opens Android notification settings automatically, follows
  with Alarms & reminders on Android 12+ when needed, and checks access on return.
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

1. Follow the six-step setup: usual meals, medication with meals or specific
   times, optional blood pressure readings and frequency, and optional blood sugar
   tracking. Defaults are placeholders, not treatment advice. Existing records
   and old reminder times are retained when upgrading from 0.1.
2. Choose Save and enable reminders. Android's notification settings open; allow
   MedicTimer notifications and return. If needed, Alarms & reminders opens next
   for precise timing. Declining notification access leaves reminders off;
   declining precise timing allows Android's inexact fallback with a warning.
   You may instead save without reminders and enable them later in Settings.
3. Test a reminder on the actual phone with the screen off. Device power-saving,
   Do Not Disturb, channel settings, and force-stop can affect delivery.
4. If blood sugar tracking is enabled, log a meal when it happens. Usual meal times
   prompt meal logging; they do not fabricate a meal or start a glucose timer.
   The glucose reminder is scheduled two hours after the actual meal timestamp;
   a glucose reading can be linked to that meal or recorded independently.
5. Use History → Export PDF for doctor to save a report, then share it using Files.

Edit daily routine in Settings to change these choices. Meal-linked medication
times follow changes to the usual meal times; medication always needs explicit
confirmation. Turning a feature off removes its future alarms and Today cards
while keeping its existing records in History. Setup drafts survive activity
recreation and only apply when saved; cancelling leaves the routine unchanged.

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
