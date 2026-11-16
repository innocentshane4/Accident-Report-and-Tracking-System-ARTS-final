package com.example.arts.utils

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import java.security.SecureRandom
import java.util.Date

/**
 * Wraps dev.turingcomplete:kotlin-onetimepassword's GoogleAuthenticator
 * decorator to give the app simple functions for TOTP-based 2FA.
 */
object TotpHelper {

    private const val STEP_MILLIS = 30_000L

    /** Generates a new random 20-byte secret, Base32-encoded for authenticator apps. */
    fun generateSecret(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        return base32Encode(bytes)
    }

    /** Builds the otpauth:// URI an authenticator app can scan/import. */
    fun buildOtpAuthUri(secret: String, accountEmail: String): String {
        val issuer = "ARTS"
        val label = "$issuer:$accountEmail"
        return "otpauth://totp/$label?secret=$secret&issuer=$issuer&digits=6&period=30"
    }

    /** Returns the currently valid 6-digit code for the given secret. */
    fun currentCode(secret: String): String {
        return GoogleAuthenticator(base32Decode(secret)).generate(Date())
    }

    /**
     * Verifies a user-entered code, allowing one 30s step of clock drift
     * in either direction.
     */
    fun verifyCode(secret: String, code: String): Boolean {
        if (code.length != 6 || code.any { !it.isDigit() }) return false
        val now = System.currentTimeMillis()
        val authenticator = GoogleAuthenticator(base32Decode(secret))
        return (-1..1).any { drift ->
            authenticator.isValid(code, Date(now + drift * STEP_MILLIS))
        }
    }

    // --- Minimal RFC 4648 Base32 codec ---

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Encode(data: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                output.append(BASE32_ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            output.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return output.toString()
    }

    private fun base32Decode(base32: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (c in base32.uppercase()) {
            val charValue = BASE32_ALPHABET.indexOf(c)
            if (charValue < 0) continue
            buffer = (buffer shl 5) or charValue
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                bytes.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return bytes.toByteArray()
    }
}
