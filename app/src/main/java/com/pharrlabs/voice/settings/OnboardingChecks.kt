package com.pharrlabs.voice.settings

object OnboardingChecks {
    enum class Step { GrantMic, AllowRestrictedSettings, EnableService, DownloadModel, BatteryExemption }

    /**
     * Setup steps still outstanding, in the order the user should do them. "Allow restricted settings"
     * is only needed while the accessibility service is off: sideloaded apps cannot be enabled until
     * the user allows it from App info.
     */
    fun missing(micGranted: Boolean, serviceEnabled: Boolean, modelInstalled: Boolean, batteryExempt: Boolean): List<Step> =
        buildList {
            if (!micGranted) add(Step.GrantMic)
            if (!serviceEnabled) { add(Step.AllowRestrictedSettings); add(Step.EnableService) }
            if (!modelInstalled) add(Step.DownloadModel)
            if (!batteryExempt) add(Step.BatteryExemption)
        }
}
