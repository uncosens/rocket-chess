package com.uncosens.rocketchess.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A public game found online. [key] identifies it across imports, e.g. "lichess:abcd1234". */
data class RemoteGame(
    val key: String,
    val white: String,
    val black: String,
    val endedAt: Long,
    val speed: String?,
    val pgn: String,
)

sealed class FetchError(message: String) : Exception(message) {
    data object BadName : FetchError("bad user name")
    data object NotFound : FetchError("user not found")
    data object Offline : FetchError("no connection")
    data object Timeout : FetchError("timeout")
    data class RateLimited(val retryAfterSec: Int) : FetchError("rate limited")
    data class Server(val code: Int) : FetchError("server error $code")
    data object BadResponse : FetchError("bad response")
}

/** HTTP for both sites. One request at a time; retries only short network and server errors. */
object Http {
    const val USER_AGENT = "RocketChess/0.1 (+https://github.com/uncosens/rocket-chess)"
    private const val MAX_BYTES = 10L * 1024 * 1024
    private const val TRIES = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    /** Maps a non-200 status to an error. 404 and 410 mean the data is not there. */
    fun errorFor(code: Int, retryAfter: String?): FetchError = when (code) {
        404, 410 -> FetchError.NotFound
        429 -> FetchError.RateLimited(retryAfter?.trim()?.toIntOrNull() ?: 60)
        else -> FetchError.Server(code)
    }

    suspend fun get(url: String, accept: String = "application/json"): String {
        var wait = 1000L
        repeat(TRIES) { attempt ->
            try {
                return withContext(Dispatchers.IO) { once(url, accept) }
            } catch (e: FetchError) {
                val retry = e is FetchError.Offline || e is FetchError.Timeout || (e is FetchError.Server && e.code >= 500)
                if (!retry || attempt == TRIES - 1) throw e
            }
            delay(wait)
            wait *= 2
        }
        error("unreachable")
    }

    private fun once(url: String, accept: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", accept).build()
        try {
            client.newCall(request).execute().use { r ->
                if (r.code != 200) throw errorFor(r.code, r.header("Retry-After"))
                val body = r.body.source()
                if (body.request(MAX_BYTES + 1)) throw FetchError.BadResponse // too big
                return body.buffer.readUtf8()
            }
        } catch (e: SocketTimeoutException) {
            throw FetchError.Timeout
        } catch (e: UnknownHostException) {
            throw FetchError.Offline
        } catch (e: IOException) {
            throw FetchError.Offline
        }
    }

    /** Lichess and Chess.com names: letters, digits, '_' and '-'. Checked before it goes into a URL. */
    fun checkName(name: String): String {
        val n = name.trim()
        if (!Regex("^[A-Za-z0-9_-]{2,30}$").matches(n)) throw FetchError.BadName
        return n
    }
}

/** Reads one PGN tag value, e.g. tag(pgn, "Result"). */
fun tag(pgn: String, name: String): String? =
    Regex("^\\[$name \"([^\"]*)\"]", RegexOption.MULTILINE).find(pgn)?.groupValues?.get(1)
