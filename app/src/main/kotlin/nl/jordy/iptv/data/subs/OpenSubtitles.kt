package nl.jordy.iptv.data.subs

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.jordy.iptv.BuildConfig
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.xtream.int
import nl.jordy.iptv.data.xtream.obj
import nl.jordy.iptv.data.xtream.str
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The household's OpenSubtitles setup: the API key (made once on opensubtitles.com, it says which app
 * asks) and the account whose daily downloads are used.
 */
@Serializable
data class OpenSubtitlesLogin(val apiKey: String, val username: String, val password: String) {
    override fun toString() = "OpenSubtitlesLogin(apiKey=***, username=$username, password=***)"
}

/** What's playing, the way OpenSubtitles searches for it. */
sealed interface SubtitleQuery {
    data class Film(val title: String, val year: Int?, val tmdbId: Int?) : SubtitleQuery
    data class Episode(val series: String, val season: Int, val episode: Int) : SubtitleQuery
}

/** One subtitle file OpenSubtitles has for the title; [release] is the video it was timed to. */
data class SubtitleHit(val fileId: Int, val language: String, val downloads: Int, val release: String? = null)

/** Says in Dutch what went wrong, for the settings dialog and the player. */
class OpenSubtitlesException(message: String) : IOException(message)

/**
 * Subtitles from OpenSubtitles for films and episodes whose stream has none (or not in the wanted
 * language). Searching is free; each file downloaded counts against the account's daily limit, so a
 * file is only fetched when picked in the player, and kept in the cache after that.
 */
@Singleton
class OpenSubtitles @Inject constructor(
    credentials: CredentialStore,
    private val http: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val store = credentials.store("opensubtitles.bin", OpenSubtitlesLogin.serializer(), "opensubtitles-login")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var token: String? = null

    val login: Flow<OpenSubtitlesLogin?> = store.data

    /** Signs in first, so a typo is said right away instead of when a film is playing. */
    suspend fun save(login: OpenSubtitlesLogin) {
        token = signIn(login)
        store.updateData { login }
    }

    suspend fun clear() {
        token = null
        store.updateData { null }
    }

    /**
     * The best files per language, most downloaded first; empty without an account. Each way of
     * asking in [attempts] is tried until one finds something: a panel's TMDB id is sometimes wrong.
     */
    suspend fun search(query: SubtitleQuery, languages: List<String>): List<SubtitleHit> {
        val login = login.first() ?: return emptyList<SubtitleHit>().also { Log.i(TAG, "search: no account") }
        if (languages.isEmpty()) return emptyList()
        for (q in attempts(query)) {
            val body = call(Request.Builder().url(searchUrl(API, q, languages)).get(), login)
            val hits = OpenSubtitlesParser.best(json.parseToJsonElement(body), languages)
            Log.i(TAG, "search $q: ${hits.size} files")
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    /** The file as SRT in the cache; downloads it (one of the day's downloads) only the first time. */
    suspend fun download(hit: SubtitleHit): File {
        val file = File(context.cacheDir, "subtitles/${hit.fileId}.srt")
        if (file.length() > 0) return file
        val request = Request.Builder().url(API.newBuilder().addPathSegment("download").build()).post(
            buildJsonObject { put("file_id", hit.fileId); put("sub_format", "srt") }.toString().toRequestBody(JSON),
        )
        val login = login.first() ?: throw OpenSubtitlesException("Geen OpenSubtitles-account ingesteld.")
        val response = json.parseToJsonElement(call(request, login, signedIn = true)) as? JsonObject
        val link = response?.str("link") ?: throw OpenSubtitlesException(response?.str("message") ?: "OpenSubtitles gaf geen bestand.")
        val text = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(link).build()).execute().use { r ->
                if (!r.isSuccessful) throw OpenSubtitlesException("Ondertitels ophalen lukte niet (${r.code}).")
                r.body.bytes()
            }
        }
        file.parentFile?.mkdirs()
        file.writeBytes(text)
        return file
    }

    private suspend fun signIn(login: OpenSubtitlesLogin): String {
        val body = buildJsonObject { put("username", login.username); put("password", login.password) }.toString()
        val response = call(Request.Builder().url(API.newBuilder().addPathSegment("login").build()).post(body.toRequestBody(JSON)), login)
        return (json.parseToJsonElement(response) as? JsonObject)?.str("token") ?: throw OpenSubtitlesException("OpenSubtitles gaf geen toegang.")
    }

    /** One API call; with [signedIn] it carries the account's token, signing in again once when it ran out. */
    private suspend fun call(builder: Request.Builder, login: OpenSubtitlesLogin, signedIn: Boolean = false, retry: Boolean = true): String {
        builder.header("Api-Key", login.apiKey)
            .header("User-Agent", "BUIS v${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/json")
        if (signedIn) {
            builder.header("Authorization", "Bearer ${token ?: signIn(login).also { token = it }}")
        }
        val request = builder.build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { r ->
                    when {
                        r.isSuccessful -> r.body.string()
                        r.code == 401 && signedIn && retry -> null
                        r.code == 401 -> throw OpenSubtitlesException("Gebruikersnaam of wachtwoord klopt niet.")
                        r.code == 403 -> throw OpenSubtitlesException("OpenSubtitles kent deze API-sleutel niet.")
                        r.code == 406 -> throw OpenSubtitlesException("De downloads van vandaag zijn op.")
                        r.code == 429 -> throw OpenSubtitlesException("OpenSubtitles is even te druk, probeer het zo nog eens.")
                        else -> throw OpenSubtitlesException("OpenSubtitles antwoordde met ${r.code}.")
                    }
                }
            } catch (e: OpenSubtitlesException) {
                throw e
            } catch (e: IOException) {
                throw OpenSubtitlesException("OpenSubtitles is niet bereikbaar.")
            }
        } ?: run {
            token = null
            call(builder, login, signedIn, retry = false)
        }
    }

    internal companion object {
        private const val TAG = "OpenSubtitles"
        val API = "https://api.opensubtitles.com/api/v1/".toHttpUrl()

        /** A film by its TMDB id, then by title and year, then by title alone; an episode as it is. */
        fun attempts(query: SubtitleQuery): List<SubtitleQuery> = when (query) {
            is SubtitleQuery.Film -> listOfNotNull(
                query.takeIf { (it.tmdbId ?: 0) > 0 },
                query.copy(tmdbId = null),
                query.copy(tmdbId = null, year = null).takeIf { query.year != null },
            )
            is SubtitleQuery.Episode -> listOf(query)
        }
        private val JSON = "application/json".toMediaType()

        /**
         * The search, with its parameters sorted and in lower case: OpenSubtitles redirects any
         * other spelling to this one first. A film goes by its TMDB id when the panel sent one.
         */
        fun searchUrl(api: HttpUrl, query: SubtitleQuery, languages: List<String>): HttpUrl {
            val params = sortedMapOf("languages" to languages.distinct().sorted().joinToString(","))
            when (query) {
                is SubtitleQuery.Film -> {
                    params["type"] = "movie"
                    if (query.tmdbId != null && query.tmdbId > 0) params["tmdb_id"] = query.tmdbId.toString()
                    else {
                        params["query"] = query.title.lowercase()
                        query.year?.let { params["year"] = it.toString() }
                    }
                }
                is SubtitleQuery.Episode -> {
                    params["type"] = "episode"
                    params["query"] = query.series.lowercase()
                    params["season_number"] = query.season.toString()
                    params["episode_number"] = query.episode.toString()
                }
            }
            return api.newBuilder().addPathSegment("subtitles").apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        }
    }
}

internal object OpenSubtitlesParser {
    /** Per language (in the order asked) the two most downloaded files. */
    fun best(root: JsonElement, languages: List<String>, perLanguage: Int = 2): List<SubtitleHit> {
        val data = (root as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        val hits = data.mapNotNull { item ->
            val a = (item as? JsonObject)?.obj("attributes") ?: return@mapNotNull null
            val file = (a["files"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            SubtitleHit(
                file.int("file_id") ?: return@mapNotNull null,
                a.str("language")?.lowercase() ?: return@mapNotNull null,
                a.int("download_count") ?: 0,
                a.str("release"),
            )
        }
        return languages.distinct().flatMap { lang -> hits.filter { it.language == lang }.sortedByDescending { it.downloads }.take(perLanguage) }
    }
}
