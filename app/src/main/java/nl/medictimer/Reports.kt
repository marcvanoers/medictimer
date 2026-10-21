package nl.medictimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun stamp(time: Long, pattern: String = "dd MMM yyyy, HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

object Reports {
    fun write(context: Context, uri: Uri, entries: List<Entry>, period: String) {
        val document = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = 0f
            var number = 0
            fun text(value: String, x: Float = 40f, size: Float = 11f, color: Int = Color.BLACK) {
                paint.color = color; paint.textSize = size
                canvas!!.drawText(value, x, y, paint)
            }
            fun newPage() {
                page?.let { document.finishPage(it) }
                number++
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
                canvas = page!!.canvas
                y = 40f; text("MedicTimer | Doctor report", size = 20f)
                y = 63f; text("Period: $period")
                y = 81f; text("Created ${stamp(System.currentTimeMillis())} | Time zone: ${ZoneId.systemDefault()}", size = 9f)
                y = 802f; text("Recorded observations | Glucose: mmol/L | Blood pressure: mmHg | Page $number", size = 9f)
                y = 112f
            }
            fun graph(kind: Kind, title: String) {
                text(title, size = 14f); y += 18f
                val data = entries.filter { it.kind == kind }.sortedBy { it.time }
                if (data.isEmpty()) { text("No readings in this period."); y += 40f; return }
                val top = y; val bottom = y + 125f; val left = 68f; val right = 547f
                val values = data.flatMap { listOfNotNull(it.first, if (kind == Kind.PRESSURE) it.second else null) }
                val min = (values.minOrNull()!! - if (kind == Kind.GLUCOSE) 1 else 10).coerceAtLeast(0.0)
                val max = values.maxOrNull()!! + if (kind == Kind.GLUCOSE) 1 else 10
                val start = data.first().time; val duration = (data.last().time - start).coerceAtLeast(1L)
                paint.color = Color.LTGRAY; paint.strokeWidth = 1f
                canvas!!.drawLine(left, top, left, bottom, paint); canvas!!.drawLine(left, bottom, right, bottom, paint)
                y = top + 5; text(decimal(max), 40f, 9f)
                y = bottom; text(decimal(min), 40f, 9f)
                fun series(second: Boolean, color: Int) {
                    paint.color = color; paint.strokeWidth = 2f
                    var previous: Pair<Float, Float>? = null
                    data.forEach {
                        val value = if (second) it.second!! else it.first!!
                        val x = if (data.size == 1) (left + right) / 2 else left + (it.time - start).toFloat() / duration * (right - left)
                        val py = bottom - ((value - min) / (max - min) * (bottom - top)).toFloat()
                        previous?.let { p -> canvas!!.drawLine(p.first, p.second, x, py, paint) }
                        canvas!!.drawCircle(x, py, 3f, paint); previous = x to py
                    }
                }
                series(false, Color.rgb(22, 92, 75))
                if (kind == Kind.PRESSURE) series(true, Color.rgb(142, 70, 27))
                y = bottom + 18f
                text(stamp(start, "dd MMM HH:mm"), left, 9f)
                if (data.size > 1) text(stamp(data.last().time, "dd MMM HH:mm"), 440f, 9f)
                y += 21f
                if (kind == Kind.PRESSURE) { text("Green: upper (systolic) | Brown: lower (diastolic)", size = 9f); y += 20f }
                y += 18f
            }
            newPage()
            graph(Kind.GLUCOSE, "Blood sugar (mmol/L)")
            graph(Kind.PRESSURE, "Blood pressure (mmHg)")
            newPage()
            fun tableHeader() {
                text("Recorded history (${entries.size} entries)", size = 14f); y += 25f
                text("Date and time", 40f, 10f); text("Type", 190f, 10f); text("Reading / event", 300f, 10f); y += 23f
            }
            tableHeader()
            if (entries.isEmpty()) text("No records in this period.")
            entries.sortedBy { it.time }.forEach { entry ->
                if (y > 760f) { newPage(); tableHeader() }
                text(stamp(entry.time), 40f, 10f); text(entry.kind.title, 190f, 10f)
                text(entry.value(), 300f, 10f); y += 22f
            }
            document.finishPage(page!!)
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open destination")
            stream.use { document.writeTo(it) }
        } finally { document.close() }
    }
}
