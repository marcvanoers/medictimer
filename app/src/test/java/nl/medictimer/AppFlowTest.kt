package nl.medictimer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `log a meal and a glucose reading then find them in history`() {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/screenshots/today.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
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
}
