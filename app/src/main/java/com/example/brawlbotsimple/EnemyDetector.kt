package com.example.brawlbotsimple

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class EnemyDetector(private val context: android.content.Context) {

    fun detectByColor(screen: Bitmap): List<Point> {
        val mat = Mat()
        Utils.bitmapToMat(screen, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)

        val gameArea = mat.submat(0, (mat.rows() * 0.82).toInt(), 0, mat.cols())

        val hsv = Mat()
        Imgproc.cvtColor(gameArea, hsv, Imgproc.COLOR_RGB2HSV)

        val mask = Mat()
        Core.inRange(hsv, Scalar(0.0, 140.0, 140.0), Scalar(10.0, 255.0, 255.0), mask)
        val mask2 = Mat()
        Core.inRange(hsv, Scalar(170.0, 140.0, 140.0), Scalar(180.0, 255.0, 255.0), mask2)
        Core.bitwise_or(mask, mask2, mask)

        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 1.0)))

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val enemies = mutableListOf<Point>()
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            val aspect = r.width.toFloat() / r.height.toFloat()
            if (r.width in 40..250 && r.height in 5..20 && aspect in 2.5f..20f) {
                enemies.add(Point((r.x + r.width / 2.0), (r.y + r.height / 2.0)))
            }
        }
        return mergeNearby(enemies, threshold = 80.0)
    }

    private fun mergeNearby(points: List<Point>, threshold: Double): List<Point> {
        val result = mutableListOf<Point>()
        for (p in points) {
            if (result.none { dist(it, p) < threshold }) result.add(p)
        }
        return result
    }

    private fun dist(a: Point, b: Point) = Math.hypot(a.x - b.x, a.y - b.y)
}
