package com.mordeniuss.animationtestassignment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacpp.avcodec
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var animationSurfaceView: AnimationSurfaceView
    private lateinit var renderBtn: Button
    private var recorder: FFmpegFrameRecorder? = null
    private val converter = AndroidFrameConverter()
    private var lastVideoPath: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        animationSurfaceView = findViewById(R.id.helloWorldSurfaceView)
        renderBtn = findViewById(R.id.btn)

        renderBtn.setOnClickListener {
            onRenderBtnClick()
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray(), 0);
        }
    }

    private fun onRenderBtnClick() {
        animationSurfaceView.init(::onBitmapReady, ::onFinish)
        renderBtn.isEnabled = false
        initRecorder(outputFile())
        recorder!!.start()
        animationSurfaceView.startAnimation()
    }

    private fun initRecorder(outputFile: String) {
        recorder = FFmpegFrameRecorder(outputFile, Config.VIDEO_RES_WIDTH, Config.VIDEO_RES_HEIGHT)
        recorder!!.videoCodec = avcodec.AV_CODEC_ID_MPEG4;
        recorder!!.format = "mp4"
        recorder!!.frameRate = Config.FPS
        recorder!!.videoBitrate = 1200
    }

    private suspend fun onBitmapReady(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val scaledBitmap =
                Bitmap.createScaledBitmap(bitmap, Config.VIDEO_RES_WIDTH, Config.VIDEO_RES_HEIGHT, true)
        val frame = converter.convert(scaledBitmap)
        recorder!!.record(frame)
    }

    private suspend fun onFinish() = withContext(Dispatchers.IO)  {
        recorder!!.stop()
        recorder!!.release()
        recorder = null
        withContext(Dispatchers.Main) { renderBtn.isEnabled = true }
        openVideo()
    }

    private fun outputFile(): String {
        val sdf = SimpleDateFormat("MM-dd-HH-mm-ss", Locale.getDefault())
        val dateString = sdf.format(Date())
        val path = "${ContextCompat.getExternalFilesDirs(this, null)[0]}/$dateString.mp4"
        lastVideoPath = path
        return path
    }

    private fun openVideo() {
        val uri = Uri.parse(lastVideoPath)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setDataAndType(uri, "video/mp4")
        startActivity(intent)
    }
}