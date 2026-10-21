package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexHeadwordsTest {
    @Test fun findsEarlyLateAndAbsentSpellings() {
        val json = """{"headwords":{"犬":["20170616125903.ogg"],"猫":["20170616125901.ogg"],"鳥":["20170616125904.ogg"]}}"""
        assertEquals("20170616125903.ogg", IndexHeadwords(json).recording("犬"))
        assertEquals("20170616125901.ogg", IndexHeadwords(json).recording("猫"))
        assertEquals("20170616125904.ogg", IndexHeadwords(json).recording("鳥"))
        assertNull(IndexHeadwords(json).recording("牛"))
    }

    @Test fun decodesEscapedSpellingsBeforeComparing() {
        val json = """{"headwords":{"\u732b":["20170616125901.ogg"],"a\"b":["20170616125902.ogg"]}}"""
        assertEquals("20170616125901.ogg", IndexHeadwords(json).recording("猫"))
        assertEquals("20170616125902.ogg", IndexHeadwords(json).recording("a\"b"))
        assertNull(IndexHeadwords(json).recording("\\u732b"))
    }

    @Test fun keepsOnlyTheFirstRecordingOfTheMatchedHeadword() {
        val json = """{"headwords":{"猫":["20170616125901.ogg","20170616125902.ogg"]}}"""
        assertEquals("20170616125901.ogg", IndexHeadwords(json).recording("猫"))
    }

    @Test fun emptyRecordingArrayIsAMiss() {
        val json = """{"headwords":{"猫":[]}}"""
        assertNull(IndexHeadwords(json).recording("猫"))
        IndexHeadwords(json).validate()
    }

    @Test fun lastDuplicateHeadwordKeyWins() {
        val json = """{"headwords":{"猫":["20170616125901.ogg"],"猫":["20170616125902.ogg"]}}"""
        assertEquals("20170616125902.ogg", IndexHeadwords(json).recording("猫"))
    }

    @Test fun emptyDuplicateDoesNotEraseAnEarlierMatch() {
        val json = """{"headwords":{"猫":["20170616125901.ogg"],"猫":[]}}"""
        assertEquals("20170616125901.ogg", IndexHeadwords(json).recording("猫"))
    }

    @Test fun laterDuplicateTopLevelSectionWins() {
        val json = """{"headwords":{"猫":["20170616125901.ogg"]},"headwords":{"犬":["20170616125902.ogg"]}}"""
        assertNull(IndexHeadwords(json).recording("猫"))
        assertEquals("20170616125902.ogg", IndexHeadwords(json).recording("犬"))
    }

    @Test fun skipsUnknownTopLevelSectionsWithTheExistingPermissiveness() {
        // Unknown primitives are skipped without being parsed as JSON values; that tolerance is
        // pre-existing behaviour and must not become stricter as a silent side effect.
        val json = """{"meta":{"version":2,"nested":[{"a":true},null,1e5]},"note":"text", """ +
            """"flag":tru,"zero":01,"dash":-,"headwords":{"猫":["20170616125901.ogg"]}}"""
        assertEquals("20170616125901.ogg", IndexHeadwords(json).recording("猫"))
        IndexHeadwords(json).validate()
    }

    @Test fun toleratesWhitespaceBetweenTokens() {
        val json = " {\n\t\"headwords\" : { \"猫\" : [ \"20170616125901.ogg\" ] } \n} "
        assertEquals("20170616125901.ogg", IndexHeadwords(json).recording("猫"))
    }

    @Test fun rejectsAnInvalidFilenameAnywhereInTheDocument() {
        val before = """{"headwords":{"犬":["2017.ogg"],"猫":["20170616125901.ogg"]}}"""
        val after = """{"headwords":{"猫":["20170616125901.ogg"],"犬":["20170616125901.mp3"]}}"""
        assertTrue(runCatching { IndexHeadwords(before).recording("猫") }.isFailure)
        assertTrue(runCatching { IndexHeadwords(after).recording("猫") }.isFailure)
    }

    @Test fun aMatchFollowedByLaterMalformedContentIsRejected() {
        val trailing = """{"headwords":{"猫":["20170616125901.ogg"],"犬":["20170616125902.ogg"]"""
        assertTrue(runCatching { IndexHeadwords(trailing).recording("猫") }.isFailure)

        val brokenValue = """{"headwords":{"猫":["20170616125901.ogg"],"犬":[20170616125902]}}"""
        assertTrue(runCatching { IndexHeadwords(brokenValue).recording("猫") }.isFailure)

        val truncated = """{"headwords":{"猫":["20170616125901.ogg"]"""
        assertTrue(runCatching { IndexHeadwords(truncated).recording("猫") }.isFailure)
    }

    @Test fun rejectsDocumentsWithoutHeadwordsOrWithTrailingBytes() {
        assertTrue(runCatching { IndexHeadwords("{}").validate() }.isFailure)
        assertTrue(runCatching { IndexHeadwords("""{"meta":{}}""").validate() }.isFailure)
        assertTrue(runCatching { IndexHeadwords("""{"headwords":{}} extra""").validate() }.isFailure)
    }

    @Test fun filenameValidatorAgreesWithThePreviousRegularExpression() {
        val reference = Regex("[0-9]{14}\\.ogg")
        val names = listOf(
            "20170616125901.ogg",
            "2017061612590.ogg",
            "201706161259011.ogg",
            "20170616125901.og",
            "20170616125901.oggg",
            "20170616125901.OGG",
            " 20170616125901.ogg",
            "20170616125901.ogg ",
            "2017061612590a.ogg",
            "２０１７０６１６１２５９０１.ogg",
            "\\u0032.ogg",
        )
        names.forEach { name ->
            val json = """{"headwords":{"x":["$name"]}}"""
            val accepted = runCatching { IndexHeadwords(json).validate() }.isSuccess
            assertEquals("validator disagreed for '$name'", reference.matches(name), accepted)
        }
    }
}
