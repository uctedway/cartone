package kr.carsound.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kr.carsound.app.core.CarEvent
import kr.carsound.app.glyph.GlyphView
import kr.carsound.app.theme.Theme
import kr.carsound.app.ui.Ui

/**
 * 내비 위에 잠깐 뜨는 알림 띠(아이콘 + 짧은 글자).
 * - 위치: 화면 오른쪽 위. 깜빡이 때 Pleos가 왼쪽 위에 측면 카메라를 띄우므로 그 자리를 피한다.
 * - 이벤트마다 정해진 시간만큼 떠 있다. 깜빡이처럼 이어지는 상태는 꺼질 때까지 떠 있는다.
 * - 터치를 받지 않아 운전 조작을 막지 않는다.
 * - "다른 앱 위에 표시" 권한이 없으면 띄우지 않고 소리만 낸다.
 */
class EventOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var card: LinearLayout? = null
    private var glyph: GlyphView? = null
    private var label: TextView? = null

    /** 꺼질 때까지 띄워 두는 이벤트(깜빡이). 다른 알림이 잠깐 덮었다가 사라지면 이 표시로 돌아온다 */
    private var held: Shown? = null
    private val hide = Runnable { held?.let { render(it, slide = false) } ?: fadeOut() }

    /** 알림 띠 한 번에 그릴 내용 */
    private class Shown(val e: CarEvent, val theme: Theme, val voice: Boolean)

    /** "다른 앱 위에 표시" 권한이 있는지 */
    fun canShow() = Settings.canDrawOverlays(context)

    /** 알림 띠를 띄운다. voice = 녹음한 목소리가 난 경우(마이크 표시를 붙임). holdMs = null이면 release()까지 떠 있는다 */
    fun show(e: CarEvent, theme: Theme, voice: Boolean, holdMs: Long?) {
        if (!canShow()) return
        val s = Shown(e, theme, voice)
        if (holdMs == null) held = s
        render(s, slide = true)
        if (holdMs != null) main.postDelayed(hide, holdMs)
    }

    /** 이어지는 이벤트의 소리가 다시 날 때 아이콘만 한 번 더 움직인다 */
    fun pulse(e: CarEvent) {
        val h = held ?: return
        if (h.e == e && label?.tag == e) glyph?.play(h.theme.motion)
    }

    /** 이어지던 이벤트가 끝났다. 그 표시가 떠 있으면 내린다 */
    fun release(e: CarEvent) {
        if (held?.e != e) return
        held = null
        if (label?.tag == e) { main.removeCallbacks(hide); fadeOut() }
    }

    /** 내용을 그리고 보여준다. slide = 옆에서 미끄러져 들어오기 */
    private fun render(s: Shown, slide: Boolean) {
        runCatching {
            val v = card ?: build().also { attach(it) }
            val bg = v.background as GradientDrawable
            bg.setColor(s.theme.colors.card); bg.setStroke(dp(1), Ui.alpha(s.theme.colors.accent, 0.45f))
            glyph?.apply { glyph = s.e.glyph; color = s.theme.colors.accent; play(s.theme.motion) }
            label?.apply { text = if (s.voice) "🎙 ${s.e.label}" else s.e.label; setTextColor(s.theme.colors.ink); tag = s.e }
            v.animate().cancel()
            if (slide) { v.alpha = 0f; v.translationX = 60f }
            v.animate().alpha(1f).translationX(0f).setDuration(180).withEndAction(null).start()
            main.removeCallbacks(hide)
        }.onFailure { Log.w("CarSound", "알림 띠 실패 $it") }
    }

    /** 옆으로 밀려나며 사라진다 */
    private fun fadeOut() {
        card?.animate()?.alpha(0f)?.translationX(60f)?.setDuration(220)?.withEndAction { detach() }
    }

    /** 알림 띠 뷰(아이콘 + 글자)를 만든다 */
    private fun build(): LinearLayout {
        val g = GlyphView(context)
        val t = TextView(context).apply { textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), 0, dp(6), 0) }
        glyph = g; label = t
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(30), dp(16))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat() }
            elevation = dp(8).toFloat()
            addView(g, LinearLayout.LayoutParams(dp(64), dp(64)))
            addView(t)
        }.also { card = it }
    }

    /** 터치를 받지 않는 창으로 화면 오른쪽 위에 붙인다 */
    private fun attach(v: View) {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(40); y = dp(110) }
        wm.addView(v, lp)
    }

    /** 사라진 뒤 창에서 뗀다 */
    private fun detach() {
        card?.let { runCatching { wm.removeView(it) } }
        card = null; glyph = null; label = null
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}
