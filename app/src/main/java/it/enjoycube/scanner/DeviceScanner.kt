package it.enjoycube.scanner

import java.util.Date
import java.util.Locale

class ReportSection(val title: String, val rows: List<Pair<String, String>>)

class ScanResult(
    val date: Date,
    val customer: String,
    val deviceName: String,
    val sections: List<ReportSection>,
    val verdict: String,
    val notes: List<String>,
    val raw: String
)

/** Esegue un unico comando shell sullo smartphone e ne interpreta l'output. */
object DeviceScanner {

    val COMMAND = listOf(
        "echo '###PROP'", "getprop",
        "echo '###BAT'", "dumpsys battery",
        "echo '###MEM'", "cat /proc/meminfo",
        "echo '###DF'", "df /data",
        "echo '###WM'", "wm size",
        "echo '###UP'", "cat /proc/uptime",
        "echo '###END'"
    ).joinToString("; ")

    fun parse(raw: String, customer: String, usbSerial: String?): ScanResult {
        val blocks = splitBlocks(raw)
        val props = parseProps(blocks["PROP"].orEmpty())
        val bat = parseKeyValue(blocks["BAT"].orEmpty())
        val mem = parseKeyValue(blocks["MEM"].orEmpty())
        val notes = mutableListOf<String>()

        fun p(k: String) = props[k].orEmpty().ifBlank { "—" }

        val manufacturer = props["ro.product.manufacturer"].orEmpty().replaceFirstChar { it.uppercase() }
        val model = props["ro.product.model"].orEmpty()
        val deviceName = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Dispositivo" }
        val serial = props["ro.serialno"].orEmpty().ifBlank { usbSerial.orEmpty() }.ifBlank { "—" }

        // ---- Dispositivo
        val device = mutableListOf(
            "Produttore" to manufacturer.ifBlank { "—" },
            "Modello" to model.ifBlank { "—" },
            "Marchio" to p("ro.product.brand"),
            "Nome in codice" to p("ro.product.device"),
            "Numero di serie" to serial,
            "Piattaforma" to p("ro.board.platform"),
            "Architettura CPU" to p("ro.product.cpu.abi")
        )
        blocks["WM"]?.let { Regex("(\\d+x\\d+)").find(it)?.groupValues?.get(1) }?.let {
            device += "Risoluzione schermo" to it
        }

        // ---- Software
        val bootState = props["ro.boot.verifiedbootstate"].orEmpty()
        val flashLocked = props["ro.boot.flash.locked"].orEmpty()
        val bootText = when (bootState) {
            "green" -> "Bloccato (originale)"
            "yellow" -> "Chiave personalizzata"
            "orange" -> "Sbloccato"
            "" -> "Non disponibile"
            else -> bootState
        }
        if (bootState == "orange" || flashLocked == "0") notes += "Bootloader sbloccato: possibile software modificato."
        val software = mutableListOf(
            "Versione Android" to p("ro.build.version.release"),
            "Livello API" to p("ro.build.version.sdk"),
            "Patch di sicurezza" to p("ro.build.version.security_patch"),
            "Build" to p("ro.build.display.id"),
            "Baseband" to p("gsm.version.baseband"),
            "Stato bootloader" to bootText
        )
        val uptime = blocks["UP"]?.trim()?.split(" ")?.firstOrNull()?.toDoubleOrNull()
        if (uptime != null) software += "Acceso da" to formatUptime(uptime.toLong())

        // ---- Batteria
        val level = bat["level"]?.toIntOrNull()
        val scale = bat["scale"]?.toIntOrNull() ?: 100
        val health = bat["health"]?.toIntOrNull()
        val temp = bat["temperature"]?.toIntOrNull()?.let { it / 10.0 }
        val battery = mutableListOf<Pair<String, String>>()
        if (level != null) battery += "Livello carica" to "${level * 100 / scale} %"
        battery += "Stato" to statusText(bat["status"]?.toIntOrNull())
        battery += "Salute" to healthText(health)
        battery += "Alimentazione" to powerText(bat)
        bat["voltage"]?.toIntOrNull()?.let { battery += "Tensione" to String.format(Locale.ITALY, "%.2f V", if (it > 100) it / 1000.0 else it.toDouble()) }
        if (temp != null) battery += "Temperatura" to String.format(Locale.ITALY, "%.1f °C", temp)
        bat["technology"]?.takeIf { it.isNotBlank() }?.let { battery += "Tecnologia" to it }
        (bat["Charge counter"] ?: bat["charge counter"])?.toLongOrNull()?.takeIf { it > 0 }?.let {
            battery += "Carica residua" to "${it / 1000} mAh"
        }
        bat["cycle count"]?.let { battery += "Cicli di carica" to it }
        if (health != null && health != 2) notes += "Batteria con anomalia: ${healthText(health)}."
        if (temp != null && temp >= 45) notes += "Temperatura batteria elevata (${temp} °C)."

        // ---- Memoria
        val memory = mutableListOf<Pair<String, String>>()
        val memTotal = mem["MemTotal"]?.let { kb(it) }
        val memAvail = (mem["MemAvailable"] ?: mem["MemFree"])?.let { kb(it) }
        if (memTotal != null) memory += "RAM totale" to gb(memTotal)
        if (memAvail != null) memory += "RAM disponibile" to gb(memAvail)

        val df = parseDf(blocks["DF"].orEmpty())
        if (df != null) {
            val (size, used, avail) = df
            memory += "Memoria interna (dati)" to gb(size)
            memory += "Spazio usato" to "${gb(used)} (${if (size > 0) used * 100 / size else 0} %)"
            memory += "Spazio libero" to gb(avail)
            if (size > 0 && avail * 100 / size < 10) notes += "Spazio di archiviazione quasi esaurito."
        } else {
            memory += "Memoria interna" to (blocks["DF"]?.trim()?.lines()?.lastOrNull() ?: "—")
        }

        // ---- Rete
        val network = listOf(
            "Stato SIM" to simText(props["gsm.sim.state"]),
            "Operatore" to p("gsm.operator.alpha").trim(',').ifBlank { "—" },
            "Lingua di sistema" to p("persist.sys.locale")
        )

        val verdict = if (notes.isEmpty()) "IDONEO – nessuna anomalia rilevata" else "DA VERIFICARE – ${notes.size} segnalazione/i"

        return ScanResult(
            date = Date(),
            customer = customer,
            deviceName = deviceName,
            sections = listOf(
                ReportSection("Dispositivo", device),
                ReportSection("Software", software),
                ReportSection("Batteria", battery),
                ReportSection("Memoria", memory),
                ReportSection("Rete", network)
            ),
            verdict = verdict,
            notes = notes,
            raw = raw
        )
    }

    // ------------------------------------------------------------ helpers

    private fun splitBlocks(raw: String): Map<String, String> {
        val map = mutableMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in raw.replace("\r", "").lines()) {
            if (line.startsWith("###")) {
                current = StringBuilder().also { map[line.removePrefix("###").trim()] = it }
            } else current?.appendLine(line)
        }
        return map.mapValues { it.value.toString() }
    }

    private fun parseProps(text: String): Map<String, String> {
        val re = Regex("^\\[(.+?)]: \\[(.*)]$")
        return text.lines().mapNotNull { re.find(it.trim())?.destructured?.let { (k, v) -> k to v } }.toMap()
    }

    private fun parseKeyValue(text: String): Map<String, String> =
        text.lines().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()

    /** Restituisce dimensione, usato, libero in kB (output di toybox df). */
    private fun parseDf(text: String): Triple<Long, Long, Long>? {
        val line = text.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() && !it.startsWith("Filesystem") } ?: return null
        val t = line.split(Regex("\\s+"))
        if (t.size < 4) return null
        val size = t[1].toLongOrNull() ?: return null
        val used = t[2].toLongOrNull() ?: return null
        val avail = t[3].toLongOrNull() ?: return null
        return Triple(size, used, avail)
    }

    private fun kb(v: String): Long? = v.split(" ").firstOrNull()?.toLongOrNull()

    private fun gb(kb: Long): String =
        if (kb >= 1024L * 1024) String.format(Locale.ITALY, "%.1f GB", kb / 1024.0 / 1024.0)
        else String.format(Locale.ITALY, "%d MB", kb / 1024)

    private fun formatUptime(sec: Long): String {
        val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
        return if (d > 0) "${d} g ${h} h ${m} min" else "${h} h ${m} min"
    }

    private fun statusText(s: Int?) = when (s) {
        2 -> "In carica"; 3 -> "In scarica"; 4 -> "Non in carica"; 5 -> "Carica completa"; else -> "Sconosciuto"
    }

    private fun healthText(h: Int?) = when (h) {
        2 -> "Buona"; 3 -> "Surriscaldata"; 4 -> "Esaurita"; 5 -> "Sovratensione"
        6 -> "Guasto non specificato"; 7 -> "Fredda"; else -> "Sconosciuta"
    }

    private fun powerText(b: Map<String, String>): String {
        val src = mutableListOf<String>()
        if (b["AC powered"] == "true") src += "Rete"
        if (b["USB powered"] == "true") src += "USB"
        if (b["Wireless powered"] == "true") src += "Wireless"
        return if (src.isEmpty()) "Batteria" else src.joinToString(", ")
    }

    private fun simText(s: String?) = when (s?.split(",")?.firstOrNull()) {
        "READY", "LOADED" -> "Presente"
        "ABSENT" -> "Assente"
        "PIN_REQUIRED" -> "PIN richiesto"
        "PUK_REQUIRED" -> "PUK richiesto"
        "NETWORK_LOCKED" -> "Bloccata dall'operatore"
        null, "" -> "—"
        else -> s
    }
}
