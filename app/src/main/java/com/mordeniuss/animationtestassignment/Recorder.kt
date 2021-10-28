package com.mordeniuss.animationtestassignment

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.reflect.KSuspendFunction0

class Recorder(
    private val onFinish: KSuspendFunction0<Unit>,
    private val onFramesCollected: () -> Unit,
    private val scope: CoroutineScope
) {
    private val bufferInfo = MediaCodec.BufferInfo()
    private var videoTrackIndex = 0

    private val bitsPerPixel = 32
    private val TIMEOUT_USEC = 5000L
    private val bitRate =
        bitsPerPixel * Config.FPS * Config.VIDEO_RES_HEIGHT * Config.VIDEO_RES_WIDTH
    private val mimeType = "video/avc"

    private var expectedFrameIndex = 0
    private var readyBitmaps = mutableMapOf<Int, Bitmap>()
    private var isFramesAwait = false
    private var recordJob: Job? = null

    private lateinit var codec: MediaCodec
    private lateinit var format: MediaFormat
    private lateinit var muxer: MediaMuxer

    fun prepare(outputFile: String) {
        codec = MediaCodec.createEncoderByType(mimeType)
        format =
            MediaFormat.createVideoFormat(mimeType, Config.VIDEO_RES_WIDTH, Config.VIDEO_RES_HEIGHT)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, Config.FPS)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        muxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        codec.start()
    }

    fun record() {
        isFramesAwait = true
        expectedFrameIndex = 0
        recordJob = scope.launch {
            startRecordLoop()
        }
    }

    fun onBitmapReady(bitmap: Bitmap, frameIndex: Int) {
        Timber.d("$frameIndex bitmap ready")
        if (isFramesAwait) {
            readyBitmaps[frameIndex] = bitmap
        }

        if (frameIndex == Config.FRAMES_IN_ANIMATION) {
            isFramesAwait = false
            onFramesCollected()
        }
    }

    private suspend fun startRecordLoop() = withContext(Dispatchers.Default) {
        while (isActive) {
            val bitmap = readyBitmaps[expectedFrameIndex]
            bitmap?.let {
                Timber.d("process $expectedFrameIndex bitmap")
                handleBitmap(expectedFrameIndex, bitmap)
                readyBitmaps.remove(expectedFrameIndex)
                expectedFrameIndex++
            }
            if (expectedFrameIndex == Config.FRAMES_IN_ANIMATION)
                release()
        }
    }

    private suspend fun release() = withContext(Dispatchers.IO) {
        isFramesAwait = false
        codec.stop()
        codec.release()

        muxer.stop()
        muxer.release()
        readyBitmaps.clear()
        onFinish()
        recordJob!!.cancelAndJoin()
    }

    private fun handleBitmap(frameIndex: Int, bitmap: Bitmap) {
        val inputBufId = codec.dequeueInputBuffer(TIMEOUT_USEC)
        if (inputBufId >= 0) {
            val input = getNV21(bitmap)
            val byteBuffer = codec.getInputBuffer(inputBufId)!!
            byteBuffer.clear()
            byteBuffer.put(input)
            codec.queueInputBuffer(
                inputBufId,
                0,
                input.size,
                computePresentationTime(frameIndex),
                0
            )
        }

        val outputBufId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
        if (outputBufId >= 0) {
            val encodedData = codec.getOutputBuffer(outputBufId) ?: return
            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
            codec.releaseOutputBuffer(outputBufId, false)
        } else if (outputBufId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val newFormat: MediaFormat = codec.outputFormat
            videoTrackIndex = muxer.addTrack(newFormat)
            muxer.start()
        }
    }

    private fun computePresentationTime(frameIndex: Int): Long {
        return (frameIndex * 1000000 / Config.FPS).toLong()
    }

    private fun getNV21(bitmap: Bitmap): ByteArray {
        val scaled =
            Bitmap.createScaledBitmap(bitmap, Config.VIDEO_RES_WIDTH, Config.VIDEO_RES_HEIGHT, true)
        val size = Config.VIDEO_RES_WIDTH * Config.VIDEO_RES_HEIGHT
        val argb = IntArray(size)
        scaled.getPixels(
            argb,
            0,
            Config.VIDEO_RES_WIDTH,
            0,
            0,
            Config.VIDEO_RES_WIDTH,
            Config.VIDEO_RES_HEIGHT
        )
        scaled.recycle()
        val yuv = ByteArray(size * 3 / 2)
        encodeYUV420SP(yuv, argb)
        return yuv
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray) {
        val frameSize = Config.VIDEO_RES_WIDTH * Config.VIDEO_RES_HEIGHT
        var yIndex = 0
        var uvIndex = frameSize
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until Config.VIDEO_RES_HEIGHT) {
            for (i in 0 until Config.VIDEO_RES_WIDTH) {
                a = argb[index] and -0x1000000 shr 24
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                U = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128
                V = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128
                yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                }
                index++
            }
        }
    }

}