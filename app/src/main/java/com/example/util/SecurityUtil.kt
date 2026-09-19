package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object SecurityUtil {
    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray())
        val hashedBytes = md.digest(password.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val hash = hashPassword(password, salt)
        return hash.equals(expectedHash, ignoreCase = true)
    }

    fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest(pin.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, expectedHash: String): Boolean {
        return hashPin(pin).equals(expectedHash, ignoreCase = true)
    }
}
