package com.pharrlabs.voice.asr

/** Keep a usable model selected when preferences are restored or a model file disappears. */
object ModelSelection {
    fun resolve(selected: String, installed: Collection<String>): String? =
        selected.takeIf { it in installed } ?: installed.firstOrNull()
}
