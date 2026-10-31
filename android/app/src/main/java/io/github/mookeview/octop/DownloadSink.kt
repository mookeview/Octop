package io.github.mookeview.octop

import java.io.OutputStream

/** Acknowledged chunks keep only one small buffer in flight, even for large downloads. */
class DownloadSink(private val output: OutputStream, private val expectedSize: Long) {
    private var written = 0L

    init {
        require(expectedSize >= 0)
    }

    fun write(bytes: ByteArray) {
        require(bytes.size <= CHUNK_BYTES && bytes.size.toLong() <= expectedSize - written)
        output.write(bytes)
        written += bytes.size
    }

    fun finish() {
        require(written == expectedSize)
        output.close()
    }

    fun close() = output.close()

    companion object {
        const val CHUNK_BYTES = 64 * 1024

        fun filename(value: String): String = value
            .substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 32 && it !in ":*?\"<>|" && it.code != 127 }
            .trim().trim('.').take(240).ifBlank { "download" }
    }
}
