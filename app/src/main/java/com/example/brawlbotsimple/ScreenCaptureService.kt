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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var enemyDetector: EnemyDetector
    private lateinit var aimBot: AimBot
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(1, buildNotification())
            
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BrawlBot::BotWakeLock")
            wakeLock?.acquire(30 * 60 * 1000L)

            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val code = intent?.getIntExtra("code", 0) ?: 0
            val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
            
            projection = mgr.getMediaProjection(code, data)
            setupCapture()
            
            enemyDetector = EnemyDetector(this)
            aimBot = AimBot(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

            BotState.running = true
            scope.launch { botLoop() }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        
        // Координаты правого нижнего угла (кнопка Играть)
        val playBtnX = screenW * 0.85f
        val playBtnY = screenH * 0.90f

        while (BotState.running) {
            try {
                val frame = captureFrame()
                if (frame == null) {
                    delay(500)
                    continue
                }

                // 1. Пытаемся понять, в меню мы или в бою.
                // Если мы в меню, кнопка "Играть" обычно ярко-желтая.
                // Но чтобы не гадать, мы просто делаем слепой тап в правый нижний угол раз в 3 секунды.
                // В меню это нажмет "Играть", в бою это нажмет атаку (не страшно).
                TapService.instance?.tap(playBtnX, playBtnY)
                delay(3000)

                // 2. В бою (после тапа) ищем врагов и бежим к ним
                val enemies = enemyDetector.detectByColor(frame)
                aimBot.tick(enemies)
                
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

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "bot")
            .setContentTitle("BrawlBot работает (Гибрид)")
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
