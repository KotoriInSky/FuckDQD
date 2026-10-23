package io.github.kotoriinsky.fuckdqd.hook

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 底部「圈子」图标：圆形说话气泡（右下角带尾巴）+ 中间三道短横，线条风格，
 * 线宽 / 尺寸对齐底栏「首页」「数据」图标。
 * 未选中是线条图标；[filled] 时（选中态）和「比赛」一样是实心色块，里面的短横镂空成 [holeColor]。
 * 模块资源进不了懂球帝进程，所以直接用 Canvas 画，不依赖任何资源文件。
 */
class CircleTabDrawable(color: Int, private val filled: Boolean = false, holeColor: Int = Color.WHITE) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    private val dash = Paint(stroke).apply { this.color = if (filled) holeColor else color }
    private val bubble = Path()
    private val oval = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = min(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        // 图标在 24 格网格里画，再缩放到实际大小
        val unit = size / 24f
        val left = b.left + (b.width() - size) / 2f
        val top = b.top + (b.height() - size) / 2f
        val cx = left + 11.5f * unit
        val cy = top + 11.5f * unit
        val r = 8.5f * unit
        stroke.strokeWidth = 2f * unit

        // 气泡：圆从尾巴右侧（35°）开始画到尾巴左侧（65°），中间用尾巴连起来；角度顺时针、0° 朝右
        oval.set(cx - r, cy - r, cx + r, cy + r)
        bubble.reset()
        bubble.moveTo(cx + r * cos(rad(65f)), cy + r * sin(rad(65f)))
        bubble.arcTo(oval, 65f, 330f)
        bubble.lineTo(left + 21f * unit, top + 21f * unit)
        bubble.close()
        if (filled) canvas.drawPath(bubble, fill)
        canvas.drawPath(bubble, stroke)

        // 三道短横
        val half = 1.1f * unit
        dash.strokeWidth = 2.2f * unit
        for (dx in floatArrayOf(-3.8f, 0f, 3.8f)) {
            val x = cx + dx * unit
            canvas.drawLine(x - half, cy, x + half, cy, dash)
        }
    }

    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
        dash.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        dash.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
