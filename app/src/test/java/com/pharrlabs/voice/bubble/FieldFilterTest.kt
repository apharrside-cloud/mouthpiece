package com.pharrlabs.voice.bubble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldFilterTest {
    private val own = "com.pharrlabs.voice"

    @Test fun editableNormalFieldShows() =
        assertTrue(FieldFilter.shouldShow(true, false, "com.google.android.apps.messaging", own))

    @Test fun passwordFieldHidden() =
        assertFalse(FieldFilter.shouldShow(true, true, "com.android.chrome", own))

    @Test fun nonEditableHidden() =
        assertFalse(FieldFilter.shouldShow(false, false, "com.android.chrome", own))

    @Test fun ownPackageHidden() =
        assertFalse(FieldFilter.shouldShow(true, false, own, own))

    @Test fun ownTryItFieldShows() =
        assertTrue(FieldFilter.shouldShow(true, false, own, own, FieldFilter.TRY_HINT))

    @Test fun otherOwnFieldsStillHidden() {
        assertFalse(FieldFilter.shouldShow(true, false, own, own, "Add a word"))
        assertFalse(FieldFilter.shouldShow(true, false, own, own, null))
    }

    @Test fun tryHintInAnotherAppDoesNotMatter() =
        assertTrue(FieldFilter.shouldShow(true, false, "com.android.chrome", own, FieldFilter.TRY_HINT))

    @Test fun tryFieldPasswordStillHidden() =
        assertFalse(FieldFilter.shouldShow(true, true, own, own, FieldFilter.TRY_HINT))
}
