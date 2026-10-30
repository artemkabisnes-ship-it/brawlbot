package com.example.brawlbotsimple

import org.opencv.core.Point

class AimBot(private val screenW: Int, private val screenH: Int) {

    private val attackBtnX = screenW * 0.87f
    private val attackBtnY = screenH * 0.88f

    private val joystickCenterX = screenW * 0.15f
    private val joystickCenterY = screenH * 0.88f
    private val joystickRadius  = screenH * 0.10f

    private val playerX = screenW * 0.5
    private val playerY = screenH * 0.55
    private val attackRange = screenW * 0.35

    fun tick(enemies: List<Point>) {
        if (enemies.isEmpty()) return

        val nearest = enemies.minByOrNull { Math.hypot(it.x - playerX, it.y - playerY) } ?: return
        val dist = Math.hypot(nearest.x - playerX, nearest.y - playerY)

        if (dist > attackRange * 0.7) {
            moveToward(nearest.x, nearest.y, durationMs = 400)
        }
        if (dist <= attackRange) {
            TapService.instance?.tap(attackBtnX, attackBtnY)
        }
    }

    private fun moveToward(targetX: Double, targetY: Double, durationMs: Long) {
        val dx = targetX - playerX
        val dy = targetY - playerY
        val len = Math.hypot(dx, dy).coerceAtLeast(1.0)

        val toX = joystickCenterX + (dx / len * joystickRadius).toFloat()
        val toY = joystickCenterY + (dy / len * joystickRadius).toFloat()

        TapService.instance?.drag(joystickCenterX, joystickCenterY, toX, toY, durationMs)
    }
}
