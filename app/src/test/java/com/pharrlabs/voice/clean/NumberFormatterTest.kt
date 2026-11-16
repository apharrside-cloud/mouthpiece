package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatterTest {
    private fun f(s: String) = NumberFormatter.apply(s)

    // ---- plain numbers ----
    @Test fun smallNumbersStayWords() {
        assertEquals("I have two kids", f("I have two kids"))
        assertEquals("one of them", f("one of them"))
        assertEquals("nine", f("nine"))
    }

    @Test fun tenAndUpBecomeDigits() {
        assertEquals("10", f("ten"))
        assertEquals("I have 25 apples", f("I have twenty five apples"))
        assertEquals("I need 20 minutes", f("I need twenty minutes"))
        assertEquals("12", f("twelve"))
    }

    @Test fun hyphenatedNumbers() = assertEquals("it is 25", f("it is twenty-five"))

    @Test fun hundredsAndThousands() {
        assertEquals("105", f("one hundred and five"))
        assertEquals("123", f("one hundred twenty three"))
        assertEquals("300", f("three hundred"))
        assertEquals("1,200", f("one thousand two hundred"))
        assertEquals("2,000,000", f("two million"))
        assertEquals("200,000", f("two hundred thousand"))
    }

    @Test fun aHundredMeansOneHundred() {
        assertEquals("100 times", f("a hundred times"))
        assertEquals("1,000", f("a thousand"))
    }

    @Test fun adjacentNumbersAreNotMerged() = assertEquals("25 and 30 people", f("twenty five and thirty people"))

    @Test fun loneAndIsLeftAlone() = assertEquals("one and two", f("one and two"))

    @Test fun alreadyDigitsUntouched() = assertEquals("I have 25 apples", f("I have 25 apples"))

    @Test fun wordsContainingNumberWordsUntouched() = assertEquals("someone phoned anyone", f("someone phoned anyone"))

    // ---- years, ordinals, decimals ----
    @Test fun years() {
        assertEquals("2026", f("twenty twenty six"))
        assertEquals("1999", f("nineteen ninety nine"))
        assertEquals("2010", f("twenty ten"))
        assertEquals("2005", f("twenty oh five"))
        assertEquals("2026", f("two thousand twenty six"))
        assertEquals("2005", f("two thousand and five"))
    }

    @Test fun ordinalsTenAndUpOnly() {
        assertEquals("the 21st century", f("the twenty first century"))
        assertEquals("the 10th", f("the tenth"))
        assertEquals("100th", f("one hundredth"))
        assertEquals("the first one", f("the first one"))
        assertEquals("third place", f("third place"))
    }

    @Test fun secondIsNotTreatedAsAnOrdinalInCompounds() = assertEquals("a 30 second clip", f("a thirty second clip"))

    @Test fun decimals() {
        assertEquals("3.14", f("three point one four"))
        assertEquals("0.5", f("zero point five"))
        assertEquals("12.5", f("twelve point five"))
    }

    @Test fun pointWithoutANumberIsLeftAlone() = assertEquals("that is a point five people made", f("that is a point five people made"))

    // ---- money and percent ----
    @Test fun dollars() {
        assertEquals("$5", f("five dollars"))
        assertEquals("$1", f("one dollar"))
        assertEquals("I paid $40 for it", f("I paid forty dollars for it"))
        assertEquals("$100", f("a hundred dollars"))
        assertEquals("$10,000", f("ten thousand dollars"))
        assertEquals("$20", f("twenty bucks"))
    }

    @Test fun dollarsAndCents() {
        assertEquals("$25.50", f("twenty five dollars and fifty cents"))
        assertEquals("$3.05", f("three dollars and five cents"))
    }

    @Test fun digitDollars() {
        assertEquals("$25", f("25 dollars"))
        assertEquals("$1,000", f("1,000 dollars"))
        assertEquals("$25.50", f("25 dollars and 50 cents"))
    }

    @Test fun percent() {
        assertEquals("50%", f("fifty percent"))
        assertEquals("3%", f("three percent"))
        assertEquals("50%", f("50 percent"))
        assertEquals("12.5%", f("twelve point five percent"))
        assertEquals("100%", f("one hundred percent"))
        assertEquals("what percent", f("what percent"))
    }

    // ---- times ----
    @Test fun spokenTimes() {
        assertEquals("3:30 PM", f("three thirty pm"))
        assertEquals("meet at 3 PM", f("meet at three pm"))
        assertEquals("10:45 AM", f("ten forty five am"))
        assertEquals("9:05 AM", f("nine oh five a m"))
        assertEquals("12:15 PM", f("twelve fifteen p m"))
        assertEquals("at 3 o'clock", f("at three o'clock"))
    }

    @Test fun digitTimes() {
        assertEquals("3:30 PM", f("3:30 p.m."))
        assertEquals("5 PM", f("5 pm"))
        assertEquals("9:15 AM", f("9:15am"))
    }

    @Test fun periodAfterAbbreviationKeptOnlyWhenItEndsTheSentence() {
        assertEquals("at 3 PM. Then we go", f("at 3 p.m. Then we go"))
        assertEquals("at 3 PM and then", f("at 3 p.m. and then"))
        assertEquals("at 3 PM.", f("at 3 pm."))
    }

    @Test fun amInOrdinarySentenceIsNotATime() {
        assertEquals("I am here", f("I am here"))
        assertEquals("a mile", f("a mile"))
    }

    // ---- digit runs and phone numbers ----
    @Test fun sevenDigitPhone() = assertEquals("call 555-1234", f("call five five five one two three four"))

    @Test fun tenDigitPhone() = assertEquals("555-123-4567", f("five five five one two three four five six seven"))

    @Test fun elevenDigitPhone() = assertEquals("1-555-123-4567", f("one five five five one two three four five six seven"))

    @Test fun zipCode() = assertEquals("28173", f("two eight one seven three"))

    @Test fun ohCountsAsZeroInsideARun() = assertEquals("5035", f("five oh three five"))

    @Test fun shortRunsStayWords() = assertEquals("one two three", f("one two three"))

    // ---- dates ----
    @Test fun monthAndDay() {
        assertEquals("January 5th", f("january fifth"))
        assertEquals("March 21st", f("march twenty first"))
        assertEquals("June 2nd", f("june second"))
        assertEquals("December 31st", f("december thirty first"))
    }

    @Test fun monthDayYear() = assertEquals("January 5th, 2026", f("january fifth twenty twenty six"))

    @Test fun mayIsOnlyAMonthBeforeADay() {
        assertEquals("May 5th", f("may fifth"))
        assertEquals("you may go", f("you may go"))
    }

    // ---- safety ----
    @Test fun idempotent() {
        val once = f("I paid twenty five dollars at three thirty pm on january fifth twenty twenty six")
        assertEquals(once, f(once))
    }

    @Test fun plainTextUntouched() {
        val s = "Hello world, this is a normal sentence."
        assertEquals(s, f(s))
    }

    @Test fun emptyIsEmpty() = assertEquals("", f(""))

    @Test fun keepsPunctuationAndLineBreaks() = assertEquals("I have 20, and 30.\n\nNext", f("I have twenty, and thirty.\n\nNext"))
}
