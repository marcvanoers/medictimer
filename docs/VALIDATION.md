# Device acceptance checks

## Verified in the cloud workspace (21 October 2026)

- Debug app and instrumentation APKs build successfully; Android lint passes.
- Version 0.2: all 26 JVM/Robolectric tests pass, with no skipped tests, including
  the six-step wizard, lunch/dinner medication selection, optional readings,
  changes in frequency, cancelled edits, state restoration, and the Compose
  meal → glucose → history flow. Permission tests check OS settings intents,
  approval/denial on return, exact alarm access, and blocked reminder channels.
- Upgrade tests verify that the v0.1 database retains readings, timestamps, and
  medication confirmations, and that existing reminder times remain intact.
- The native PDF instrumentation test passes on an Android 9 / API 28 software
  emulator. Poppler independently parses its output: five pages, all 90 synthetic
  glucose readings and one blood pressure reading, with expected units.
- The app launches on the API 28 emulator; its Today screen was visually inspected.
- The version 0.2 wizard was navigated on the API 28 emulator and its meal,
  medication, blood pressure, blood sugar, and summary screens were inspected.
- Enable reminders was tapped on the emulator: Android's MedicTimer notification
  settings opened, and returning to the app enabled reminders after access was
  checked. Android 12+ exact-alarm permission paths are covered by Robolectric;
  actual-phone delivery still requires the checks below.
- The cloud setup script ran successfully twice, reusing installed tools and caches.

The actual phone's notification delivery, accessibility, and file-sharing checks
below still need to be performed. Software-emulator timings do not establish
real-phone performance.

## Notification permission update (23 October 2026)

Version 0.2.1 corrects all three manifest permission names to their required
`android.permission.*` form, and requests native notification permission first on Android 13+,
retains OS settings fallback, reconciles a pending enable request on app resume,
and adds a test notification and Android version in Settings. All 34 JVM/Robolectric
tests pass, Android lint passes, and the rebuilt APK upgrades successfully on API 28.
The packaged manifest was independently checked with aapt. Automated checks
cover native prompt approval on API 33/34/35, denial on API 34, settings fallback,
precise-alarm access, and persisted enable requests on API 33/34/35.

The grey notification switch reported on the actual phone has not been reproduced;
its model and Android version are still unknown. Automated permission checks do
not establish that an OEM-disabled switch will become editable, or that alarms
will arrive under that phone's battery settings.

## How to validate

Automated tests exercise reading validation, decimal commas, daily schedules
across daylight saving, two-hour timing across midnight, meal associations, and
SQLite persistence, edits, and deletion. Build and lint check the Android package.

The native PDF check runs on Android (Robolectric has no native PdfDocument
implementation):

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w nl.medictimer.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as nl.medictimer cat files/doctor-report.pdf > /tmp/doctor-report.pdf
python3 scripts/verify_report.py /tmp/doctor-report.pdf
```

The last check uses Poppler to parse the exported PDF and verify pagination,
units, and all 91 synthetic test records. These test records are passed directly
to the report writer and are never inserted into the app's actual database.

The following checks require an Android phone or emulator; do not infer they passed
from a successful APK build. Test with the father's actual font/display settings.

1. Open on a clean install. Complete guided setup with medication only at lunch
   and dinner, no blood pressure tracking, and blood sugar tracking on. Confirm
   only the selected tasks appear. Reopen and rotate the app; the wizard should
   stay completed. During unfinished setup, rotation must preserve the current step.
   Saving without reminders must allow recording data and show the reminder warning.
2. Tap Enable reminders. On Android 13+, the first request must show the native
   notification prompt. Denial must leave reminders off. Older versions and
   denied requests without a rationale use OS settings. Return after allowing
   access; on Android 12+, check the subsequent Alarms & reminders flow. Deny
   notification access and verify reminders stay off. Deny exact alarm access and
   verify the warning about delayed delivery. Check disabled notification channels.
   Tap Test notification and check the notification panel and sound settings.
   Grant notifications, enable precise reminders, set a medication reminder a few
   minutes ahead, lock the screen, and confirm it arrives. Open it, record the dose,
   and verify the button cannot double-record that slot today.
3. Log breakfast and check its timestamp and due time (+2 hours). Record glucose
   linked to breakfast. Its due card and alarm must disappear; an independent glucose
   reading must not complete an unrelated meal.
4. Enter `6,4` mmol/L; enter 122/81 mmHg with pulse 64. Reject blank/invalid inputs,
   inverted pressures, and nonnumeric pulse. Restart the app; readings must persist.
5. Inspect 7-day, 30-day, all-time history and both plots. Edit a reading, check that
   its original time stays unchanged. Delete a meal and ensure linked readings survive.
6. Export the PDF to Downloads. Open it in a PDF viewer: verify units, date range,
   graph legends, exact table values, timestamps/time zone, and multi-page pagination.
   Cancel export and test an unwritable destination; the app must remain usable.
7. Reboot and test the next reminder. Change reminder times/time zone and verify no
   duplicate alarms. Change frequency, turn individual features off, and verify old
   daily and meal alarms are cancelled while history is retained. Meal-linked doses
   must follow edits to their meal time; actual meal logging must still use +2 hours.
   Disable all reminders and verify future alarms are cancelled.
8. Increase font size/display scaling and enable TalkBack. Ensure forms, confirmations,
   tabs, and tables remain reachable, readable, and understandable.
9. Test screen-off delivery under the phone's battery settings. Force-stopping the
   app requires reopening it before alarms can resume; document this to the family.

Remaining product work: restorable backup, caregiver sharing if wanted, medication
names, optional translations, and validation with the intended user.
