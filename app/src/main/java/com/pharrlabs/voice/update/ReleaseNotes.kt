package com.pharrlabs.voice.update

data class ReleaseNote(val version: String, val changes: List<String>)

/** Bundled notes stay available offline. Add an entry when shipping a new feature release. */
object ReleaseNotes {
    const val LATEST_URL = "https://github.com/apharrside-cloud/mouthpiece/releases/latest"

    val releases = listOf(
        ReleaseNote("0.6.0", listOf(
            "New recommended speech model: Parakeet 0.6B. More accurate than Balanced at a similar speed, with punctuation built in. It is now the default for new installs.",
            "Fixed Medium and Large turbo producing garbled text. Both are now accurate, but they are slow: expect several times longer than your speech.",
            "The model list now runs from fastest to most accurate. The Tiny model is retired (Base is the new fastest) and its download is removed automatically.",
            "A dictation already being transcribed now keeps going if the screen turns off, and is copied to the clipboard if the text field is gone.",
        )),
        ReleaseNote("0.5.4", listOf(
            "A cleaner History page with date groups, compact Copy actions, and full text and timing details on tap.",
            "History now keeps the last 30 days, with automatic cleanup of older entries.",
            "New metrics for words, dictation count, average length, recording time, average recording duration and words per minute.",
            "Home shows each speech model’s average processing time and sample count from the last 30 days.",
            "A dot on the What’s new menu tile highlights an available app update.",
        )),
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
