package it.enjoycube.scanner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/** Mostra le prime due pagine del PDF; dalla seconda si avvia la scansione. */
class IntroActivity : AppCompatActivity() {

    private var page = 0
    private lateinit var image: ImageView
    private lateinit var root: View
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)
        image = findViewById(R.id.pageImage)
        root = findViewById(R.id.introRoot)
        button = findViewById(R.id.introButton)
        page = savedInstanceState?.getInt("page") ?: 0
        show()
        image.setOnClickListener { if (page == 0) { page = 1; show() } }
        button.setOnClickListener {
            if (page == 0) { page = 1; show() }
            else startActivity(Intent(this, ScanActivity::class.java))
        }
    }

    private fun show() {
        if (page == 0) {
            image.setImageResource(R.drawable.page1)
            root.setBackgroundColor(Color.rgb(0, 57, 173))
            button.text = "Avanti  ›"
        } else {
            image.setImageResource(R.drawable.page2)
            root.setBackgroundColor(Color.rgb(243, 243, 243))
            button.text = "START"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page == 1) { page = 0; show() } else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }
}
