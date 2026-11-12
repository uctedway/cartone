package kr.carsound.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import kr.carsound.app.core.Glyph
import kr.carsound.app.glyph.GlyphPainter
import kr.carsound.app.theme.ThemeColors

/**
 * 화면 가운데에 이벤트 아이콘을 크게 잠깐 보여주는 판. 빠르게 페이드 인/아웃한다.
 * - 소리 재생 중: 아이콘만
 * - 이벤트를 켰을 때: 아이콘 + 스피커 표시
 * - 이벤트를 껐을 때: 아이콘 + 금지 표시(아이콘 위에 겹침)
 * 터치는 받지 않는다(아래 화면을 그대로 누를 수 있음).
 */
class CenterStage(context: Context) : FrameLayout(context) {
    /** 아이콘 위에 겹쳐 그리는 표시 */
    enum class Badge { NONE, SPEAKER, BAN }

    private val art = Art(context)
    private val hideLater = Runnable { hide() }

    init {
        alpha = 0f; visibility = GONE
        val p = Ui.dp(context, 44)
        setPadding(p, p, p, p)
        elevation = Ui.dp(context, 12).toFloat()
        addView(art, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** holdMs 뒤에 스스로 사라진다. null이면 hide()를 부를 때까지 떠 있다 */
    fun show(glyph: Glyph, colors: ThemeColors, badge: Badge = Badge.NONE, holdMs: Long? = null) {
        removeCallbacks(hideLater)
        art.glyph = glyph; art.color = colors.accent; art.badge = badge; art.card = colors.card
        art.dim = badge == Badge.BAN
        art.invalidate()
        background = Ui.rounded(Ui.alpha(colors.card, 0.95f), Ui.dp(context, 40), Ui.alpha(colors.accent, 0.35f), Ui.dp(context, 2))
        animate().cancel()
        visibility = VISIBLE
        animate().alpha(1f).setDuration(140).withEndAction(null).start()
        if (holdMs != null) postDelayed(hideLater, holdMs)
    }

    /** 빠르게 페이드 아웃하고 숨긴다 */
    fun hide() {
        removeCallbacks(hideLater)
        animate().cancel()
        animate().alpha(0f).setDuration(200).withEndAction { visibility = GONE }.start()
    }

    /** 아이콘과 겹침 표시를 그리는 뷰 */
    private class Art(context: Context) : View(context) {
        var glyph = Glyph.GEAR_D
        var color = Color.WHITE
        var card = Color.BLACK
        var badge = Badge.NONE
        var dim = false
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        /** 아이콘을 그리고, 100×100 좌표계로 맞춘 뒤 표시를 겹쳐 그린다 */
        override fun onDraw(c: Canvas) {
            val s = minOf(width, height).toFloat()
            c.save(); c.translate((width - s) / 2f, (height - s) / 2f)
            // 꺼진 이벤트는 아이콘을 흐리게 하고 그 위에 금지 표시를 겹친다
            GlyphPainter.draw(c, glyph, s, if (dim) Ui.alpha(color, 0.35f) else color)
            c.scale(s / 100f, s / 100f)
            when (badge) {
                Badge.BAN -> {
                    stroke.color = BAN; stroke.strokeWidth = 9f
                    c.drawCircle(50f, 50f, 46f, stroke)
                    c.drawLine(17.5f, 17.5f, 82.5f, 82.5f, stroke)
                }
                Badge.SPEAKER -> {
                    // 오른쪽 아래에 둥근 바탕을 깔고 스피커를 그린다
                    fill.color = color; c.drawCircle(78f, 78f, 24f, fill)
                    fill.color = card
                    val body = Path().apply { moveTo(64f, 73f); lineTo(71f, 73f); lineTo(80f, 65f); lineTo(80f, 91f); lineTo(71f, 83f); lineTo(64f, 83f); close() }
                    c.drawPath(body, fill)
                    stroke.color = card; stroke.strokeWidth = 3.5f
                    c.drawArc(76f, 70f, 92f, 86f, -50f, 100f, false, stroke)
                }
                Badge.NONE -> Unit
            }
            c.restore()
        }

        companion object { private val BAN = Color.parseColor("#E5484D") }
    }
}
