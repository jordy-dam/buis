package nl.jordy.iptv.mock

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Dev-only Xtream Codes look-alike. Serves the messy fixtures from resources plus generated bulk
 * items, so the app sees both the edge cases and a realistically large catalog.
 *
 * Login: demo / demo. Run: `./gradlew :mockserver:run` (args: --port, --bind, --live, --vod,
 * --series, --latency). The emulator reaches it at http://10.0.2.2:8080.
 */
private const val USER = "demo"
private const val PASS = "demo"

private data class Config(
    val port: Int = 8080,
    val bind: String = "127.0.0.1",
    val live: Int = 1500,
    val vod: Int = 10_000,
    val series: Int = 2_000,
    val latencyMs: Long = 0,
    val mediaDir: File = File("mockserver/media"),
)

private val json = Json { prettyPrint = false }

fun main(args: Array<String>) {
    val cfg = parseArgs(args)
    require(cfg.mediaDir.isDirectory) { "No media in ${cfg.mediaDir.absolutePath}; run mockserver/generate-media.sh first" }
    val catalog = Catalog(cfg)
    val server = HttpServer.create(InetSocketAddress(cfg.bind, cfg.port), 0)
    server.executor = Executors.newFixedThreadPool(16)
    server.createContext("/") { ex ->
        try {
            if (cfg.latencyMs > 0) Thread.sleep(cfg.latencyMs)
            route(ex, cfg, catalog)
        } catch (t: Throwable) {
            t.printStackTrace()
            runCatching { send(ex, 500, "text/plain", "error".toByteArray()) }
        } finally {
            ex.close()
        }
    }
    server.start()
    println("Mock Xtream on http://${cfg.bind}:${cfg.port}  (login $USER/$PASS, live=${cfg.live} vod=${cfg.vod} series=${cfg.series})")
}

private fun parseArgs(args: Array<String>): Config {
    var c = Config()
    args.toList().chunked(2).forEach { (k, v) ->
        c = when (k) {
            "--port" -> c.copy(port = v.toInt())
            "--bind" -> c.copy(bind = v)
            "--live" -> c.copy(live = v.toInt())
            "--vod" -> c.copy(vod = v.toInt())
            "--series" -> c.copy(series = v.toInt())
            "--latency" -> c.copy(latencyMs = v.toLong())
            "--media" -> c.copy(mediaDir = File(v))
            else -> error("Unknown arg $k")
        }
    }
    return c
}

private fun route(ex: HttpExchange, cfg: Config, catalog: Catalog) {
    val path = ex.requestURI.path
    val query = parseQuery(ex.requestURI.rawQuery)
    val base = "http://" + (ex.requestHeaders.getFirst("Host") ?: "${cfg.bind}:${cfg.port}")
    println("${ex.requestMethod} ${path.redactCredentials()}${query["action"]?.let { " action=$it" } ?: ""}")

    when {
        path == "/player_api.php" -> {
            if (query["username"] != USER || query["password"] != PASS) {
                return sendJson(ex, """{"user_info":{"auth":0}}""")
            }
            val body = when (query["action"]) {
                null -> fixture("account.json")
                "get_live_categories" -> catalog.liveCategories()
                "get_live_streams" -> catalog.liveStreams(query["category_id"])
                "get_vod_categories" -> catalog.vodCategories()
                "get_vod_streams" -> catalog.vodStreams(query["category_id"])
                "get_vod_info" -> catalog.vodInfo(query["vod_id"]?.toIntOrNull() ?: 0)
                "get_series_categories" -> catalog.seriesCategories()
                "get_series" -> catalog.series(query["category_id"])
                "get_series_info" -> catalog.seriesInfo(query["series_id"]?.toIntOrNull() ?: 0)
                "get_short_epg" -> catalog.shortEpg(query["stream_id"]?.toIntOrNull() ?: 0, query["limit"]?.toIntOrNull() ?: 4)
                else -> "[]"
            }
            sendJson(ex, body.replace("{base}", base))
        }

        path.startsWith("/img/") -> sendFile(ex, File(cfg.mediaDir, "img/" + path.removePrefix("/img/")), "image/png", cfg.mediaDir)

        path.startsWith("/media/live/") -> {
            val rel = path.removePrefix("/media/live/")
            sendFile(ex, File(cfg.mediaDir, "live/$rel"), "video/mp2t", cfg.mediaDir)
        }

        path.startsWith("/live/") -> {
            val parts = path.split('/') // "", live, u, p, file
            if (parts.size != 5 || parts[2] != USER || parts[3] != PASS) return send(ex, 403, "text/plain", ByteArray(0))
            val id = parts[4].substringBefore('.').toIntOrNull() ?: return send(ex, 404, "text/plain", ByteArray(0))
            val variant = id % 4
            when (parts[4].substringAfter('.')) {
                // Every channel ending in 7 has no HLS, to exercise the .m3u8 -> .ts fallback.
                "m3u8" -> if (id % 10 == 7) {
                    send(ex, 404, "text/plain", ByteArray(0))
                } else {
                    val playlist = File(cfg.mediaDir, "live/$variant/index.m3u8").readLines().joinToString("\n") { line ->
                        if (line.endsWith(".ts")) "/media/live/$variant/$line" else line
                    }
                    send(ex, 200, "application/vnd.apple.mpegurl", playlist.toByteArray())
                }
                "ts" -> sendFile(ex, File(cfg.mediaDir, "live/$variant/stream.ts"), "video/mp2t", cfg.mediaDir)
                else -> send(ex, 404, "text/plain", ByteArray(0))
            }
        }

        path.startsWith("/movie/") || path.startsWith("/series/") -> {
            val parts = path.split('/')
            if (parts.size != 5 || parts[2] != USER || parts[3] != PASS) return send(ex, 403, "text/plain", ByteArray(0))
            sendFile(ex, File(cfg.mediaDir, "movie.mp4"), "video/mp4", cfg.mediaDir)
        }

        else -> send(ex, 404, "text/plain", ByteArray(0))
    }
}

private class Catalog(private val cfg: Config) {
    private val liveCatNames = listOf(
        "NL | Algemeen HD", "NL | Regionaal", "NL | Kids", "NL | Muziek", "NL | Documentaires",
        "BE | Vlaams", "UK | News", "UK | Sports", "DE | Allgemein", "FR | Général",
        "|| ===== 24/7 ===== ||", "PPV | Events", "ADULT ---", "TR | Ulusal", "AR | MBC",
    )
    private val vodCatNames = listOf("Nieuw", "Actie", "Komedie", "Drama", "Thriller", "Animatie", "Documentaire", "4K", "Kerst", "Multi-Sub")
    private val seriesCatNames = listOf("Netflix", "HBO", "Disney+", "Nederlandse series", "Kids", "Anime")

    fun liveCategories() = merge(fixture("live_categories.json"), generatedCategories(100, liveCatNames))
    fun vodCategories() = merge(fixture("vod_categories.json"), generatedCategories(200, vodCatNames))
    fun seriesCategories() = merge(fixture("series_categories.json"), generatedCategories(300, seriesCatNames))

    fun liveStreams(categoryId: String?) = filtered(
        merge(fixture("live_streams.json"), buildJsonArray {
            repeat(cfg.live) { i ->
                val id = 1000 + i
                add(buildJsonObject {
                    put("num", i + 1)
                    put("name", "Kanaal ${i + 1}" + if (i % 3 == 0) " HD" else "")
                    put("stream_type", "live")
                    // Mix of numbers and strings, as real panels do.
                    if (i % 2 == 0) put("stream_id", id) else put("stream_id", id.toString())
                    put("stream_icon", if (i % 9 == 0) "" else "{base}/img/logo${i % 6}.png")
                    put("epg_channel_id", "kanaal$id")
                    put("added", "1700000000")
                    put("category_id", (100 + i % liveCatNames.size).toString())
                })
            }
        }),
        categoryId,
    )

    fun vodStreams(categoryId: String?) = filtered(
        merge(fixture("vod_streams.json"), buildJsonArray {
            repeat(cfg.vod) { i ->
                add(buildJsonObject {
                    put("num", i + 1)
                    put("name", "Film ${i + 1} (${1980 + i % 45})")
                    put("stream_type", "movie")
                    put("stream_id", 10_000 + i)
                    put("stream_icon", "{base}/img/poster${i % 6}.png")
                    put("rating", if (i % 7 == 0) "" else "%.1f".format(java.util.Locale.ROOT, 4 + (i % 50) / 10.0))
                    put("added", (1_700_000_000 + i * 600).toString())
                    put("category_id", (200 + i % vodCatNames.size).toString())
                    put("container_extension", if (i % 4 == 0) "mkv" else "mp4")
                })
            }
        }),
        categoryId,
    )

    fun series(categoryId: String?) = filtered(
        merge(fixture("series.json"), buildJsonArray {
            repeat(cfg.series) { i ->
                add(buildJsonObject {
                    put("num", i + 1)
                    put("name", "Serie ${i + 1}")
                    put("series_id", 20_000 + i)
                    put("cover", "{base}/img/poster${i % 6}.png")
                    put("plot", "Gegenereerde serie nummer ${i + 1}.")
                    put("genre", listOf("Drama", "Komedie", "Misdaad", "Animatie")[i % 4])
                    put("releaseDate", "${1995 + i % 30}-01-01")
                    put("rating", "%.1f".format(java.util.Locale.ROOT, 5 + (i % 40) / 10.0))
                    put("category_id", (300 + i % seriesCatNames.size).toString())
                    put("backdrop_path", buildJsonArray { add(JsonPrimitive("{base}/img/backdrop${i % 6}.png")) })
                })
            }
        }),
        categoryId,
    )

    fun vodInfo(vodId: Int): String {
        if (vodId == 5003) return fixture("vod_info_empty.json")
        val root = json.parseToJsonElement(fixture("vod_info.json")).jsonObject
        val movie = root["movie_data"]!!.jsonObject.toMutableMap()
        movie["stream_id"] = JsonPrimitive(vodId)
        return JsonObject(root + ("movie_data" to JsonObject(movie))).toString()
    }

    fun seriesInfo(seriesId: Int): String = when {
        seriesId == 7001 -> fixture("series_info.json")
        seriesId % 5 == 0 -> fixture("series_info_list_episodes.json")
        else -> {
            val seasons = 1 + seriesId % 4
            buildJsonObject {
                put("seasons", buildJsonArray {
                    for (s in 1..seasons) add(buildJsonObject { put("season_number", s); put("name", "Seizoen $s") })
                })
                put("info", buildJsonObject {
                    put("name", "Serie $seriesId")
                    put("plot", "Gegenereerde serie.")
                    put("cover", "{base}/img/poster${seriesId % 6}.png")
                    put("backdrop_path", buildJsonArray { add(JsonPrimitive("{base}/img/backdrop${seriesId % 6}.png")) })
                })
                put("episodes", buildJsonObject {
                    for (s in 1..seasons) put(s.toString(), buildJsonArray {
                        for (e in 1..(6 + s)) add(buildJsonObject {
                            put("id", (seriesId * 100 + s * 20 + e).toString())
                            put("episode_num", e)
                            put("title", "Serie $seriesId - S%02dE%02d".format(s, e))
                            put("container_extension", "mp4")
                            put("season", s)
                            put("info", buildJsonObject { put("plot", "Aflevering $e van seizoen $s."); put("duration_secs", 180) })
                        })
                    })
                })
            }.toString()
        }
    }

    /** Programmes in 30-minute slots around "now", titles base64 as real panels send them. */
    fun shortEpg(streamId: Int, limit: Int): String {
        val slot = 30 * 60L
        val start = System.currentTimeMillis() / 1000 / slot * slot
        fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
        return buildJsonObject {
            put("epg_listings", buildJsonArray {
                repeat(limit) { i ->
                    val from = start + i * slot
                    add(buildJsonObject {
                        put("id", "$streamId$i")
                        put("title", b64(listOf("Journaal", "Weerbericht", "Documentaire", "Talkshow", "Film", "Sport")[(streamId + i) % 6] + " $streamId"))
                        put("description", b64("Programma-informatie voor kanaal $streamId."))
                        put("start_timestamp", from.toString())
                        put("stop_timestamp", (from + slot).toString())
                    })
                }
            })
        }.toString()
    }

    private fun generatedCategories(firstId: Int, names: List<String>) = buildJsonArray {
        names.forEachIndexed { i, n -> add(buildJsonObject { put("category_id", (firstId + i).toString()); put("category_name", n); put("parent_id", 0) }) }
    }

    private fun merge(fixtureJson: String, generated: JsonArray): String =
        JsonArray(json.parseToJsonElement(fixtureJson).jsonArray + generated).toString()

    private fun filtered(all: String, categoryId: String?): String {
        if (categoryId == null) return all
        return JsonArray(json.parseToJsonElement(all).jsonArray.filter { el ->
            (el as? JsonObject)?.get("category_id")?.let { (it as? JsonPrimitive)?.content } == categoryId
        }).toString()
    }
}

private fun fixture(name: String): String =
    Catalog::class.java.getResource("/fixtures/$name")?.readText() ?: error("Missing fixture $name")

private fun parseQuery(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').filter { it.contains('=') }.associate {
        URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

private fun String.redactCredentials() = replace("/$USER/$PASS/", "/***/***/")

private fun sendJson(ex: HttpExchange, body: String) = send(ex, 200, "application/json", body.toByteArray())

private fun send(ex: HttpExchange, status: Int, type: String, body: ByteArray) {
    ex.responseHeaders.add("Content-Type", type)
    ex.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
    if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
}

/** Serves a file with single-range support, which ExoPlayer needs to seek (and so to resume) in MP4. */
private fun sendFile(ex: HttpExchange, file: File, type: String, mediaRoot: File) {
    if (!file.isFile || !file.canonicalPath.startsWith(mediaRoot.canonicalPath)) {
        return send(ex, 404, "text/plain", ByteArray(0))
    }
    val length = file.length()
    val range = ex.requestHeaders.getFirst("Range")?.let { Regex("bytes=(\\d*)-(\\d*)").find(it) }
    ex.responseHeaders.add("Content-Type", type)
    ex.responseHeaders.add("Accept-Ranges", "bytes")
    val (from, to) = if (range != null) {
        val s = range.groupValues[1].toLongOrNull() ?: 0
        val e = range.groupValues[2].toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
        s to e
    } else 0L to length - 1
    if (from > to || from >= length) {
        ex.responseHeaders.add("Content-Range", "bytes */$length")
        return ex.sendResponseHeaders(416, -1)
    }
    val count = to - from + 1
    if (range != null) ex.responseHeaders.add("Content-Range", "bytes $from-$to/$length")
    ex.sendResponseHeaders(if (range != null) 206 else 200, count)
    file.inputStream().use { input ->
        input.skipNBytes(from)
        ex.responseBody.use { out ->
            val buf = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
    }
}
