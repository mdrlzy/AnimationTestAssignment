package com.mordeniuss.animationtestassignment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var animationSurfaceView: AnimationSurfaceView
    private lateinit var renderBtn: Button
    private var lastVideoPath: String? = null
    private val recorder = Recorder()


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
        animationSurfaceView.init(recorder::onBitmapReady, ::onFinish)
        renderBtn.isEnabled = false
        recorder.prepare(outputFile())
        animationSurfaceView.startAnimation()
    }


    private suspend fun onFinish() = withContext(Dispatchers.IO)  {
        recorder.release()
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