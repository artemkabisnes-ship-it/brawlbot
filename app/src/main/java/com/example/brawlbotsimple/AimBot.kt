package com.example.brawlbotsimple

import org.opencv.core.Point

class AimBot(private val screenW: Int, private val screenH: Int) {

    private val attackBtnX = screenW * 0.85f
    private val attackBtnY = screenH * 0.88f

    // Центр джойстика (слева внизу)
    private val joystickCenterX = screenW * 0.15f
    private val joystickCenterY = screenH * 0.85f
    private val joystickRadius = screenH * 0.10f

    // Где находится наш персонаж (примерно центр экрана)
    private val playerX = screenW * 0.5
    private val playerY = screenH * 0.5
    
    // Радиус атаки (если враг ближе, можно стрелять)
    private val attackRange = screenW * 0.40

    fun tick(enemies: List<Point>) {
        if (enemies.isEmpty()) {
            // Если врагов нет, двигаемся вперед (вправо), чтобы найти их
            moveJoystick(1.0, 0.0, 300) 
            return
        }

        // Берем ближайшего врага
        val nearest = enemies.minByOrNull { Math.hypot(it.x - playerX, it.y - playerY) } ?: return
        val dist = Math.hypot(nearest.x - playerX, nearest.y - playerY)

        // Двигаемся в сторону врага, если он далеко
        if (dist > attackRange * 0.6) {
            val dx = nearest.x - playerX
            val dy = nearest.y - playerY
            val len = Math.hypot(dx, dy).coerceAtLeast(1.0)
            // Нормализуем вектор направления
            moveJoystick(dx / len, dy / len, 300)
        }

        // Если враг в радиусе атаки — стреляем
        if (dist <= attackRange) {
            TapService.instance?.tap(attackBtnX, attackBtnY)
        }
    }

    private fun moveJoystick(dx: Double, dy: Double, durationMs: Long) {
        val toX = joystickCenterX + (dx * joystickRadius).toFloat()
        val toY = joystickCenterY + (dy * joystickRadius).toFloat()
        TapService.instance?.drag(joystickCenterX, joystickCenterY, toX, toY, durationMs)
    }
}
