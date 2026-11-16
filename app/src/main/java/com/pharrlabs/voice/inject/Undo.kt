package com.pharrlabs.voice.inject

/**
 * What a dictation did to a field: the text and selection before, the text after, and the exact piece that
 * was inserted (including any spaces the merger added around it).
 */
data class InsertRecord(
    val before: String,
    val selStart: Int,
    val selEnd: Int,
    val after: String,
    val piece: String,
)

/** Text and selection to put back when a dictation is undone. */
data class Reverted(val text: String, val selStart: Int, val selEnd: Int)

object Undo {
    fun record(existing: String, selStart: Int, selEnd: Int, insert: String): InsertRecord {
        val merged = TextMerger.merge(existing, selStart, selEnd, insert)
        val (from, to) = TextMerger.range(existing, selStart, selEnd)
        val pieceLength = merged.text.length - (existing.length - (to - from))
        return InsertRecord(existing, from, to, merged.text, merged.text.substring(from, from + pieceLength))
    }

    /**
     * Undoes [rec] against the field's [current] text. If nothing else changed, the original text and
     * selection come back; if the user typed elsewhere meanwhile, only the inserted piece is removed. Returns
     * null when the piece cannot be found exactly once, so undo never guesses and deletes the wrong text.
     */
    fun revert(rec: InsertRecord, current: String): Reverted? {
        if (current == rec.after) return Reverted(rec.before, rec.selStart, rec.selEnd)
        val idx = current.indexOf(rec.piece)
        if (idx < 0 || current.indexOf(rec.piece, idx + 1) >= 0) return null
        return Reverted(current.removeRange(idx, idx + rec.piece.length), idx, idx)
    }
}
