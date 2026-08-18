package my.noveldokusha.coreui.components

import my.noveldokusha.coreui.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentTypeBadgeTest {

    @Test
    fun manga() = assertEquals(R.drawable.ic_content_type_manga, "manga".toContentTypeBadgeIcon())

    @Test
    fun emptyStringIsNovel() = assertEquals(R.drawable.ic_content_type_novel, "".toContentTypeBadgeIcon())

    @Test
    fun nullIsNovel() = assertEquals(R.drawable.ic_content_type_novel, null.toContentTypeBadgeIcon())

    @Test
    fun novelIsNovel() = assertEquals(R.drawable.ic_content_type_novel, "novel".toContentTypeBadgeIcon())

    @Test
    fun unexpectedValueIsNovel() = assertEquals(R.drawable.ic_content_type_novel, "comic".toContentTypeBadgeIcon())
}
