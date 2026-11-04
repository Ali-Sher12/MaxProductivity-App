package com.maxproductivity.app.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores passwords as PBKDF2-HMAC-SHA256 hashes. The plain password is never persisted.
 * Format: iterations:saltBase64:hashBase64
 */
object PasswordUtils {

    private const val DEFAULT_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, DEFAULT_ITERATIONS)
        return "$DEFAULT_ITERATIONS:${salt.toBase64()}:${hash.toBase64()}"
    }

    fun verify(password: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = parts[1].fromBase64() ?: return false
        val expected = parts[2].fromBase64() ?: return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    fun needsRehash(stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        return iterations > DEFAULT_ITERATIONS
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray? =
        try {
            Base64.decode(this, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            null
        }
}
