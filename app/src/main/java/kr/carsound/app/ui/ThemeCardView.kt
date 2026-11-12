package kr.carsound.app.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import kr.carsound.app.core.Glyph
import kr.carsound.app.glyph.GlyphView
import kr.carsound.app.theme.Theme

/**
 * 테마 고르기 카드. 테마 색으로 칠하고 대표 아이콘 3개를 테마 움직임으로 보여준다.
 * 같은 아이콘이라도 테마마다 색·움직임이 달라지는 게 이 앱의 그래픽 원칙.
 */
class ThemeCardView(context: Context, val theme: Theme, onPick: (Theme) -> Unit, onLong: ((Theme) -> Unit)? = null) : LinearLayout(context) {
    private val state = Ui.body(context, "", theme.colors.accent, 16f)
    private val icons = listOf(Glyph.ARROW_LEFT, Glyph.GEAR_D, Glyph.WINDOW_DOWN).map { g ->
        GlyphView(context).apply { glyph = g; color = theme.colors.accent }
    }

    init {
        orientation = VERTICAL
        val p = Ui.dp(context, 18)
        setPadding(p, p, p, p)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            icons.forEach { addView(it, LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32)).apply { marginEnd = Ui.dp(context, 8) }) }
        }
        row.addView(state, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { state.gravity = Gravity.END or Gravity.CENTER_VERTICAL })
        addView(row)
        addView(Ui.title(context, (if (theme.isCustom) "✎ " else "") + theme.name, theme.colors.ink, 24f).apply { setPadding(0, Ui.dp(context, 10), 0, 0) })
        addView(Ui.body(context, theme.subtitle, Ui.alpha(theme.colors.ink, 0.7f), 17f))
        setOnClickListener { onPick(theme); icons.forEach { it.play(theme.motion) } }
        // 커스텀 테마는 길게 눌러 지울 수 있다
        if (onLong != null) setOnLongClickListener { onLong(theme); true }
        setSelected(false)
    }

    /** 고른 테마는 강조색 테두리를 굵게 */
    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        state.text = if (selected) "✓ 적용 중" else ""
        val r = Ui.dp(context, 18)
        background = if (selected) Ui.rounded(theme.colors.card, r, theme.colors.accent, Ui.dp(context, 2))
        else Ui.rounded(theme.colors.card, r, Ui.alpha(theme.colors.ink, 0.12f), Ui.dp(context, 1))
    }
}
