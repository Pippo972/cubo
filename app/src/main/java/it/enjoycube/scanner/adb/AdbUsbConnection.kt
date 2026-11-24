package it.enjoycube.scanner.adb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementazione minimale del protocollo ADB sopra USB Host (OTG).
 * Permette al tablet di eseguire comandi di sola lettura sullo smartphone collegato
 * (proprietà di sistema, stato batteria, memoria...).
 */
class AdbUsbConnection private constructor(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
    private val keys: AdbKeyStore
) {
    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private var nextLocalId = 1
    private val readBuf = ByteArray(16384)

    // ---------------------------------------------------------------- handshake

    fun connect(onWaitingForUser: () -> Unit): String {
        send(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
        var sentSignature = false
        var sentKey = false
        while (true) {
            val msg = try {
                readMessage(if (sentKey) 60_000 else 10_000)
            } catch (e: AdbException) {
                if (sentKey) throw AdbException("Autorizzazione non confermata sullo smartphone (tempo scaduto)")
                throw e
            }
            when (msg.command) {
                A_CNXN -> return String(msg.data).trimEnd('\u0000')
                A_AUTH -> {
                    if (msg.arg0 != AUTH_TOKEN) continue
                    when {
                        !sentSignature -> {
                            send(A_AUTH, AUTH_SIGNATURE, 0, keys.sign(msg.data))
                            sentSignature = true
                        }
                        !sentKey -> {
                            send(A_AUTH, AUTH_RSAPUBLICKEY, 0, keys.adbPublicKey())
                            sentKey = true
                            onWaitingForUser()
                        }
                        else -> throw AdbException("Autorizzazione rifiutata sullo smartphone")
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- shell

    fun shell(command: String, timeoutMs: Int = 30_000): String {
        val localId = nextLocalId++
        send(A_OPEN, localId, 0, "shell:$command\u0000".toByteArray())
        val out = ByteArrayOutputStream()
        while (true) {
            val msg = readMessage(timeoutMs)
            if (msg.arg1 != localId) continue
            when (msg.command) {
                A_OKAY -> { /* stream aperto */ }
                A_WRTE -> {
                    out.write(msg.data)
                    send(A_OKAY, localId, msg.arg0)
                }
                A_CLSE -> break
            }
        }
        return out.toString("UTF-8")
    }

    fun close() {
        try { conn.releaseInterface(iface) } catch (_: Exception) {}
        try { conn.close() } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- I/O

    private fun send(cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cmd).putInt(arg0).putInt(arg1)
            .putInt(data.size).putInt(checksum(data)).putInt(cmd.inv())
            .array()
        writeFully(header)
        if (data.isNotEmpty()) writeFully(data)
    }

    private fun writeFully(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val len = minOf(16384, buf.size - off)
            val chunk = if (off == 0 && len == buf.size) buf else buf.copyOfRange(off, off + len)
            val n = conn.bulkTransfer(epOut, chunk, len, 5000)
            if (n < 0) throw AdbException("Errore di scrittura USB: controlla il cavo")
            off += n
        }
    }

    private fun readFully(len: Int, timeoutMs: Int): ByteArray {
        val out = ByteArray(len)
        var off = 0
        while (off < len) {
            val want = minOf(readBuf.size, len - off)
            val n = conn.bulkTransfer(epIn, readBuf, want, timeoutMs)
            if (n < 0) throw AdbException("Nessuna risposta dallo smartphone (timeout)")
            System.arraycopy(readBuf, 0, out, off, n)
            off += n
        }
        return out
    }

    private fun readMessage(timeoutMs: Int): Message {
        val h = ByteBuffer.wrap(readFully(24, timeoutMs)).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = h.int
        val a0 = h.int
        val a1 = h.int
        val len = h.int
        h.int // checksum
        val magic = h.int
        if (magic != cmd.inv()) throw AdbException("Pacchetto ADB non valido")
        val data = if (len > 0) readFully(len, timeoutMs) else ByteArray(0)
        return Message(cmd, a0, a1, data)
    }

    private fun checksum(data: ByteArray): Int {
        var s = 0
        for (b in data) s += (b.toInt() and 0xff)
        return s
    }

    companion object {
        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val A_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 4096
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        /** Interfaccia ADB: classe 0xFF, sottoclasse 0x42, protocollo 0x01. */
        fun findAdbInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val itf = device.getInterface(i)
                if (itf.interfaceClass == 255 && itf.interfaceSubclass == 66 && itf.interfaceProtocol == 1) return itf
            }
            return null
        }

        fun open(manager: UsbManager, device: UsbDevice, keys: AdbKeyStore): AdbUsbConnection {
            val itf = findAdbInterface(device)
                ?: throw AdbException("Debug USB non attivo sullo smartphone")
            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
                }
            }
            if (epIn == null || epOut == null) throw AdbException("Endpoint USB non trovati")
            val conn = manager.openDevice(device) ?: throw AdbException("Impossibile aprire il dispositivo USB")
            if (!conn.claimInterface(itf, true)) {
                conn.close()
                throw AdbException("Interfaccia USB occupata")
            }
            return AdbUsbConnection(conn, itf, epIn, epOut, keys)
        }
    }
}
