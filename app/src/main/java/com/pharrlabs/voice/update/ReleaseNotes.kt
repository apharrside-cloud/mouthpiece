package com.pharrlabs.voice.update

data class ReleaseNote(val version: String, val changes: List<String>)

/** Bundled notes stay available offline. Add an entry when shipping a new feature release. */
object ReleaseNotes {
    const val LATEST_URL = "https://github.com/apharrside-cloud/mouthpiece/releases/latest"

    val releases = listOf(
        ReleaseNote("0.5.2", listOf(
            "What’s new now lists the 0.5.1 changes.",
        )),
        ReleaseNote("0.5.1", listOf(
            "Dictation no longer starts with the word “Message” in Telegram, or “Compose email” in Gmail. Placeholder text in a field is now treated as empty.",
        )),
        ReleaseNote("0.5.0", listOf(
            "A hamburger menu with page tiles and a highlight for the current page.",
            "Clear speech-model status, Download & use, and setup guidance when no model is ready.",
            "A compact Remove action for downloaded models.",
            "Side-by-side dictionary fields: When I say on the left and Write on the right.",
            "The download-update button beside Check for updates, with the result shown underneath.",
            "Optional update notifications and background checks approximately every 12 hours.",
            "This What’s new page with release highlights available offline.",
        )),
        ReleaseNote("0.4.10", listOf(
            "Report a problem or suggest a feature from Home, without an account.",
            "Update-check results appear near the check button, with confirmation messages.",
        )),
        ReleaseNote("0.4.9", listOf(
            "Five-line live captions follow the latest words and retain earlier text for scrolling.",
        )),
        ReleaseNote("0.4.8", listOf(
            "A bottom-left corner option for the floating bubble, with dragging and backup support.",
        )),
        ReleaseNote("0.4.7", listOf(
            "Bubble color choices, a Lips icon, and a first-launch welcome screen.",
        )),
    )
}
