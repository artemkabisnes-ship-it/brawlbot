package com.example.brawlbotsimple

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.coroutines.resume

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var enemyDetector: EnemyDetector
    private lateinit var aimBot: AimBot

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        projection = mgr.getMediaProjection(code, data)

        setupCapture()
        enemyDetector = EnemyDetector(this)
        aimBot = AimBot(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

        BotState.running = true
        scope.launch { botLoop() }
        return START_STICKY
    }

    private fun setupCapture() {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val dpi = resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
        projection?.createVirtualDisplay(
            "brawl", w, h, dpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }

    private suspend fun botLoop() {
        var lastTrophyCheck = 0L
        while (BotState.running) {
            try {
                val frame = captureFrame()
                if (frame == null) {
                    delay(500)
                    continue
                }

                val inMenu = findAndTapIfPresent(frame, "btn_play.png", tap = false)
                if (inMenu) {
                    findAndTapIfPresent(frame, "btn_play.png", tap = true)
                    delay(3000)
                    continue
                }

                val enemies = enemyDetector.detectByColor(frame)
                aimBot.tick(enemies)

                val now = System.currentTimeMillis()
                if (now - lastTrophyCheck > 5000) {
                    lastTrophyCheck = now
                    val t = readTrophies(frame)
                    if (t != null && t >= BotState.targetTrophies) {
                        BotState.currentIndex++
                        if (BotState.currentIndex >= BotState.allCharacters.size) BotState.currentIndex = 0
                        BotState.targetCharacter = BotState.allCharacters[BotState.currentIndex]
                        BotState.targetTrophies += 500
                    }
                }

                delay(200)
            } catch (e: Exception) {
                e.printStackTrace()
                delay(2000)
            }
        }
    }

    private fun captureFrame(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bmp = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        } finally { image.close() }
    }

    private fun findAndTapIfPresent(bitmap: Bitmap, templateName: String, tap: Boolean): Boolean {
        val template = loadTemplate(templateName) ?: return false
        val srcMat = Mat(); val tplMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        Utils.bitmapToMat(template, tplMat)
        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(tplMat, tplMat, Imgproc.COLOR_RGBA2RGB)

        val result = Mat()
        Imgproc.matchTemplate(srcMat, tplMat, result, Imgproc.TM_CCOEFF_NORMED)
        val mm = Core.minMaxLoc(result)
        return if (mm.maxVal >= 0.75) {
            if (tap) {
                val cx = (mm.maxLoc.x + tplMat.cols() / 2).toFloat()
                val cy = (mm.maxLoc.y + tplMat.rows() / 2).toFloat()
                TapService.instance?.tap(cx, cy)
            }
            true
        } else false
    }

    private suspend fun readTrophies(bitmap: Bitmap): Int? {
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height / 6)
        val image = InputImage.fromBitmap(crop, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val numbers = Regex("\\d{2,5}").findAll(text.text)
                        .mapNotNull { it.value.toIntOrNull() }
                        .maxOrNull()
                    cont.resume(numbers)
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun loadTemplate(name: String): Bitmap? = try {
        assets.open("templates/$name").use { android.graphics.BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "bot")
            .setContentTitle("BrawlBot работает")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        BotState.running = false
        scope.cancel()
        projection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
