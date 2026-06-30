package com.chiragdodia.screentrack

data class PauseState(
    val screenOff: Boolean = false,
    val onCall: Boolean = false,
    val manual: Boolean = false
) {
    val isPaused: Boolean get() = screenOff || onCall || manual
}
