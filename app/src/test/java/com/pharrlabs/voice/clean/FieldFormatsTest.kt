package com.pharrlabs.voice.clean

import com.pharrlabs.voice.field.FieldFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldFormatsTest {
    private fun email(s: String, d: Map<String, String> = emptyMap()) = FieldFormats.apply(s, FieldFormat.Email, d)
    private fun phone(s: String) = FieldFormats.apply(s, FieldFormat.Phone, emptyMap())
    private fun number(s: String) = FieldFormats.apply(s, FieldFormat.Number, emptyMap())

    // ---- email

    @Test fun spokenAddress() = assertEquals("sam@gmail.com", email("Sam at gmail dot com."))

    @Test fun alreadyAnAddress() = assertEquals("sam@gmail.com", email("Sam@gmail.com."))

    @Test fun halfSpoken() = assertEquals("sam@gmail.com", email("sam at gmail.com"))

    @Test fun commasFromTheTranscriptAreDropped() = assertEquals("sam@gmail.com", email("Sam, at gmail dot com."))

    @Test fun dotsInTheName() = assertEquals("alex.pharr@example.org", email("alex dot pharr at example dot org"))

    @Test fun underscoreDashAndPlus() =
        assertEquals("john-smith+news@outlook.com", email("john dash smith plus news at outlook dot com"))

    @Test fun underscore() = assertEquals("sam_lee@gmail.com", email("sam underscore lee at gmail dot com"))

    @Test fun spokenDigits() = assertEquals("sam123@yahoo.com", email("sam one two three at yahoo dot com"))

    @Test fun twoPartTld() = assertEquals("sam@example.co.uk", email("sam at example dot co dot uk"))

    @Test fun dictionaryLabelsApply() =
        assertEquals("alex@pharrlabs.com", email("Alex at far labs dot com", mapOf("far labs" to "Pharr Labs")))

    @Test fun noAtSignJustJoinsTheWords() = assertEquals("hello", email("Hello."))

    @Test fun anAtInsideTheNameIsNotTheSeparator() = assertEquals("pat@gmail.com", email("pat at gmail dot com"))

    // ---- phone

    @Test fun sevenDigits() = assertEquals("555-1234", phone("five five five one two three four."))

    @Test fun tenDigits() = assertEquals("555-123-4567", phone("five five five one two three four five six seven"))

    @Test fun alreadyDigits() = assertEquals("555-123-4567", phone("555-123-4567."))

    @Test fun plusPrefix() = assertEquals("+44", phone("plus four four").take(3))

    // ---- number

    @Test fun twoDigitNumber() = assertEquals("25", number("twenty five."))

    @Test fun smallNumberBecomesADigit() = assertEquals("5", number("five"))

    @Test fun decimal() = assertEquals("3.5", number("three point five"))

    @Test fun noThousandsSeparator() = assertEquals("2000", number("two thousand"))

    @Test fun negative() = assertEquals("-10", number("minus ten"))

    @Test fun currencyKeepsOnlyTheDigits() = assertEquals("25.00", number("\$25.00."))

    @Test fun hundreds() = assertEquals("150", number("one hundred fifty"))
}
