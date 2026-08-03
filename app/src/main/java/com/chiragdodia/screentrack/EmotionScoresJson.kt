package com.chiragdodia.screentrack

import org.json.JSONObject

/** Serialize the 28-emotion score map to/from a JSON string for Room storage. */
object EmotionScoresJson {

    fun encode(scores: EmotionScores): String {
        val o = JSONObject()
        scores.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun decode(json: String): EmotionScores {
        if (json.isBlank()) return emptyMap()
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { o.getDouble(it) }
    }
}
