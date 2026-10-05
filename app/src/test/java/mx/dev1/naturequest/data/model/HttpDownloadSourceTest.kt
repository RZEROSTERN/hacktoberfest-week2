package mx.dev1.naturequest.data.model

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.domain.model.DownloadProgress
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.random.Random

/** Runs the real `HttpURLConnection` code against a tiny local server: redirect, Range, errors, dropped connection. */
class HttpDownloadSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val content = Random(7).nextBytes(2 * 1024 * 1024 + 77)
    private lateinit var server: HttpServer
    private val requests = mutableListOf<String?>() // the Range header of every request to /file
    private var dropFirstTransfer = false
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            // Like Hugging Face: the first URL redirects to the real file.
            createContext("/start") { ex -> ex.responseHeaders.add("Location", "/file"); ex.sendResponseHeaders(302, -1); ex.close() }
            createContext("/file") { ex -> serveFile(ex, honorRange = true) }
            createContext("/no-range") { ex -> serveFile(ex, honorRange = false) }
            createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
            createContext("/loop") { ex -> ex.responseHeaders.add("Location", "/loop"); ex.sendResponseHeaders(302, -1); ex.close() }
            start()
        }
    }

    @After
    fun stopServer() = server.stop(0)

    private fun serveFile(ex: HttpExchange, honorRange: Boolean) {
        val range = ex.requestHeaders.getFirst("Range")
        if (ex.requestURI.path == "/file") requests += range
        val from = if (honorRange && range != null) Regex("bytes=(\\d+)-").find(range)!!.groupValues[1].toInt() else 0
        if (honorRange && range != null) {
            ex.responseHeaders.add("Content-Range", "bytes $from-${content.size - 1}/${content.size}")
            ex.sendResponseHeaders(206, (content.size - from).toLong())
        } else {
            ex.sendResponseHeaders(200, content.size.toLong())
        }
        val drop = dropFirstTransfer && requests.size == 1
        ex.responseBody.use { out ->
            if (drop) out.write(content, from, 600_000) else out.write(content, from, content.size - from)
        }
        ex.close()
    }

    private val source get() = HttpDownloadSource(allowInsecure = true, readTimeoutMillis = 5_000)

    @Test
    fun `follows a redirect and reads the whole file`() {
        source.open("$base/start", 0).use { response ->
            assertEquals(200, response.code)
            assertEquals(0L, response.startByte)
            assertEquals(content.size.toLong(), response.totalBytes)
            assertArrayEquals(content, response.body.readBytes())
        }
    }

    @Test
    fun `sends the Range header through the redirect and reads the 206 answer`() {
        source.open("$base/start", 1_000).use { response ->
            assertEquals(206, response.code)
            assertEquals(1_000L, response.startByte)
            assertEquals(content.size.toLong(), response.totalBytes)
            assertArrayEquals(content.copyOfRange(1_000, content.size), response.body.readBytes())
        }
        assertEquals("bytes=1000-", requests.single())
    }

    @Test
    fun `reports a server that ignores Range as a full answer`() {
        source.open("$base/no-range", 1_000).use { response ->
            assertEquals(200, response.code)
            assertEquals(0L, response.startByte)
        }
    }

    @Test
    fun `reports an HTTP error code`() {
        source.open("$base/missing", 0).use { assertEquals(404, it.code) }
    }

    @Test
    fun `gives up on a redirect loop`() {
        assertThrows(IOException::class.java) { source.open("$base/loop", 0) }
    }

    @Test
    fun `refuses plain http unless explicitly allowed`() {
        assertThrows(IOException::class.java) { HttpDownloadSource().open("$base/file", 0) }
    }

    @Test
    fun `the whole downloader resumes after the real connection is cut`() = runTest {
        dropFirstTransfer = true
        val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        val spec = ModelSpec("model.litertlm", "$base/start", content.size.toLong(), sha)
        val downloader = ModelDownloader(spec, folder.root, source, freeSpaceBytes = { Long.MAX_VALUE / 2 }, retryDelaysMillis = listOf(0L, 0L), spaceMarginBytes = 0)

        val progress = downloader.download().toList()

        assertEquals(DownloadProgress.Done, progress.last())
        assertArrayEquals(content, File(folder.root, "model.litertlm").readBytes())
        assertFalse(File(folder.root, "model.litertlm.part").exists())
        assertEquals("the second request must continue where the first stopped", 2, requests.size)
        assertEquals(null, requests[0])
        assertTrue(requests[1]!!.startsWith("bytes=") && requests[1] != "bytes=0-")
    }
}
