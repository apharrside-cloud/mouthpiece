package com.pharrlabs.voice.bubble

/** Fill colors of the floating bubble per state: brand orange when idle, and three clearly different colors for the rest. */
object BubbleColors {
    val IDLE = 0xFFE8650D.toInt()
    val LISTENING = 0xFF008391.toInt()
    val PROCESSING = 0xFF5E35B1.toInt()
    val ERROR = 0xFFC2185B.toInt()

    fun of(state: BubbleState): Int = when (state) {
        BubbleState.Idle -> IDLE
        BubbleState.Listening -> LISTENING
        BubbleState.Processing -> PROCESSING
        is BubbleState.Error -> ERROR
    }
}
