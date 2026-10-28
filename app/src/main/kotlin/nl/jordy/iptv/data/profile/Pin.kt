package nl.jordy.iptv.data.profile

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Profile PINs: four digits, stored as `salt:sha256(salt + pin)` in hex.
 *
 * This keeps children out of a parent's profile; it is not security. Four digits fall to brute
 * force in milliseconds for anyone with the database file, which on an unrooted tv nobody has.
 */
object Pin {
    const val LENGTH = 4

    fun isValid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        require(isValid(pin)) { "PIN must be $LENGTH digits" }
        return salt.hex() + ":" + digest(salt, pin).hex()
    }

    fun verify(pin: String, stored: String): Boolean {
        val (salt, expected) = stored.split(':').takeIf { it.size == 2 } ?: return false
        if (!isValid(pin)) return false
        return MessageDigest.isEqual(digest(salt.unhex(), pin), expected.unhex())
    }

    private fun digest(salt: ByteArray, pin: String) =
        MessageDigest.getInstance("SHA-256").run { update(salt); digest(pin.toByteArray()) }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
