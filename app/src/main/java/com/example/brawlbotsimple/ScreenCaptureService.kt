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
import java.io.File

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var enemyDetector: EnemyDetector
    private lateinit var aimBot: AimBot
    
    // Список всех наших картинок
    private var templates: List<Pair<String, Bitmap>> = emptyList()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        projection = mgr.getMediaProjection(code, data)

        setupCapture()
        enemyDetector = EnemyDetector(this)
        aimBot = AimBot(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        
        loadAllTemplates() // Загружаем картинки

        BotState.running = true
        scope.launch { botLoop() }
        return START_STICKY
    }

    // Читаем ВСЕ картинки из папки templates
    private fun loadAllTemplates() {
        try {
            val files = assets.list("templates") ?: emptyArray()
            templates = files.mapNotNull { fileName ->
                if (fileName.endsWith(".png") || fileName.endsWith(".jpg")) {
                    val bmp = assets.open("templates/$fileName").use { android.graphics.BitmapFactory.decodeStream(it) }
                    if (bmp != null) fileName to bmp else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        var lastTrophyCheck = 0L
        while (BotState.running) {
            try {
                val frame = captureFrame()
                if (frame == null) {
                    delay(500)
                    continue
                }

                // Проверяем, не в меню ли мы
                val inMenu = checkAndTapTemplate(frame, isPlayButton = true, tap = false)
                if (inMenu) {
                    checkAndTapTemplate(frame, isPlayButton = true, tap = true)
                    delay(3000)
                    continue
                }

                // В бою: ищем врагов и стреляем
                val enemies = enemyDetector.detectByColor(frame)
                aimBot.tick(enemies)

                // Раз в 5 секунд проверяем трофеи
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

    // Ищем картинку на экране. isPlayButton = true означает, что мы ищем именно желтую кнопку
    private fun checkAndTapTemplate(bitmap: Bitmap, isPlayButton: Boolean, tap: Boolean): Boolean {
        if (templates.isEmpty()) return false
        
        // Берем первую картинку из списка (у нас их две: кнопка Играть и кнопка Атака)
        // Чтобы не путать, мы просто проверяем обе
        for ((fileName, template) in templates) {
            // Определяем, является ли эта картинка кнопкой "Играть" (по размеру или пропорциям)
            // Желтая кнопка широкая, а атака квадратная. 
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
            
            if (mm.maxVal >= 0.70) { // 70% совпадения достаточно
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
