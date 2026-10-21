# Device acceptance checks

## Verified in the cloud workspace (21 October 2026)

- Debug app and instrumentation APKs build successfully; Android lint passes.
- All 12 JVM/Robolectric tests pass, with no skipped tests, including the Compose
  meal → glucose → history flow, SQLite persistence, alarm cancellation, and
  Android 13+ notification denial.
- The native PDF instrumentation test passes on an Android 9 / API 28 software
  emulator. Poppler independently parses its output: five pages, all 90 synthetic
  glucose readings and one blood pressure reading, with expected units.
- The app launches on the API 28 emulator; its Today screen was visually inspected.
- The cloud setup script ran successfully twice, reusing installed tools and caches.

The actual phone's notification delivery, accessibility, and file-sharing checks
below still need to be performed. Software-emulator timings do not establish
real-phone performance.

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

1. Open on a clean install. No fabricated readings should appear. Deny notification
   permission; saving meals/readings must still work and the reminder warning must show.
2. Grant notifications, enable precise reminders, set a medication reminder a few
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
   duplicate alarms. Disable reminders and verify future alarms are cancelled.
8. Increase font size/display scaling and enable TalkBack. Ensure forms, confirmations,
   tabs, and tables remain reachable, readable, and understandable.
9. Test screen-off delivery under the phone's battery settings. Force-stopping the
   app requires reopening it before alarms can resume; document this to the family.

Remaining product work: restorable backup, caregiver sharing if wanted, medication
names, optional translations, and validation with the intended user.
