package com.pharrlabs.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun appIdIsPharrVoice() {
        assertEquals("com.pharrlabs.voice", BuildConfig.APPLICATION_ID)
    }
}
