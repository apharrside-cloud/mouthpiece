package com.pharrlabs.voice.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationTest {
    private val update = UpdateInfo("0.5.1", "7", "")

    @Test fun newVersionCanNotify() = assertTrue(UpdateCheck.shouldNotify(update, "0.5.0", null, null))
    @Test fun alreadyNotifiedVersionDoesNotRepeat() = assertFalse(UpdateCheck.shouldNotify(update, "0.5.0", null, "0.5.1"))
    @Test fun skippedVersionDoesNotNotify() = assertFalse(UpdateCheck.shouldNotify(update, "0.5.0", "0.5.1", null))
    @Test fun installedVersionDoesNotNotify() = assertFalse(UpdateCheck.shouldNotify(update, "0.5.1", null, null))
    @Test fun olderVersionDoesNotNotify() = assertFalse(UpdateCheck.shouldNotify(update, "0.6.0", null, null))
    @Test fun laterReleaseCanNotifyAgain() = assertTrue(UpdateCheck.shouldNotify(update, "0.5.0", null, "0.4.10"))
}
