package my.noveldokusha.coreui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookRatingParserTest {

    private fun assertScore(raw: String?, expectedValue: String, expectedScore: Float) {
        val parsed = parseBookRating(raw)
        assertEquals(BookRatingType.Score, parsed?.type)
        assertEquals(expectedValue, parsed?.value)
        assertEquals(expectedScore, parsed?.score ?: 0f, 0.001f)
    }

    private fun assertRank(raw: String?, expectedValue: String) {
        val parsed = parseBookRating(raw)
        assertEquals(BookRatingType.Rank, parsed?.type)
        assertEquals(expectedValue, parsed?.value)
        assertNull(parsed?.score)
    }

    private fun assertInvalid(raw: String?) {
        assertNull(parseBookRating(raw))
    }

    @Test
    fun scoreDotDecimal() = assertScore("4.3", "4.3", 4.3f)

    @Test
    fun scoreCommaDecimal() = assertScore("4,6", "4.6", 4.6f)

    @Test
    fun scoreTrailingZeroTrimmed() = assertScore("4.0", "4", 4.0f)

    @Test
    fun scoreWholeNumber() = assertScore("3", "3", 3.0f)

    @Test
    fun scoreScaleTen() = assertScore("9/10", "4.5", 4.5f)

    @Test
    fun scoreScaleTenWhole() = assertScore("6/10", "3", 3.0f)

    @Test
    fun scoreScaleFive() = assertScore("4.6/5", "4.6", 4.6f)

    @Test
    fun rankWord() = assertRank("Rank: 3", "3")

    @Test
    fun rankHash() = assertRank("Rank #12", "12")

    @Test
    fun rankHashBare() = assertRank("#12", "12")

    @Test
    fun scoreScaleTenFractional() = assertScore("8.7/10", "4.3", 4.35f)

    @Test
    fun bareNumberOverFiveInvalid() = assertInvalid("12")

    @Test
    fun sixInvalid() = assertInvalid("6")

    @Test
    fun ratingWordOverFiveInvalid() = assertInvalid("Rating: 9.5")

    @Test
    fun emptyStringInvalid() = assertInvalid("")

    @Test
    fun nonNumericInvalid() = assertInvalid("abc")

    @Test
    fun nullInvalid() = assertInvalid(null)

    @Test
    fun ratingWordValid() = assertScore("Rating: 4.3", "4.3", 4.3f)

    @Test
    fun scoreTenOfTen() = assertScore("10/10", "5", 5.0f)

    @Test
    fun scoreFiveOfFive() = assertScore("5/5", "5", 5.0f)

    @Test
    fun scoreZeroOfTen() = assertScore("0/10", "0", 0.0f)

    @Test
    fun scoreZeroDecimal() = assertScore("0.0", "0", 0.0f)

    @Test
    fun whitespaceInvalid() = assertInvalid("   ")

    @Test
    fun zeroScaleInvalid() = assertInvalid("0/0")

    @Test
    fun rankFractionalInvalid() = assertInvalid("Rank #12.5")

    @Test
    fun rankedWordIsScoreNotRank() = assertScore("Ranked 4.5", "4.5", 4.5f)

    @Test
    fun rankLowercase() = assertRank("rank 3", "3")
}
