package nl.medictimer

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native PdfDocument is not implemented by Robolectric; run this on Android. */
@RunWith(AndroidJUnit4::class)
class ReportsDeviceTest {
    @Test fun exportNativePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = (1..90).map { index ->
            Entry(id = index.toLong(), kind = Kind.GLUCOSE, time = 1792569600000L + index * 60000L, first = 6.4)
        } + Entry(id = 91, kind = Kind.PRESSURE, time = 1792579600000L, first = 122.0, second = 81.0, pulse = 64)
        val file = File(context.filesDir, "doctor-report.pdf")
        Reports.write(context, Uri.fromFile(file), entries, "Test period")
        assertTrue(file.length() > 1000)
        val header = ByteArray(4)
        file.inputStream().use { assertEquals(4, it.read(header)) }
        assertEquals("%PDF", String(header))
        // Pull this artifact and parse it on the host to verify pagination and all 91 rows.
    }
}
