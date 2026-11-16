package com.pharrlabs.voice.settings

import com.pharrlabs.voice.settings.OnboardingChecks.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingChecksTest {
    @Test fun allMissingInOrder() = assertEquals(
        listOf(Step.GrantMic, Step.AllowRestrictedSettings, Step.EnableService, Step.DownloadModel, Step.BatteryExemption),
        OnboardingChecks.missing(micGranted = false, serviceEnabled = false, modelInstalled = false, batteryExempt = false)
    )

    @Test fun nothingMissing() = assertTrue(
        OnboardingChecks.missing(micGranted = true, serviceEnabled = true, modelInstalled = true, batteryExempt = true).isEmpty()
    )

    @Test fun restrictedSettingsOnlyWhileServiceOff() = assertEquals(
        listOf(Step.DownloadModel),
        OnboardingChecks.missing(micGranted = true, serviceEnabled = true, modelInstalled = false, batteryExempt = true)
    )

    @Test fun onlyBatteryMissing() = assertEquals(
        listOf(Step.BatteryExemption),
        OnboardingChecks.missing(true, true, true, false)
    )
}
