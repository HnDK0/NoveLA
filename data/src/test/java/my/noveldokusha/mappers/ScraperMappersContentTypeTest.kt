package my.noveldokusha.mappers

import my.noveldokusha.scraper.domain.BookResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Propagation: BookResult.contentType (из Lua content_type) переносится в
 * BookMetadata при маппинге — это единственное звено, через которое метка
 * формата попадает в цепочку добавления книги в библиотеку.
 */
class ScraperMappersContentTypeTest {

    @Test
    fun `mapToBookMetadata propagates contentType`() {
        val result = BookResult(
            title = "Manga",
            url = "https://site/book/manga",
            contentType = "manga"
        )

        val metadata = result.mapToBookMetadata()

        assertEquals("manga", metadata.contentType)
    }

    @Test
    fun `mapToBookMetadata keeps empty contentType for sources without label`() {
        val result = BookResult(
            title = "Novel",
            url = "https://site/book/novel"
        )

        val metadata = result.mapToBookMetadata()

        assertEquals("", metadata.contentType)
    }
}