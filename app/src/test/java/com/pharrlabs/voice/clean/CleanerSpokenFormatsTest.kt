package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanerSpokenFormatsTest {
    private val brand = mapOf("far labs" to "Pharr Labs", "pharr labs" to "Pharr Labs")

    @Test fun numbersAreFormattedByDefault() =
        assertEquals("I paid $25 at 3:30 PM.", Cleaner.clean("I paid twenty five dollars at three thirty pm"))

    @Test fun numberFormattingCanBeTurnedOff() =
        assertEquals("I paid twenty five dollars.", Cleaner.clean("I paid twenty five dollars", formatNumbers = false))

    @Test fun literalStyleKeepsWordsAsSpoken() =
        assertEquals("twenty five dollars", Cleaner.clean("twenty five dollars", style = Style.Literal))

    @Test fun theReportedCase() {
        val raw = "we could host it on a subdomain of my far labs site something like voice dot far labs dot com"
        assertEquals(
            "We could host it on a subdomain of my Pharr Labs site something like voice.pharrlabs.com.",
            Cleaner.clean(raw, brand),
        )
    }

    @Test fun domainIsNotCapitalizedOrSplitByTheDictionary() =
        assertEquals("voice.pharrlabs.com", Cleaner.clean("voice dot far labs dot com", brand, singleLine = true))

    @Test fun domainKeepsItsLettersWhenTheDictionaryHasTheBareWord() =
        assertEquals("Check out voice.pharrlabs.com.", Cleaner.clean("check out voice dot pharrlabs dot com", mapOf("pharrlabs" to "Pharr Labs")))

    @Test fun domainsInsideNumbersAreNotMangled() =
        assertEquals("Open ten.example.com.", Cleaner.clean("open ten dot example dot com"))

    @Test fun proseUsesTheBrandSpelling() = assertEquals("I love Pharr Labs.", Cleaner.clean("I love far labs", brand))
}
