package mx.dev1.naturequest.data.model

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.domain.model.DownloadFailure
import mx.dev1.naturequest.domain.model.DownloadFailureReason
import mx.dev1.naturequest.domain.model.DownloadProgress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.random.Random

class ModelDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val content = Random(42).nextBytes(3 * 1024 * 1024 + 123) // 3 MB and a bit
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val spec = ModelSpec("model.litertlm", "https://example.test/model", content.size.toLong(), sha)

    private fun downloader(source: DownloadSource, spec: ModelSpec = this.spec, freeSpace: Long = Long.MAX_VALUE / 2) =
        ModelDownloader(spec, folder.root, source, freeSpaceBytes = { freeSpace }, retryDelaysMillis = listOf(0L, 0L, 0L), spaceMarginBytes = 0L)

    private val target get() = File(folder.root, "model.litertlm")
    private val part get() = File(folder.root, "model.litertlm.part")

    @Test
    fun `downloads, verifies and moves the file into place`() = runTest {
        val source = FakeSource(content)

        val progress = downloader(source).download().toList()

        assertArrayEquals(content, target.readBytes())
        assertFalse("the .part file must be gone", part.exists())
        assertEquals(DownloadProgress.Done, progress.last())
        assertTrue(progress.any { it is DownloadProgress.Downloading })
        assertTrue(progress.any { it is DownloadProgress.Verifying })
        assertEquals(listOf(0L), source.starts)
    }

    @Test
    fun `progress only moves forward and ends at the full size`() = runTest {
        val downloading = downloader(FakeSource(content)).download().toList().filterIsInstance<DownloadProgress.Downloading>()

        assertEquals(downloading.map { it.downloaded }.sorted(), downloading.map { it.downloaded })
        assertEquals(content.size.toLong(), downloading.last().downloaded)
        assertTrue(downloading.all { it.total == content.size.toLong() })
    }

    @Test
    fun `continues an interrupted download from where it stopped`() = runTest {
        part.writeBytes(content.copyOfRange(0, 1_000_000))
        val source = FakeSource(content)

        downloader(source).download().toList()

        assertEquals(listOf(1_000_000L), source.starts)
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun `starts again from zero when the server ignores the Range header`() = runTest {
        part.writeBytes(ByteArray(500_000) { 9 }) // stale bytes that must not survive
        val source = FakeSource(content, supportsRange = false)

        downloader(source).download().toList()

        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun `retries and resumes after the connection drops`() = runTest {
        val source = FakeSource(content, dropAfter = mutableListOf(700_000, 900_000))

        downloader(source).download().toList()

        assertEquals(3, source.starts.size)
        assertEquals(0L, source.starts[0])
        assertTrue("each retry resumes further along", source.starts[1] > 0 && source.starts[2] > source.starts[1])
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun `gives up with a network failure but keeps what was downloaded`() = runTest {
        val source = FakeSource(content, dropAfter = MutableList(10) { 400_000 })

        val error = runCatching { downloader(source).download().toList() }.exceptionOrNull()

        assertTrue("got $error", error is DownloadFailure && error.reason == DownloadFailureReason.NETWORK)
        assertTrue("the partial file is kept so the download can continue later", part.length() > 0)
        assertFalse(target.exists())
    }

    @Test
    fun `a failure to connect at all is a network failure`() = runTest {
        val source = DownloadSource { _, _ -> throw IOException("no route to host") }

        val error = runCatching { downloader(source).download().toList() }.exceptionOrNull()

        assertTrue(error is DownloadFailure && error.reason == DownloadFailureReason.NETWORK)
    }

    @Test
    fun `a corrupt file is deleted and reported`() = runTest {
        val corrupted = content.copyOf().also { it[100] = (it[100] + 1).toByte() }

        val error = runCatching { downloader(FakeSource(corrupted)).download().toList() }.exceptionOrNull()

        assertTrue("got $error", error is DownloadFailure && error.reason == DownloadFailureReason.CORRUPT)
        assertFalse("a corrupt file must never be left as the model", target.exists())
        assertFalse("and must not be resumed from", part.exists())
    }

    @Test
    fun `a leftover complete but corrupt part file is rejected too`() = runTest {
        part.writeBytes(ByteArray(content.size) { 1 })

        val error = runCatching { downloader(FakeSource(content)).download().toList() }.exceptionOrNull()

        assertTrue(error is DownloadFailure && error.reason == DownloadFailureReason.CORRUPT)
        assertFalse(target.exists())
    }

    @Test
    fun `an already complete part file is only verified, not downloaded again`() = runTest {
        part.writeBytes(content)
        val source = FakeSource(content)

        val progress = downloader(source).download().toList()

        assertTrue("nothing should be requested", source.starts.isEmpty())
        assertEquals(DownloadProgress.Done, progress.last())
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun `refuses to start without enough free space`() = runTest {
        val error = runCatching {
            downloader(FakeSource(content), freeSpace = 1_000L).download().toList()
        }.exceptionOrNull()

        assertTrue(error is DownloadFailure && error.reason == DownloadFailureReason.NOT_ENOUGH_SPACE)
    }

    @Test
    fun `a server error is reported as a server failure`() = runTest {
        val notFound = DownloadSource { _, _ -> DownloadResponse(404, 0, -1, ByteArrayInputStream(ByteArray(0))) }

        val error = runCatching { downloader(notFound).download().toList() }.exceptionOrNull()

        assertTrue(error is DownloadFailure && error.reason == DownloadFailureReason.SERVER)
    }

    @Test
    fun `a file of the wrong size on the server is refused`() = runTest {
        val source = FakeSource(content, reportedTotal = content.size + 10L)

        val error = runCatching { downloader(source).download().toList() }.exceptionOrNull()

        assertTrue(error is DownloadFailure && error.reason == DownloadFailureReason.SERVER)
        assertFalse(target.exists())
    }

    @Test
    fun `cancelling keeps the partial file and never produces the final file`() = runTest {
        val progress = downloader(FakeSource(content)).download().take(2).toList()

        assertEquals(2, progress.size)
        assertFalse(target.exists())
        assertTrue(part.exists())
    }

    @Test
    fun `the shipped spec is pinned and well formed`() {
        val spec = ModelSpec.GEMMA_4_E2B
        assertEquals(64, spec.sha256.length)
        assertTrue(spec.sha256.all { it in "0123456789abcdef" })
        assertTrue("must be https", spec.url.startsWith("https://"))
        assertTrue("must pin a 40 character revision, not a branch", Regex("/resolve/[0-9a-f]{40}/").containsMatchIn(spec.url))
        assertEquals(ModelStore.MODEL_FILE_NAME, spec.fileName)
        assertTrue(spec.sizeBytes > 1_000_000_000L)
    }

    /** Serves [content]; can ignore Range, drop the connection part-way, or lie about the size. */
    private class FakeSource(
        private val content: ByteArray,
        private val supportsRange: Boolean = true,
        private val dropAfter: MutableList<Int> = mutableListOf(),
        private val reportedTotal: Long = content.size.toLong(),
    ) : DownloadSource {
        val starts = mutableListOf<Long>()

        override fun open(url: String, startByte: Long): DownloadResponse {
            starts += startByte
            val from = if (supportsRange) startByte.toInt() else 0
            val code = if (supportsRange && startByte > 0) 206 else 200
            val limit = dropAfter.removeFirstOrNull()
            val body: InputStream = if (limit == null) {
                ByteArrayInputStream(content, from, content.size - from)
            } else {
                DroppingStream(ByteArrayInputStream(content, from, content.size - from), limit)
            }
            return DownloadResponse(code, from.toLong(), reportedTotal, body)
        }
    }

    /** Delivers [limit] bytes, then fails like a dropped connection. */
    private class DroppingStream(private val inner: InputStream, private var limit: Int) : InputStream() {
        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (limit <= 0) throw IOException("connection reset")
            val read = inner.read(b, off, minOf(len, limit))
            if (read > 0) limit -= read
            return read
        }
    }
}
