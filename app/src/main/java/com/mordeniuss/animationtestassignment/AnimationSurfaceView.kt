package com.mordeniuss.animationtestassignment

import android.content.Context
import android.graphics.Bitmap
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
import kotlin.reflect.KSuspendFunction0
import kotlin.reflect.KSuspendFunction1

class AnimationSurfaceView : SurfaceView, SurfaceHolder.Callback {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )


    private val mutex = Mutex()
    private var scope = CoroutineScope(Dispatchers.Default)
    private var animationJob: Job? = null
    private var renderJob: Job? = null

    private var onBitmapReadyListener: KSuspendFunction1<Bitmap, Unit>? = null
    private var onFinishListener: KSuspendFunction0<Unit>? = null

    private var textY = 0f
    private var textX = 0f

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        textY = height / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runBlocking {
            animationJob?.cancelAndJoin()
            renderJob?.cancelAndJoin()
        }
    }


    fun init(onBitmapReady: KSuspendFunction1<Bitmap, Unit>, onFinish: KSuspendFunction0<Unit>) {
        onBitmapReadyListener = onBitmapReady
        onFinishListener = onFinish
    }

    fun startAnimation() {
        animationJob = scope.launch {
            resetAnimationProperties()
            runAnimationLoop()
            mutex.withLock {}
            onFinishListener?.let { it() }
        }
    }

    private suspend fun runAnimationLoop() {
        for (i in 0..Config.FRAMES_IN_ANIMATION) {
            mutex.withLock {}
            calculate(i)
            draw()
            takeFrame()
        }
    }

    private fun calculate(animationIndex: Int) {
        val cycleIndex = animationIndex % Config.FRAMES_IN_CYCLE
        val widthPercent = cycleIndex / Config.FRAMES_IN_CYCLE
        textX = widthPercent * width
    }

    private fun draw() {
        val textPaint = TextPaint()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = 20f * resources.displayMetrics.scaledDensity
        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.GRAY)
        canvas.drawText("Hello World", textX, textY, textPaint)
        holder.unlockCanvasAndPost(canvas)
    }

    private fun resetAnimationProperties() {
        textX = 0f
    }

    private suspend fun takeFrame() {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mutex.lock()
        PixelCopy.request(this, bitmap, { onBitmapReady(bitmap) }, Handler(Looper.getMainLooper()))
    }

    private fun onBitmapReady(bitmap: Bitmap) {
        renderJob = scope.launch {
            onBitmapReadyListener?.let { it(bitmap) }
            mutex.unlock()
        }
    }


}