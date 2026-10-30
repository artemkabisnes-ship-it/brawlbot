package com.example.brawlbotsimple

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
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
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var templates: List<Pair<String, Bitmap>> = emptyList()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(1, buildNotification())
            
            // Не даем телефону усыпить процесс
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BrawlBot::BotWakeLock")
            wakeLock?.acquire(10 * 60 * 1000L) // 10 минут

            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val code = intent?.getIntExtra("code", 0) ?: 0
            val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
            
            projection = mgr.getMediaProjection(code, data)
            setupCapture()
            
            enemyDetector = EnemyDetector(this)
            aimBot = AimBot(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            loadAllTemplates()

            BotState.running = true
            scope.launch { botLoop() }
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка: ${e.message}")
        }
        return START_STICKY
    }

    private fun loadAllTemplates() {
        try {
            val files = assets.list("templates") ?: emptyArray()
            templates = files.mapNotNull { fileName ->
                if (fileName.endsWith(".png") || fileName.endsWith(".jpg")) {
                    val bmp = assets.open("templates/$fileName").use { android.graphics.BitmapFactory.decodeStream(it) }
                    if (bmp != null) fileName to bmp else null
                } else null
            }
            if (templates.isEmpty()) {
                updateNotification("Ошибка: картинки не найдены!")
            } else {
                updateNotification("Картинки загружены: ${templates.size}")
            }
        } catch (e: Exception) {
            updateNotification("Ошибка картинок: ${e.message}")
        }
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
        var inMenuCount = 0
        while (BotState.running) {
            try {
                val frame = captureFrame()
                if (frame == null) {
                    delay(500)
                    continue
                }

                val inMenu = checkAndTapTemplate(frame, isPlayButton = true, tap = false)
                if (inMenu) {
                    inMenuCount++
                    if (inMenuCount > 3) { // Если видим кнопку 3 раза подряд, нажимаем
                        checkAndTapTemplate(frame, isPlayButton = true, tap = true)
                        inMenuCount = 0
                        delay(3000)
                    }
                } else {
                    inMenuCount = 0
                    val enemies = enemyDetector.detectByColor(frame)
                    aimBot.tick(enemies)
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

    private fun checkAndTapTemplate(bitmap: Bitmap, isPlayButton: Boolean, tap: Boolean): Boolean {
        if (templates.isEmpty()) return false
        for ((_, template) in templates) {
            val isPlayShape = template.width > template.height * 1.5
            if (isPlayShape != isPlayButton) continue

            val srcMat = Mat(); val tplMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)
            Utils.bitmapToMat(template, tplMat)
            Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(tplMat, tplMat, Imgproc.COLOR_RGBA2RGB)

            val result = Mat()
            Imgproc.matchTemplate(srcMat, tplMat, result, Imgproc.TM_CCOEFF_NORMED)
            val mm = Core.minMaxLoc(result)
            
            if (mm.maxVal >= 0.65) { // Снизил порог до 65% для лучшего распознавания
                if (tap) {
                    val cx = (mm.maxLoc.x + tplMat.cols() / 2).toFloat()
                    val cy = (mm.maxLoc.y + tplMat.rows() / 2).toFloat()
                    TapService.instance?.tap(cx, cy)
                }
                return true
            }
        }
        return false
    }

    private fun showToast(text: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(this@ScreenCaptureService, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, "bot")
            .setContentTitle("BrawlBot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        nm.notify(1, notif)
    }

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
        wakeLock?.release()
        scope.cancel()
        projection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
