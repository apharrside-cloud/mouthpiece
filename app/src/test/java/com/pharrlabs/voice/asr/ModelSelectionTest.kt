package com.pharrlabs.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSelectionTest {
    @Test fun keepsSelectedDownloadedModel() =
        assertEquals("small", ModelSelection.resolve("small", listOf("tiny", "small")))

    @Test fun usesDownloadedModelWhenSelectedOneIsMissing() =
        assertEquals("tiny", ModelSelection.resolve("small", listOf("tiny")))

    @Test fun restoredUnknownSelectionUsesAnInstalledModel() =
        assertEquals("base", ModelSelection.resolve("obsolete", listOf("base", "small")))

    @Test fun noDownloadsRequiresSetup() =
        assertNull(ModelSelection.resolve("small", emptyList()))
}
