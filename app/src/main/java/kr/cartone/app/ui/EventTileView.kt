package kr.cartone.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import kr.cartone.app.core.CarEvent
import kr.cartone.app.glyph.GlyphView
import kr.cartone.app.theme.Theme
import kr.cartone.app.theme.ThemeColors

/**
 * 이벤트 하나의 설정 칸: 아이콘, 이름, 켜고 끄기 스위치, [듣기] [녹음] 버튼. 녹음한 칸에만 "내 녹음" 표시가 붙는다.
 * - 스위치를 끄면 그 이벤트는 주행 중에 소리도 알림도 내지 않는다(기본은 모두 켜짐).
 * - 녹음은 P단일 때만 누를 수 있다(잠금은 MainActivity가 setLocked로 알려준다).
 */
class EventTileView(
    context: Context,
    val event: CarEvent,
    private val onListen: (CarEvent) -> Unit,
    private val onRecord: (CarEvent) -> Unit,
    private val onToggle: (CarEvent, Boolean) -> Unit,
) : LinearLayout(context) {
    private val icon = GlyphView(context).apply { glyph = event.glyph }
    private val name = Ui.title(context, event.label, 0, 20f)
    private val source = Ui.body(context, "", 0, 15f)
    private val toggle = Switch(context)
    private lateinit var listen: TextView
    private lateinit var record: TextView
    private var playing = false
    private var colors: ThemeColors? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(context, 16)
        setPadding(p, p, p, p)
        addView(icon, LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 8), 0)
            addView(name); addView(source)
        }
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        toggle.minHeight = Ui.dp(context, 56); toggle.minWidth = Ui.dp(context, 72)
        addView(toggle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /** 테마 색, 녹음 여부, 켜짐 여부를 반영해 다시 그린다. master = 앱 전체 소리 스위치 */
    fun bind(theme: Theme, on: Boolean, master: Boolean = true) {
        val c = theme.colors
        colors = c
        background = Ui.rounded(c.card, Ui.dp(context, 16), Ui.alpha(c.ink, 0.07f), Ui.dp(context, 1))
        minimumHeight = Ui.dp(context, 88)
        // 꺼진 이벤트는 아이콘과 이름을 흐리게
        icon.color = if (on) c.accent else Ui.alpha(c.ink, 0.3f)
        name.setTextColor(if (on) c.ink else Ui.alpha(c.ink, 0.45f))
        source.setTextColor(Ui.alpha(c.ink, 0.65f))
        // 녹음한 칸만 표시한다(모든 칸에 "기본 소리"를 적으면 잡음일 뿐)
        val note = listOfNotNull(if (theme.isVoice(event)) "🎙 내 녹음" else null, if (on) null else "꺼짐").joinToString(" · ")
        source.text = note
        source.visibility = if (note.isEmpty()) GONE else VISIBLE
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = on
        toggle.contentDescription = "${event.label} 소리 켜기"
        toggle.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(c.accent, Ui.alpha(c.ink, 0.55f)))
        toggle.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.alpha(c.accent, 0.28f), Ui.alpha(c.ink, 0.20f)))
        toggle.setOnCheckedChangeListener { _, v -> onToggle(event, v) }
        // 앱 전체가 꺼져 있으면 이벤트별 스위치는 상태만 유지한 채 잠근다
        toggle.isEnabled = master; toggle.alpha = if (master) 1f else 0.35f
        // 버튼은 색이 바뀌므로 새로 만든다
        if (::listen.isInitialized) { removeView(listen); removeView(record) }
        listen = Ui.pill(context, "▶ 듣기", Ui.alpha(c.ink, 0.06f), c.ink) { onListen(event) }
        record = Ui.pill(context, "● 녹음", Ui.alpha(c.accent, 0.12f), c.accent) { onRecord(event) }
        record.contentDescription = "${event.label} 녹음"
        listen.minWidth = Ui.dp(context, 108) // 글자가 바뀌어도 버튼 폭이 흔들리지 않게
        showPlaying(playing)
        for (b in listOf(listen, record)) addView(b, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(context, 8) })
    }

    /** P단이 아니면 녹음을 흐리게 하고 누를 수 없게 한다 */
    fun setLocked(locked: Boolean) {
        if (!::record.isInitialized) return
        record.isEnabled = !locked; record.alpha = if (locked) 0.35f else 1f
    }

    /** 재생 중 표시: 듣기 버튼이 정지(❚❚) 모양과 강조색으로 바뀐다. 다시 누르면 멈춘다 */
    fun showPlaying(on: Boolean) {
        playing = on
        val c = colors ?: return
        if (!::listen.isInitialized) return
        listen.text = if (on) "❚❚ 정지" else "▶ 듣기"
        listen.contentDescription = if (on) "${event.label} 재생 멈추기" else "${event.label} 듣기"
        listen.setTextColor(if (on) c.card else c.ink)
        listen.background = RippleDrawable(ColorStateList.valueOf(Ui.alpha(c.ink, 0.12f)),
            Ui.rounded(if (on) c.accent else Ui.alpha(c.ink, 0.06f), Ui.dp(context, 14)), null)
    }

    /** 이벤트가 실제로 들어왔을 때 아이콘을 한 번 움직인다 */
    fun ping(theme: Theme) = icon.play(theme.motion)
}
