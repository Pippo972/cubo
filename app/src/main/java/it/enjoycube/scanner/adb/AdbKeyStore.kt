package it.enjoycube.scanner.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Chiave RSA del tablet usata per l'autorizzazione ADB.
 * Viene generata una sola volta e salvata nella memoria privata dell'app:
 * se sullo smartphone si sceglie "Consenti sempre", le volte successive
 * non verrà più chiesta la conferma.
 */
class AdbKeyStore(context: Context) {

    private val privFile = File(context.filesDir, "adbkey")
    private val pubFile = File(context.filesDir, "adbkey.pub")
    private val keyPair: KeyPair

    init {
        keyPair = loadKeys() ?: generateKeys()
    }

    private fun loadKeys(): KeyPair? = try {
        if (privFile.exists() && pubFile.exists()) {
            val kf = KeyFactory.getInstance("RSA")
            KeyPair(
                kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes())),
                kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
            )
        } else null
    } catch (e: Exception) {
        null
    }

    private fun generateKeys(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val kp = gen.generateKeyPair()
        privFile.writeBytes(kp.private.encoded)
        pubFile.writeBytes(kp.public.encoded)
        return kp
    }

    /** Firma il token inviato dallo smartphone (il token è già un digest SHA-1). */
    fun sign(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
        cipher.update(SHA1_DIGEST_INFO)
        return cipher.doFinal(token)
    }

    /** Chiave pubblica nel formato che il demone adbd di Android si aspetta. */
    fun adbPublicKey(): ByteArray {
        val pub = keyPair.public as RSAPublicKey
        val n = pub.modulus
        val words = 64 // 2048 bit / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
        val rr = BigInteger.ONE.shiftLeft(4096).mod(n)

        val bb = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(words)
        bb.putInt(n0inv.toInt())
        putWords(bb, n, words)
        putWords(bb, rr, words)
        bb.putInt(pub.publicExponent.toInt())

        val b64 = Base64.encodeToString(bb.array(), Base64.NO_WRAP)
        return "$b64 enjoycube@tablet\u0000".toByteArray(Charsets.US_ASCII)
    }

    private fun putWords(bb: ByteBuffer, value: BigInteger, words: Int) {
        val mask = BigInteger.valueOf(0xffffffffL)
        var x = value
        repeat(words) {
            bb.putInt(x.and(mask).toInt())
            x = x.shiftRight(32)
        }
    }

    companion object {
        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14
        )
    }
}
