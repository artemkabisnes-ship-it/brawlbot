package com.example.brawlbotsimple

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(1, buildNotification())
            
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BrawlBot::BotWakeLock")
            wakeLock?.acquire(30 * 60 * 1000L)

            BotState.running = true
            scope.launch { blindBotLoop() }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
        return START_STICKY
    }

    // Слепой цикл: просто жмет кнопки по очереди
    private suspend fun blindBotLoop() {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()

        // Координаты (в процентах от экрана)
        val playButtonX = w * 0.85f  // Правый нижний угол (Играть)
        val playButtonY = h * 0.90f
        
        val attackBtnX = w * 0.85f   // Кнопка атаки
        val attackBtnY = h * 0.85f
        
        val joystickX = w * 0.15f    // Джойстик (лево)
        val joystickY = h * 0.85f

        while (BotState.running) {
            try {
                // 1. Жмем кнопку "Играть" (или "Продолжить") в правом нижнем углу
                TapService.instance?.tap(playButtonX, playButtonY)
                delay(2000) // Ждем 2 секунды

                // 2. Если мы в бою, начинаем двигаться и стрелять
                // Двигаемся вправо
                TapService.instance?.drag(joystickX, joystickY, joystickX + 200f, joystickY, 300)
                delay(500)
                // Стреляем
                TapService.instance?.tap(attackBtnX, attackBtnY)
                delay(500)
                
                // Двигаемся влево
                TapService.instance?.drag(joystickX, joystickY, joystickX - 200f, joystickY, 300)
                delay(500)
                // Стреляем
                TapService.instance?.tap(attackBtnX, attackBtnY)
                delay(500)

            } catch (e: Exception) {
                e.printStackTrace()
                delay(2000)
            }
        }
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "bot")
            .setContentTitle("BrawlBot работает (слепой режим)")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        BotState.running = false
        wakeLock?.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
