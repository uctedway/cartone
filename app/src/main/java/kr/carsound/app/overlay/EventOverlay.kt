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
 * - 1.8초 뒤 사라지고, 터치를 받지 않아 운전 조작을 막지 않는다.
 * - "다른 앱 위에 표시" 권한이 없으면 띄우지 않고 소리만 낸다.
 */
class EventOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var card: LinearLayout? = null
    private var glyph: GlyphView? = null
    private var label: TextView? = null
    private val hide = Runnable { card?.animate()?.alpha(0f)?.translationX(60f)?.setDuration(220)?.withEndAction { detach() } }

    /** "다른 앱 위에 표시" 권한이 있는지 */
    fun canShow() = Settings.canDrawOverlays(context)

    /** 알림 띠를 띄운다. voice = 녹음한 목소리가 난 경우(마이크 표시를 붙임) */
    fun show(e: CarEvent, theme: Theme, voice: Boolean) {
        if (!canShow()) return
        runCatching {
            val v = card ?: build().also { attach(it) }
            val bg = v.background as GradientDrawable
            bg.setColor(theme.colors.card); bg.setStroke(dp(1), Ui.alpha(theme.colors.accent, 0.45f))
            glyph?.apply { glyph = e.glyph; color = theme.colors.accent; play(theme.motion) }
            label?.apply { text = if (voice) "🎙 ${e.label}" else e.label; setTextColor(theme.colors.ink) }
            v.animate().cancel()
            v.alpha = 0f; v.translationX = 60f
            v.animate().alpha(1f).translationX(0f).setDuration(180).start()
            main.removeCallbacks(hide)
            main.postDelayed(hide, SHOW_MS)
        }.onFailure { Log.w("CarSound", "알림 띠 실패 $it") }
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

    companion object { private const val SHOW_MS = 1800L }
}
