package kr.cartone.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kr.cartone.app.core.CarEvent
import kr.cartone.app.glyph.GlyphView
import kr.cartone.app.record.VoiceRecorder
import kr.cartone.app.theme.Theme

/**
 * 녹음 중에 화면 가운데를 덮는 큰 녹음 창.
 * 어떤 이벤트를 어느 테마에 녹음하는지, 흐른 시간, [취소] [저장]을 보여준다.
 * 뒤 화면은 어둡게 가리고 누를 수 없게 한다(녹음 중 다른 조작 방지).
 */
class RecordLayer(context: Context, private val onSave: () -> Unit, private val onCancel: () -> Unit) : FrameLayout(context) {
    private val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

    init {
        visibility = GONE; alpha = 0f
        setBackgroundColor(0x99000000.toInt())
        isClickable = true // 뒤 화면 터치를 막는다
        addView(box, LayoutParams(Ui.dp(context, 860), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    /** 흐른 시간 글자와 진행 막대. 창을 띄울 때마다 새로 만든다 */
    private var time: TextView? = null
    private var bar: ProgressBar? = null

    /** 녹음 창을 띄운다. 테마 색으로 매번 새로 그린다 */
    fun show(e: CarEvent, theme: Theme) {
        val c = theme.colors
        val p = Ui.dp(context, 48)
        box.removeAllViews()
        box.setPadding(p, p, p, p)
        box.background = Ui.rounded(c.bg, Ui.dp(context, 28), Ui.alpha(c.accent, 0.4f), Ui.dp(context, 2))
        box.elevation = Ui.dp(context, 16).toFloat()
        val icon = GlyphView(context).apply { glyph = e.glyph; color = c.accent }
        box.addView(icon, LinearLayout.LayoutParams(Ui.dp(context, 240), Ui.dp(context, 240)))
        box.addView(Ui.title(context, "● ${e.label} 녹음 중", c.accent, 40f).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(context, 18), 0, 0) })
        box.addView(Ui.body(context, "'${theme.name}' 테마에 저장돼요", Ui.alpha(c.ink, 0.7f), 24f).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(context, 6), 0, 0) })
        time = Ui.title(context, "", c.ink, 76f).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(context, 20), 0, Ui.dp(context, 12)) }
        box.addView(time)
        bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(c.accent)
            progressBackgroundTintList = ColorStateList.valueOf(Ui.alpha(c.ink, 0.25f))
        }
        box.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 18)))
        box.addView(Ui.body(context, "말해 주세요 · ${VoiceRecorder.MAX_SEC}초가 되면 자동으로 저장돼요", Ui.alpha(c.ink, 0.7f), 22f).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 24)) })
        val buttons = LinearLayout(context)
        buttons.addView(Ui.pill(context, "취소", Ui.alpha(c.ink, 0.08f), c.ink) { onCancel() }.apply { minHeight = Ui.dp(context, 104); textSize = 28f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Ui.pill(context, "■ 녹음 끝내고 저장", c.accent, c.card) { onSave() }.apply { minHeight = Ui.dp(context, 104); textSize = 28f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply { marginStart = Ui.dp(context, 14) })
        box.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        progress(0f)
        animate().cancel()
        visibility = VISIBLE
        animate().alpha(1f).setDuration(140).withEndAction(null).start()
        icon.play(theme.motion)
    }

    /** 진행률(0~1)을 시간과 막대로 보여준다 */
    fun progress(p: Float) {
        time?.text = String.format("%.1f / %d.0초", p * VoiceRecorder.MAX_SEC, VoiceRecorder.MAX_SEC)
        bar?.progress = (p * 1000).toInt()
    }

    fun hide() {
        animate().cancel()
        animate().alpha(0f).setDuration(180).withEndAction { visibility = GONE }.start()
    }
}
