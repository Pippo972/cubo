package it.enjoycube.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Genera il report in PDF (A4) e lo salva in Android/data/it.enjoycube.scanner/files/Documents. */
object ReportPdf {

    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val BLUE = Color.rgb(0, 57, 173)

    fun create(ctx: Context, r: ScanResult): File {
        val doc = PdfDocument()
        val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.nova_wordmark)
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
        val reportId = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ITALY).format(r.date)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 10f }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 8f }
        val line = Paint().apply { color = Color.rgb(225, 228, 235); strokeWidth = 0.7f }
        val fill = Paint().apply { color = BLUE }
        val band = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), 80f, Color.rgb(8, 18, 64), BLUE, Shader.TileMode.CLAMP)
        }

        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun footer() {
            c.drawLine(M, H - 40f, W - M, H - 40f, line)
            c.drawText("NOVA · Nuova Opportunità per Vecchi Apparecchi  —  Report n. $reportId", M, H - 28f, small)
            c.drawText("Pagina $pageNo", W - M - 35f, H - 28f, small)
        }

        fun newPage() {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            c.drawRect(0f, 0f, W.toFloat(), 80f, band)
            val lh = 30f
            val lw = logo.width * lh / logo.height
            c.drawBitmap(logo, null, RectF(M, 25f, M + lw, 25f + lh), null)
            c.drawText("Report di analisi dispositivo", W - M - white.measureText("Report di analisi dispositivo"), 46f, white)
            y = 110f
        }

        fun ensure(space: Float) { if (y + space > H - 60f) newPage() }

        newPage()

        // Intestazione
        val info = listOf(
            "Dispositivo" to r.deviceName,
            "Data analisi" to dateFmt.format(r.date),
            "Cliente / Riferimento" to r.customer.ifBlank { "—" },
            "N. report" to reportId
        )
        for ((k, v) in info) {
            c.drawText(k, M, y, label); c.drawText(v, M + 150f, y, value); y += 16f
        }
        y += 6f

        // Esito
        val ok = r.notes.isEmpty()
        val box = Paint().apply { color = if (ok) Color.rgb(226, 246, 233) else Color.rgb(255, 240, 220) }
        val boxText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (ok) Color.rgb(20, 110, 50) else Color.rgb(170, 90, 0); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        }
        val boxH = 28f + r.notes.size * 14f
        c.drawRoundRect(RectF(M, y, W - M, y + boxH), 6f, 6f, box)
        c.drawText("Esito: ${r.verdict}", M + 10f, y + 18f, boxText)
        var ny = y + 32f
        for (n in r.notes) { c.drawText("• $n", M + 14f, ny, label); ny += 14f }
        y += boxH + 22f

        // Sezioni
        for (s in r.sections) {
            ensure(40f)
            c.drawText(s.title.uppercase(Locale.ITALY), M, y, title)
            y += 6f
            c.drawLine(M, y, W - M, y, fill.apply { strokeWidth = 1.2f })
            y += 16f
            for ((k, v) in s.rows) {
                ensure(16f)
                c.drawText(k, M + 4f, y, label)
                c.drawText(ellipsize(v, value, W - M - (M + 190f)), M + 190f, y, value)
                y += 4f
                c.drawLine(M, y, W - M, y, line)
                y += 12f
            }
            y += 12f
        }

        ensure(40f)
        y += 10f
        c.drawText("Analisi eseguita via USB in sola lettura: nessun dato personale è stato letto o modificato.", M, y, small)

        footer()
        doc.finishPage(page)

        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: ctx.filesDir
        dir.mkdirs()
        val safeName = r.deviceName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        val file = File(dir, "Report_${safeName}_$reportId.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        // copia dei dati grezzi, utile per assistenza
        File(dir, "Report_${safeName}_$reportId.txt").writeText(r.raw)
        return file
    }

    private fun ellipsize(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
