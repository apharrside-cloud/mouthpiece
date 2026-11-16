package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenWebTest {
    private val brand = mapOf("far labs" to "Pharr Labs", "pharr labs" to "Pharr Labs")
    private fun f(s: String, d: Map<String, String> = emptyMap()) = SpokenWeb.apply(s, d)

    @Test fun spokenDomain() = assertEquals("voice.pharrlabs.com", f("voice dot pharrlabs dot com"))

    @Test fun sentenceAroundIt() = assertEquals("go to voice.pharrlabs.com now", f("go to voice dot pharrlabs dot com now"))

    @Test fun multiWordLabelIsJoinedAndMappedThroughTheDictionary() =
        assertEquals("go to voice.pharrlabs.com", f("go to voice dot far labs dot com", brand))

    @Test fun twoWordFirstLabelUsesTheDictionary() = assertEquals("visit pharrlabs.com", f("visit far labs dot com", brand))

    @Test fun twoWordFirstLabelWithoutDictionaryTakesOnlyTheLastWord() = assertEquals("visit example.com", f("visit example dot com"))

    @Test fun pathAfterSlash() = assertEquals("see example.com/download please", f("see example dot com slash download please"))

    @Test fun otherEndings() {
        assertEquals("example.org", f("example dot org"))
        assertEquals("blog.site.io", f("blog dot site dot io"))
    }

    @Test fun uppercaseSpokenIsLowercased() = assertEquals("voice.pharrlabs.com", f("Voice Dot PharrLabs Dot Com"))

    @Test fun literalDomainFromWhisperIsFixed() = assertEquals("voice.pharrlabs.com", f("Voice.FarLabs.com", mapOf("far labs" to "Pharr Labs")))

    @Test fun literalDomainIsLowercased() = assertEquals("visit example.com", f("visit Example.COM"))

    @Test fun dictionaryMapsEveryLabel() = assertEquals("blog.acme.com", f("blog.ACMEcorp.com", mapOf("acmecorp" to "Acme")))

    @Test fun trailingPeriodIsKept() = assertEquals("Visit voice.pharrlabs.com.", f("Visit voice dot pharrlabs dot com."))

    @Test fun theDotComBubbleIsNotADomain() = assertEquals("the dot com bubble", f("the dot com bubble"))

    @Test fun aDotAloneIsLeftAlone() = assertEquals("put a dot on the page", f("put a dot on the page"))

    @Test fun abbreviationsAreNotDomains() {
        assertEquals("at 3 p.m. today", f("at 3 p.m. today"))
        assertEquals("the U.S. economy", f("the U.S. economy"))
    }

    @Test fun plainTextUntouched() = assertEquals("Hello there, how are you?", f("Hello there, how are you?"))

    @Test fun extractAndRestoreProtectDomainsFromLaterSteps() {
        val (masked, urls) = SpokenWeb.extract("open voice dot pharrlabs dot com today", emptyMap())
        assertEquals(listOf("voice.pharrlabs.com"), urls)
        assertEquals(false, masked.contains("pharrlabs"))
        assertEquals("open voice.pharrlabs.com today", SpokenWeb.restore(masked, urls))
    }

    @Test fun twoDomainsInOneDictation() = assertEquals("acme.com and beta.org", f("acme dot com and beta dot org"))
}
