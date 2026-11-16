package com.pharrlabs.voice.bubble

/** The idle-bubble colors a user can pick from; the other states keep fixed colors so they stay recognisable. */
enum class BubbleTint(val label: String, val argb: Int) {
    Orange("Orange", 0xFFE8650D.toInt()),
    Gold("Gold", 0xFFA67C00.toInt()),
    Green("Green", 0xFF228B22.toInt()),
    Blue("Blue", 0xFF1E63D6.toInt()),
    Brown("Brown", 0xFF7B3F2A.toInt()),
    Graphite("Graphite", 0xFF37474F.toInt());

    companion object {
        fun parse(name: String?): BubbleTint = values().firstOrNull { it.name == name } ?: Orange
    }
}

/** Fill colors of the floating bubble per state: the chosen tint (brand orange by default) when idle, three clearly different colors for the rest. */
object BubbleColors {
    val IDLE = 0xFFE8650D.toInt()
    val LISTENING = 0xFF008391.toInt()
    val PROCESSING = 0xFF5E35B1.toInt()
    val ERROR = 0xFFC2185B.toInt()

    fun of(state: BubbleState, idle: Int = IDLE): Int = when (state) {
        BubbleState.Idle -> idle
        BubbleState.Listening -> LISTENING
        BubbleState.Processing -> PROCESSING
        is BubbleState.Error -> ERROR
    }
}
