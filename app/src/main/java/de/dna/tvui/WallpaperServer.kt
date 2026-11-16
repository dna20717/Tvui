package de.dna.tvui

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Winziger Webserver im lokalen Netz: Das iPad öffnet die Seite,
 * wählt ein Foto und schickt es direkt an den Fernseher.
 * Läuft nur, solange die Hintergrund-Einstellungen offen sind.
 */
class WallpaperServer(
    private val assets: AssetManager,
    private val onUpload: (ByteArray) -> Boolean,
    private val onPlay: (Map<String, String>) -> Boolean,
) {
    @Volatile
    private var socket: ServerSocket? = null

    val isRunning get() = socket?.isClosed == false

    @Throws(IOException::class)
    fun start() {
        if (isRunning) return
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(PORT))
        socket = s
        thread(isDaemon = true, name = "tvui-server") {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: IOException) { break }
                thread(isDaemon = true) { handle(client) }
            }
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }

    private fun handle(client: Socket) {
        try {
            client.use { c ->
                c.soTimeout = 30_000
                val input = BufferedInputStream(c.getInputStream())
                val out = c.getOutputStream()
                val requestLine = readLine(input) ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                val parts = requestLine.split(" ")
                val method = parts.getOrElse(0) { "" }
                val target = parts.getOrElse(1) { "/" }
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))

                when {
                    method == "GET" && path == "/" -> {
                        val base = "http://" + (headers["host"] ?: "$PORT")
                        val page = assets.open("upload.html").use { it.readBytes() }
                            .toString(Charsets.UTF_8).replace("{{TV}}", base)
                        respond(out, 200, "text/html; charset=utf-8", page.toByteArray())
                    }
                    path == "/play" -> {
                        val ok = onPlay(query)
                        val msg = if (ok) "Läuft auf dem Fernseher 📺" else "Nichts zum Abspielen gefunden 😕"
                        respond(out, if (ok) 200 else 400, "text/plain; charset=utf-8", msg.toByteArray())
                    }
                    method == "POST" && path == "/upload" -> {
                        val len = headers["content-length"]?.toIntOrNull() ?: -1
                        if (len <= 0 || len > MAX_UPLOAD) {
                            respond(out, 413, "text/plain", "zu gross".toByteArray())
                            return
                        }
                        val body = ByteArray(len)
                        var read = 0
                        while (read < len) {
                            val n = input.read(body, read, len - read)
                            if (n < 0) break
                            read += n
                        }
                        val ok = read == len && onUpload(body)
                        respond(out, if (ok) 200 else 400, "text/plain", (if (ok) "ok" else "fehler").toByteArray())
                    }
                    else -> respond(out, 404, "text/plain", "nicht gefunden".toByteArray())
                }
            }
        } catch (_: Exception) {
            // Verbindung abgebrochen – egal, das iPad kann es nochmal versuchen.
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }.associate {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=')
            fun dec(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }
            dec(k) to dec(v)
        }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 413 -> "Payload Too Large"; else -> "OK"
        }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    companion object {
        const val PORT = 8765
        private const val MAX_UPLOAD = 30 * 1024 * 1024

        /** IPv4-Adresse des Fernsehers im Heimnetz (WLAN oder Kabel). */
        fun localIp(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }

        fun qr(text: String, size: Int): Bitmap {
            val m = QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, size, size,
                mapOf(EncodeHintType.MARGIN to 0),
            )
            val px = IntArray(size * size) { i ->
                if (m[i % size, i / size]) Color.BLACK else Color.WHITE
            }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
