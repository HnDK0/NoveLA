package my.noveldokusha.features.chapterslist

import my.noveldokusha.feature.local_database.DAOs.BookTitleTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectBookInfoRowTest {

    private fun row(sourceLang: String, title: String = "t", desc: String = "d") =
        BookTitleTranslation(
            bookUrl = "url",
            sourceLang = sourceLang,
            titleTranslation = title,
            descriptionTranslation = desc
        )

    @Test
    fun `empty rows returns null`() {
        assertNull(selectBookInfoRow(emptyList(), "en"))
    }

    @Test
    fun `exact source row present returns that row`() {
        val exact = row("en", title = "English title")
        val other = row("ja", title = "Japanese title")
        val result = selectBookInfoRow(listOf(exact, other), "en")

        assertEquals(exact, result)
    }

    @Test
    fun `only different-source non-empty row returns null`() {
        val other = row("ja", title = "Japanese title")
        val result = selectBookInfoRow(listOf(other), "en")

        assertNull(result)
    }

    @Test
    fun `exact source row with blank translations still selected`() {
        val exact = row("en", title = "", desc = "")
        val other = row("ja", title = "filled")
        val result = selectBookInfoRow(listOf(exact, other), "en")

        assertEquals(exact, result)
    }
}
