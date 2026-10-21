package nl.medictimer

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test @Config(sdk = [34], qualifiers = "w320dp-h470dp")
    fun `log a meal and a glucose reading then find them in history`() {
        completeDefaultSetup()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Get started").assertDoesNotExist()
        compose.onNodeWithText("Log a meal").performScrollTo().performClick()
        compose.onNodeWithText("Breakfast").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Record blood sugar").performScrollTo().performClick()
        compose.onNodeWithText("Blood sugar (mmol/L)").performTextInput("6,4")
        compose.onNodeWithText("Save reading").performClick()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("6.4 mmol/L").assertExists()
        compose.onNodeWithText("Breakfast").assertExists()
        compose.onNodeWithText("Export PDF for doctor").assertExists()
    }

    @Test fun `wizard can configure lunch and dinner pills without blood pressure tracking`() {
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("With meals").performScrollTo().performClick()
        compose.onNodeWithText("Breakfast · 08:00").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("With meals").assertIsSelected()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("I measure blood pressure").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Save without reminders").performClick()
        compose.runOnIdle {
            val routine = Preferences(compose.activity).routine()
            assertEquals(listOf(Meal.LUNCH, Meal.DINNER), routine.medications.map { it.meal })
            assertTrue(routine.pressureTimes.isEmpty())
            assertTrue(routine.glucoseEnabled)
            assertTrue(Preferences(compose.activity).setupComplete)
        }
        compose.onNodeWithText("Record blood pressure").assertDoesNotExist()
        compose.onNodeWithText("Confirm With lunch (13:00) taken").assertExists()
    }

    @Test fun `editing the routine changes counts and can disable blood sugar independently`() {
        completeDefaultSetup()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Edit daily routine").performScrollTo().performClick()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Next").performClick()
        repeat(2) { compose.onAllNodesWithText("Remove").onLast().performScrollTo().performClick() }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Add blood pressure time").performScrollTo().performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("I measure blood sugar").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.runOnIdle {
            val routine = Preferences(compose.activity).routine()
            assertEquals(1, routine.medications.size)
            assertEquals(3, routine.pressureTimes.size)
            assertFalse(routine.glucoseEnabled)
        }
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Log a meal").assertDoesNotExist()
        compose.onNodeWithText("Record blood pressure").assertExists()
    }

    @Test fun `back and cancel leave the saved routine unchanged`() {
        completeDefaultSetup()
        val original = Preferences(compose.activity).routine()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Edit daily routine").performScrollTo().performClick()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("I take medication").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(original, Preferences(compose.activity).routine()) }
    }

    @Test @Config(sdk = [28], qualifiers = "w393dp-h851dp")
    fun `enable opens Android settings even if notifications are already allowed`() {
        completeDefaultSetup()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Enable reminders").performScrollTo().performClick()
        compose.runOnIdle {
            val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals("nl.medictimer", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertFalse(Preferences(compose.activity).enabled)
            shadowOf(compose.activity).receiveResult(intent, Activity.RESULT_CANCELED, null)
        }
        compose.runOnIdle { assertTrue(Preferences(compose.activity).enabled) }
    }

    @Test fun `permission flow checks notification denial when returning from Android settings`() {
        completeDefaultSetup()
        compose.runOnIdle { shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Enable reminders").performScrollTo().performClick()
        compose.runOnIdle {
            val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
            shadowOf(compose.activity).receiveResult(intent, Activity.RESULT_CANCELED, null)
        }
        compose.onNodeWithText("Reminders are still off. Allow MedicTimer notifications in Android Settings, then return here.").assertExists()
        compose.runOnIdle { assertFalse(Preferences(compose.activity).enabled) }
    }

    @Test fun `notification approval leads to precise alarm access and refreshes permissions on return`() {
        completeDefaultSetup()
        compose.runOnIdle {
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
        }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Enable reminders").performScrollTo().performClick()
        compose.runOnIdle {
            val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
            shadowOf(compose.activity).receiveResult(intent, Activity.RESULT_CANCELED, null)
        }
        compose.runOnIdle {
            val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
            assertEquals("package:nl.medictimer", intent.data.toString())
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            shadowOf(compose.activity).receiveResult(intent, Activity.RESULT_CANCELED, Intent())
        }
        compose.runOnIdle { assertTrue(Preferences(compose.activity).enabled) }
    }

    private fun completeDefaultSetup() {
        compose.onNodeWithText("Get started").performClick()
        repeat(4) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Save without reminders").performClick()
    }
}
