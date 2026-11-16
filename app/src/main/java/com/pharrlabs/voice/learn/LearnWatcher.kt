package com.pharrlabs.voice.learn

/**
 * Watches the field for a short window after a dictation is typed and reports the first edit that looks like a
 * vocabulary fix. Android-free so the timing and de-duplication rules are unit-tested.
 */
class LearnWatcher(
    private val windowMs: Long = 60_000,
    private val isKnown: (Correction) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var baseline: String? = null
    private var inserted: String = ""
    private var deadline = 0L
    private val offered = mutableSetOf<Correction>()

    fun start(baseline: String, inserted: String) {
        this.baseline = baseline
        this.inserted = inserted
        deadline = clock() + windowMs
        offered.clear()
    }

    fun stop() { baseline = null }

    /** Call with the field's full text after the user edits it. Returns a correction to offer, or null. */
    fun onTextChanged(current: String): Correction? {
        val base = baseline ?: return null
        if (clock() > deadline) { baseline = null; return null }
        val c = CorrectionDiff.find(base, current, inserted) ?: return null
        if (isKnown(c) || !offered.add(c)) return null
        return c
    }
}
