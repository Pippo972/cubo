package it.enjoycube.scanner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        val r = ScanHolder.result
        val pdf = ScanHolder.pdf
        if (r == null || pdf == null) { finish(); return }

        findViewById<TextView>(R.id.deviceName).text = r.deviceName
        val verdict = findViewById<TextView>(R.id.verdict)
        verdict.text = buildString {
            append(r.verdict)
            r.notes.forEach { append("\n• ").append(it) }
        }
        verdict.setBackgroundColor(if (r.notes.isEmpty()) Color.rgb(226, 246, 233) else Color.rgb(255, 240, 220))
        verdict.setTextColor(if (r.notes.isEmpty()) Color.rgb(20, 110, 50) else Color.rgb(170, 90, 0))

        val container = findViewById<LinearLayout>(R.id.sections)
        val dp = resources.displayMetrics.density
        for (s in r.sections) {
            container.addView(TextView(this).apply {
                text = s.title.uppercase()
                setTextColor(Color.rgb(0, 57, 173))
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                setPadding(0, (20 * dp).toInt(), 0, (6 * dp).toInt())
            })
            for ((k, v) in s.rows) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
                }
                row.addView(TextView(this).apply {
                    text = k; textSize = 15f; setTextColor(Color.DKGRAY)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(this).apply {
                    text = v; textSize = 15f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
                })
                container.addView(row)
                container.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(225, 228, 235))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                })
            }
        }

        findViewById<TextView>(R.id.savedPath).text = "Report salvato in:\n${pdf.absolutePath}"

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", pdf)
        findViewById<Button>(R.id.openPdf).setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { startActivity(i) } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Nessuna app per aprire i PDF installata", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.sharePdf).setOnClickListener {
            val i = Intent(Intent.ACTION_SEND).setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Report analisi ${r.deviceName}")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Condividi report"))
        }
        findViewById<Button>(R.id.newScan).setOnClickListener { finish() }
    }
}
