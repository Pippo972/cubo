package it.enjoycube.scanner

import java.io.File

/** Passa l'ultimo risultato alla schermata del report. */
object ScanHolder {
    var result: ScanResult? = null
    var pdf: File? = null
}
