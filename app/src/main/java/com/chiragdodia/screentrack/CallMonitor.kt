package com.chiragdodia.screentrack

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager

/**
 * Detects active phone or VoIP/video calls so capture can auto-pause.
 * Phone calls via TelephonyManager call state; VoIP via AudioManager mode.
 */
class CallMonitor(
    private val context: Context,
    private val onCallActiveChanged: (Boolean) -> Unit
) {
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var listener: android.telephony.PhoneStateListener? = null
    private var lastActive = false

    private fun evaluate() {
        @Suppress("DEPRECATION")
        val callState = telephony.callState // CALL_STATE_IDLE / RINGING / OFFHOOK
        val mode = audio.mode // MODE_IN_CALL / MODE_IN_COMMUNICATION for VoIP
        val active = callState != TelephonyManager.CALL_STATE_IDLE ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION
        if (active != lastActive) {
            lastActive = active
            onCallActiveChanged(active)
        }
    }

    fun start() {
        @Suppress("DEPRECATION")
        listener = object : android.telephony.PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) { evaluate() }
        }
        @Suppress("DEPRECATION")
        telephony.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        evaluate() // initial state (also catches VoIP already in progress)
    }

    fun stop() {
        @Suppress("DEPRECATION")
        listener?.let { telephony.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE) }
        listener = null
    }
}
