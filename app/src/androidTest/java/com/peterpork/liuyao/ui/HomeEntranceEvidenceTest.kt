package com.peterpork.liuyao.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional extraction of a real adb screenrecord; never synthesizes UI or motion frames. */
class HomeEntranceEvidenceTest {
    @Test fun extractRecordedFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(isMotionTestPackage(context.packageName))
        val name = InstrumentationRegistry.getArguments().getString("motionVideo")
        assumeTrue("Supply a recording name in the isolated app's files directory", name != null)
        require(name!!.matches(Regex("[a-z0-9-]+")))
        val directory = File(context.filesDir, "motion-round1")
        val video = File(directory, "$name.mp4")
        check(video.isFile) { "Missing recording: ${video.absolutePath}" }
        val frames = File(directory, name).apply { mkdirs() }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            // Optional bounded sampling for long recordings; defaults preserve round 1/2 extraction.
            val args = InstrumentationRegistry.getArguments()
            val step = args.getString("motionStepMillis")?.toLong() ?: 100L
            val start = args.getString("motionStartMillis")?.toLong() ?: 0L
            val end = args.getString("motionEndMillis")?.toLong()?.coerceAtMost(duration) ?: duration
            require(step in 16L..1000L && start >= 0 && end > start)
            for (millis in start until end step step) {
                val bitmap = retriever.getFrameAtTime(millis * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                File(frames, "%05d.png".format(millis)).outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        } finally { retriever.release() }
    }
}
