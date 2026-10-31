package io.github.mookeview.octop

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class DownloadSinkTest {
    @Test fun preservesBinaryBytesAcrossChunks() {
        val expected = ByteArray(DownloadSink.CHUNK_BYTES * 3 + 17) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()
        val sink = DownloadSink(output, expected.size.toLong())
        expected.asList().chunked(DownloadSink.CHUNK_BYTES).forEach {
            sink.write(it.toByteArray())
        }
        sink.finish()
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test fun supportsAnEmptyFile() {
        val output = ByteArrayOutputStream()
        DownloadSink(output, 0).finish()
        assertEquals(0, output.size())
    }

    @Test fun rejectsTruncatedOrExtraContentBeforeReportingSuccess() {
        val sink = DownloadSink(ByteArrayOutputStream(), 3)
        sink.write(byteArrayOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) { sink.finish() }
        assertThrows(IllegalArgumentException::class.java) { sink.write(byteArrayOf(3, 4)) }
    }

    @Test fun boundsTheSizeOfEachMessage() {
        val sink = DownloadSink(ByteArrayOutputStream(), Long.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) {
            sink.write(ByteArray(DownloadSink.CHUNK_BYTES + 1))
        }
    }

    @Test fun preservesChineseAndSpacesWithoutAllowingAPath() {
        assertEquals("中文 附件测试.txt", DownloadSink.filename("中文 附件测试.txt"))
        assertEquals("report.pdf", DownloadSink.filename("../../report.pdf"))
        assertEquals("report.pdf", DownloadSink.filename("C:\\private\\report.pdf"))
        assertEquals("badname.txt", DownloadSink.filename("bad\nname?.txt"))
        assertEquals("download", DownloadSink.filename(" .. "))
    }
}
