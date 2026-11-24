package it.enjoycube.scanner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import it.enjoycube.scanner.adb.AdbKeyStore
import it.enjoycube.scanner.adb.AdbUsbConnection

class ScanActivity : AppCompatActivity() {

    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var startBtn: Button
    private lateinit var customer: EditText
    private val keys by lazy { AdbKeyStore(this) }
    @Volatile private var busy = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                runScan(device)
            } else {
                setStatus("Permesso USB negato. Premi di nuovo AVVIA SCANSIONE e conferma.")
                setBusy(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        usb = getSystemService(Context.USB_SERVICE) as UsbManager
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        startBtn = findViewById(R.id.startScan)
        customer = findViewById(R.id.customer)
        startBtn.setOnClickListener { startScan() }
        ContextCompat.registerReceiver(
            this, permissionReceiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun startScan() {
        if (busy) return
        val devices = usb.deviceList.values
        if (devices.isEmpty()) {
            setStatus("Nessuno smartphone rilevato.\nControlla il cavo e l'adattatore OTG, poi riprova.")
            return
        }
        val device = devices.firstOrNull { AdbUsbConnection.findAdbInterface(it) != null }
        if (device == null) {
            setStatus("Smartphone collegato, ma il Debug USB non è attivo.\n" +
                "Sullo smartphone: Impostazioni › Info telefono › tocca 7 volte \"Numero build\", " +
                "poi Opzioni sviluppatore › attiva \"Debug USB\". Scollega e ricollega il cavo.")
            return
        }
        setBusy(true)
        if (usb.hasPermission(device)) {
            runScan(device)
        } else {
            setStatus("Conferma l'accesso al dispositivo USB sul tablet…")
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags)
            usb.requestPermission(device, pi)
        }
    }

    private fun runScan(device: UsbDevice) {
        setBusy(true)
        val customerText = customer.text.toString().trim()
        Thread {
            try {
                setStatus("Connessione allo smartphone…")
                val adb = AdbUsbConnection.open(usb, device, keys)
                try {
                    adb.connect {
                        setStatus("Sullo SMARTPHONE compare \"Consentire il debug USB?\"\n" +
                            "Spunta \"Consenti sempre da questo computer\" e premi CONSENTI.")
                    }
                    setStatus("Lettura dei dati in corso…")
                    val raw = adb.shell(DeviceScanner.COMMAND)
                    val serial = try { device.serialNumber } catch (e: Exception) { null }
                    val result = DeviceScanner.parse(raw, customerText, serial)
                    setStatus("Creazione del report…")
                    val pdf = ReportPdf.create(this, result)
                    ScanHolder.result = result
                    ScanHolder.pdf = pdf
                    runOnUiThread {
                        setBusy(false)
                        setStatus("Analisi completata.")
                        startActivity(Intent(this, ReportActivity::class.java))
                    }
                } finally {
                    adb.close()
                }
            } catch (e: Exception) {
                setStatus("Errore: ${e.message ?: e.javaClass.simpleName}\nScollega e ricollega lo smartphone, poi riprova.")
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun setStatus(text: String) = runOnUiThread { status.text = text }

    private fun setBusy(b: Boolean) = runOnUiThread {
        busy = b
        progress.visibility = if (b) View.VISIBLE else View.GONE
        startBtn.isEnabled = !b
    }

    companion object {
        const val ACTION_USB_PERMISSION = "it.enjoycube.scanner.USB_PERMISSION"
    }
}
