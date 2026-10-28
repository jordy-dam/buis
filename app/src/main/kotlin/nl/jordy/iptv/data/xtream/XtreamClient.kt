package nl.jordy.iptv.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data class Success(val account: Account) : LoginResult
    data object InvalidUrl : LoginResult
    data object WrongCredentials : LoginResult
    data class Inactive(val status: String?) : LoginResult
    data class Unreachable(val cause: String) : LoginResult
    data object NotXtream : LoginResult
}

@Singleton
class XtreamClient @Inject constructor(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun login(c: XtreamCredentials, nowEpochSec: Long = System.currentTimeMillis() / 1000): LoginResult {
        if (c.baseUrl == null) return LoginResult.InvalidUrl
        val account = try {
            XtreamParser.account(get(c))
        } catch (e: SerializationException) {
            return LoginResult.NotXtream
        } catch (e: IOException) {
            return LoginResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
        return when {
            !account.authenticated -> LoginResult.WrongCredentials
            account.status != null && !account.status.equals("Active", ignoreCase = true) -> LoginResult.Inactive(account.status)
            account.expiresAt != null && account.expiresAt in 1 until nowEpochSec -> LoginResult.Inactive("Expired")
            else -> LoginResult.Success(account)
        }
    }

    /** Small responses (account, info, EPG): parsed fully. */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun get(c: XtreamCredentials, action: String? = null, vararg params: Pair<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            execute(apiUrl(c, action, params)).use { resp ->
                json.decodeFromStream<JsonElement>(resp.body.byteStream())
            }
        }

    /**
     * Large arrays (10k+ items): decoded element by element straight from the socket, so the
     * whole catalog is never held in memory. A non-array body (some panels answer `{}`) yields nothing.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun forEachInArray(c: XtreamCredentials, action: String, onItem: suspend (JsonElement) -> Unit) =
        withContext(Dispatchers.IO) {
            execute(apiUrl(c, action, emptyArray())).use { resp ->
                val stream = resp.body.byteStream().buffered()
                stream.mark(PEEK_LIMIT)
                val first = generateSequence { stream.read().takeIf { it >= 0 } }.take(PEEK_LIMIT - 1)
                    .firstOrNull { !it.toChar().isWhitespace() }
                stream.reset()
                if (first?.toChar() != '[') return@use
                json.decodeToSequence<JsonElement>(stream, DecodeSequenceMode.ARRAY_WRAPPED).forEach { onItem(it) }
            }
        }

    private fun apiUrl(c: XtreamCredentials, action: String?, params: Array<out Pair<String, String>>): HttpUrl {
        val base = c.baseUrl ?: throw IOException("Ongeldige server-URL")
        return base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
            .apply {
                action?.let { addQueryParameter("action", it) }
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }
            .build()
    }

    private fun execute(url: HttpUrl): Response {
        val resp = http.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("HTTP ${resp.code}")
        }
        return resp
    }

    private companion object {
        const val PEEK_LIMIT = 8192
    }
}
