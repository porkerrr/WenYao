package com.peterpork.liuyao.preview

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Decode the delivered, unedited recording; these are recording metrics, never render FPS. */
@RunWith(AndroidJUnit4::class)
class Round4VideoTest {
    @Test fun decodeEntireRecording() {
        val folder = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
        val file = File(folder, "round4-playback.mp4")
        assertTrue(file.isFile)
        val extractor = MediaExtractor()
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).single {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        var inputDone = false
        var outputDone = false
        var submitted = 0
        val timestamps = mutableListOf<Long>()
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + 180_000
        try {
            while (!outputDone) {
                assertTrue("Decoder timeout", SystemClock.elapsedRealtime() < deadline)
                if (!inputDone) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val size = extractor.readSampleData(checkNotNull(codec.getInputBuffer(input)), 0)
                        if (size < 0) {
                            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            submitted++
                            extractor.advance()
                        }
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 10_000)
                if (output >= 0) {
                    if (info.size > 0) {
                        assertNotNull(codec.getOutputBuffer(output))
                        timestamps.add(info.presentationTimeUs)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
        } finally { codec.stop(); codec.release(); extractor.release() }
        assertEquals(submitted, timestamps.size)
        assertTrue(timestamps.size > 100)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.path)
        val duration = checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
        val samples = JSONArray()
        try {
            for (ms in 0 until duration step 500) {
                val image = checkNotNull(retriever.getScaledFrameAtTime(ms * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST, 270, 600))
                val name = "round4_video_${ms.toString().padStart(5, '0')}.png"
                File(folder, name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                image.recycle()
                samples.put(name)
            }
        } finally { retriever.release() }
        val intervals = timestamps.zipWithNext { a, b -> b-a }
        assertTrue(intervals.all { it > 0 })
        File(folder, "round4-video.json").writeText(JSONObject()
            .put("mime", mime).put("width", format.getInteger(MediaFormat.KEY_WIDTH))
            .put("height", format.getInteger(MediaFormat.KEY_HEIGHT)).put("durationMs", duration)
            .put("decodedFrames", timestamps.size).put("submittedSamples", submitted)
            .put("recordingAverageFps", (timestamps.size-1)*1_000_000.0/(timestamps.last()-timestamps.first()))
            .put("recordingFrameIntervalsUs", JSONArray(intervals))
            .put("sourceAnimationFps", 60).put("renderPerformance", "not measured")
            .put("fullDecodePassed", true).put("sampleImages", samples).toString(2))
    }
}
