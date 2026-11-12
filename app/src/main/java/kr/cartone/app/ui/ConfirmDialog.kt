package kr.cartone.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.LinearLayout
import kr.cartone.app.theme.ThemeColors

/**
 * 되돌릴 수 없는 동작(내 테마 삭제)을 한 번 더 묻는 대화상자.
 * 차 화면에 맞춰 버튼을 크게 하고, 테마 색을 그대로 써서 화면과 어울리게 한다.
 */
object ConfirmDialog {
    /** [취소]와 위험 동작 버튼(action) 두 개를 보여준다. action을 누르면 onAction을 부른다 */
    fun show(c: Context, colors: ThemeColors, title: String, message: String, action: String, onAction: () -> Unit) {
        val dialog = Dialog(c)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = Ui.dp(c, 32)
        // 어두운 테마에서는 밝은 빨강, 밝은 테마에서는 짙은 빨강으로 대비를 맞춘다
        val danger = Color.parseColor(if (Color.red(colors.bg) < 128) "#FF9BA8" else "#AD3049")
        val buttons = LinearLayout(c).apply {
            addView(Ui.pill(c, "취소", Ui.alpha(colors.ink, 0.07f), colors.ink) { dialog.dismiss() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.pill(c, action, Ui.alpha(danger, 0.12f), danger) { dialog.dismiss(); onAction() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(c, 12) })
        }
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = Ui.rounded(colors.bg, Ui.dp(c, 20))
            addView(Ui.title(c, title, colors.ink, 26f))
            addView(Ui.body(c, message, Ui.alpha(colors.ink, 0.75f), 20f).apply { setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 28)) })
            addView(buttons)
        }
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        // 화면이 좁으면 좌우 여백을 남기고, 넓으면 620dp까지만
        val width = minOf(Ui.dp(c, 620), c.resources.displayMetrics.widthPixels - Ui.dp(c, 64))
        dialog.window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
}
