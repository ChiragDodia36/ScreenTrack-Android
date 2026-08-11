package com.chiragdodia.screentrack

import android.content.Context
import java.io.File

/**
 * Copies a bundled model asset into cacheDir once and returns its path, so
 * ONNX Runtime can memory-map it (native memory) instead of reading the whole
 * file onto the ~192 MB Java heap — which OOMs for the 125 MB emotion model.
 * The copy streams in small chunks, so it never holds the model in the heap.
 */
internal object ModelAssets {
    fun path(context: Context, assetName: String): String {
        val out = File(context.applicationContext.cacheDir, assetName)
        if (!out.exists() || out.length() == 0L) {
            context.applicationContext.assets.open(assetName).use { input ->
                out.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
            }
        }
        return out.absolutePath
    }
}
