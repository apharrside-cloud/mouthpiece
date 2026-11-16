package com.pharrlabs.voice.inject

object PieceEdit {
    /**
     * Swaps [piece] (what a dictation inserted) for [replacement] inside the field's [current] text, with the
     * cursor after the replacement. Null when the piece is not found exactly once, so it never edits the wrong text.
     */
    fun replace(current: String, piece: String, replacement: String): Reverted? {
        if (piece.isEmpty()) return null
        val idx = current.indexOf(piece)
        if (idx < 0 || current.indexOf(piece, idx + 1) >= 0) return null
        val text = current.replaceRange(idx, idx + piece.length, replacement)
        val cursor = idx + replacement.length
        return Reverted(text, cursor, cursor)
    }
}
