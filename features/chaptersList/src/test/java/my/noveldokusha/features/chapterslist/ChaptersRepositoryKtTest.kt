package my.noveldokusha.features.chapterslist

import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao.UrlSize
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersRepositoryKtTest {

    private fun pageRow(url: String, totalBytes: Long) =
        DownloadedPageChapter(url = url, pages = "[]", totalBytes = totalBytes, quality = "HIGH")

    // ─── buildDbInfo ─────────────────────────────────────────────────────────

    @Test
    fun `buildDbInfo merges sizes and page rows, page rows win`() {
        val sizes = listOf(
            UrlSize("a", 100),
            UrlSize("b", 200),
            UrlSize("c", 300),
        )
        val rows = listOf(pageRow("b", 999), pageRow("d", 42))

        val info = buildDbInfo(sizes, rows)

        assertEquals(setOf("b", "d"), info.downloadedUrls)
        assertEquals(100L, info.sizeByUrl["a"])
        assertEquals(999L, info.sizeByUrl["b"]) // page row overrides UrlSize
        assertEquals(300L, info.sizeByUrl["c"])
        assertEquals(42L, info.sizeByUrl["d"])
    }

    @Test
    fun `buildDbInfo with empty inputs returns empty info`() {
        val info = buildDbInfo(emptyList(), emptyList())
        assertTrue(info.downloadedUrls.isEmpty())
        assertTrue(info.sizeByUrl.isEmpty())
    }

    // ─── mergeDiskInfo ───────────────────────────────────────────────────────

    @Test
    fun `mergeDiskInfo disk sizes win and urls are union`() {
        val db = DownloadInfo(
            downloadedUrls = setOf("a", "b"),
            sizeByUrl = mapOf("a" to 10, "b" to 20),
        )
        val disk = DownloadInfo(
            downloadedUrls = setOf("b", "c"),
            sizeByUrl = mapOf("b" to 999, "c" to 30),
        )

        val merged = mergeDiskInfo(db, disk)

        assertEquals(setOf("a", "b", "c"), merged.downloadedUrls)
        assertEquals(10L, merged.sizeByUrl["a"])
        assertEquals(999L, merged.sizeByUrl["b"]) // disk wins
        assertEquals(30L, merged.sizeByUrl["c"])
    }

    // ─── formatBytes ─────────────────────────────────────────────────────────

    @Test
    fun `formatBytes formats bytes, kb and mb`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(1536)) // 1.5 KB округляется до целых
        assertEquals("2.0 MB", formatBytes(2 * 1024 * 1024))
        assertEquals("0 B", formatBytes(0))
    }
}