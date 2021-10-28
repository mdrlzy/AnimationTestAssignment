package com.mordeniuss.animationtestassignment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.AttributeSet
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
class AnimationSurfaceView : SurfaceView, SurfaceHolder.Callback {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val tickTime = ((1f/Config.FPS) * 1000000000).toLong()
    private var startAnimationTickTime = -1L
    private var delayTime = -1L

    private val mutex = Mutex()
    private lateinit var scope: CoroutineScope
    private var animationJob: Job? = null

    private lateinit var onBitmapReadyListener: (Bitmap, Int) -> Unit

    private var localFrameIndex = 0
    private var recordFrameIndex = 0
    private var isRecording = false

    private var textY = 0f
    private var textX = 0f

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startAnimation()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        textY = height / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runBlocking {
            animationJob?.cancelAndJoin()
        }
    }


    fun init(onBitmapReady: (Bitmap, Int) -> Unit, scope: CoroutineScope) {
        onBitmapReadyListener = onBitmapReady
        this.scope = scope
    }

    private fun startAnimation() {
        animationJob = scope.launch(Dispatchers.Default) {
            runAnimationLoop()
        }
    }

    fun record() {
        recordFrameIndex = 0
        isRecording = true
    }

    fun stopRecord() {
        isRecording = false
    }

    private suspend fun runAnimationLoop() = withContext(Dispatchers.Default) {
        while (isActive) {
            startAnimationTickTime = System.nanoTime()
            if (localFrameIndex > Config.FRAMES_IN_CYCLE)
                localFrameIndex = 0

            calculate()
            draw()

            if (isRecording) {
                takeFrame(recordFrameIndex)
                recordFrameIndex++
                mutex.withLock {}
            }
            localFrameIndex++

            delayTime = tickTime - (System.nanoTime() - startAnimationTickTime)
            delay(Duration.Companion.nanoseconds(delayTime))
        }
    }

    private fun calculate() {
        val widthPercent = localFrameIndex.toFloat() / Config.FRAMES_IN_CYCLE
        textX = widthPercent * width
    }

    private fun draw() {
        val textPaint = TextPaint()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = 20f * resources.displayMetrics.scaledDensity
        val canvas: Canvas? = holder.lockCanvas()
        canvas?.drawColor(Color.GRAY)
        canvas?.drawText("Hello World", textX, textY, textPaint)
        canvas?.let { holder.unlockCanvasAndPost(canvas) }
    }

    private suspend fun takeFrame(frameIndex: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mutex.lock()
        PixelCopy.request(this, bitmap, { onBitmapReady(bitmap, frameIndex) }, Handler(Looper.getMainLooper()))
    }

    private fun onBitmapReady(bitmap: Bitmap, frameIndex: Int) {
        scope.launch {
            mutex.unlock()
            onBitmapReadyListener(bitmap, frameIndex)
        }
    }


}