package nl.jordy.iptv.data.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/*
 * Defensive accessors for Xtream JSON. Providers send numbers as strings, strings as numbers,
 * `null`, "" or an empty array where an object belongs. None of these may throw.
 */

internal fun JsonObject.str(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content.trim().takeIf { it.isNotEmpty() && it != "null" }
}

internal fun JsonObject.long(key: String): Long? {
    val s = str(key) ?: return null
    return s.toLongOrNull() ?: s.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

internal fun JsonObject.int(key: String): Int? = long(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

internal fun JsonObject.double(key: String): Double? = str(key)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** Absolute http(s) URL or null, so the UI can show a placeholder instead of a broken image. */
internal fun JsonObject.url(key: String): String? = str(key)?.let(::validUrl)

/** `backdrop_path` comes as an array, a single string, or an empty array. */
internal fun JsonObject.firstUrl(key: String): String? = when (val v = this[key]) {
    is JsonArray -> v.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.let(::validUrl) }
    is JsonPrimitive -> v.content.let(::validUrl)
    else -> null
}

internal fun validUrl(raw: String): String? {
    val url = raw.trim().toHttpUrlOrNull() ?: return null
    return if (url.host.isNotBlank()) url.toString() else null
}

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
