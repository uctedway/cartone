package kr.cartone.app.glyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kr.cartone.app.core.Glyph
import kr.cartone.app.theme.Motion

/**
 * 이벤트 아이콘 한 개를 그리는 뷰. 테마 움직임에 맞춰 한 번 튀거나(BOUNCE) 번쩍인다(PULSE).
 */
class GlyphView(context: Context) : View(context) {
    var glyph: Glyph = Glyph.GEAR_D
        set(v) { field = v; invalidate() }
    var color: Int = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }

    private var scaleNow = 1f
    private var alphaNow = 1f

    fun play(motion: Motion) {
        val anim = when (motion) {
            Motion.BOUNCE -> ValueAnimator.ofFloat(0.4f, 1f).apply { interpolator = OvershootInterpolator(3f); duration = 520 }
            Motion.PULSE -> ValueAnimator.ofFloat(1.35f, 1f).apply { interpolator = DecelerateInterpolator(); duration = 380 }
        }
        anim.addUpdateListener {
            scaleNow = it.animatedValue as Float
            alphaNow = if (motion == Motion.PULSE) 0.6f + 0.4f * it.animatedFraction else 1f
            invalidate()
        }
        anim.start()
    }

    /** 뷰 가운데 정사각형 안에 지금 크기·투명도로 아이콘을 그린다 */
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        c.save()
        c.translate((width - s) / 2f, (height - s) / 2f)
        c.scale(scaleNow, scaleNow, s / 2f, s / 2f)
        val a = (alphaNow * 255).toInt().coerceIn(0, 255)
        GlyphPainter.draw(c, glyph, s, (color and 0x00FFFFFF) or (a shl 24))
        c.restore()
    }
}
