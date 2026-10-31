package app.videogir

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.Request as OkRequest

/** دانلود فایل به‌صورت تکه‌تکه (چون یوتیوب دانلودهای یک‌تکه و بزرگ را کند می‌کند). */
object Fetcher {
    private const val CHUNK = 8L * 1024 * 1024

    fun uaFor(url: String): String = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
        else -> Http.DEFAULT_UA
    }

    /** طول فایل از پارامتر clen داخل لینک؛ اگر نبود -1. */
    fun clen(url: String): Long =
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    fun length(url: String): Long {
        val c = clen(url)
        if (c > 0) return c
        return try {
            val req = OkRequest.Builder().url(url).head().header("User-Agent", uaFor(url)).build()
            Http.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.header("Content-Length")?.toLongOrNull() ?: -1L else -1L
            }
        } catch (e: IOException) {
            -1L
        }
    }

    /** onBytes همیشه «مجموع بایت‌های دانلودشده تا الان» را می‌گیرد. */
    suspend fun download(url: String, dest: File, onBytes: (Long) -> Unit) {
        val ua = uaFor(url)
        val total = length(url)
        dest.delete()
        FileOutputStream(dest).use { out ->
            if (total <= 0) {
                fetch(url, ua, out, 0L, onBytes)
                return
            }
            var pos = 0L
            while (pos < total) {
                val end = minOf(pos + CHUNK, total) - 1
                val sep = if ('?' in url) "&" else "?"
                val chunkUrl = "$url${sep}range=$pos-$end"
                var attempt = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    try {
                        val n = fetch(chunkUrl, ua, out, pos, onBytes)
                        if (n != end - pos + 1) throw IOException("short chunk")
                        pos += n
                        break
                    } catch (e: IOException) {
                        attempt++
                        if (attempt >= 4) throw e
                        out.channel.truncate(pos)
                        out.channel.position(pos)
                        onBytes(pos)
                        delay(800L * attempt)
                    }
                }
            }
        }
    }

    private suspend fun fetch(
        url: String, ua: String, out: FileOutputStream, base: Long, onBytes: (Long) -> Unit
    ): Long {
        val req = OkRequest.Builder().url(url).header("User-Agent", ua).build()
        return Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val buf = ByteArray(64 * 1024)
            var n = 0L
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    n += r
                    onBytes(base + n)
                }
            }
            n
        }
    }
}
