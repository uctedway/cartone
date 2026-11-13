package kr.cartone.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** 화면 공용 도우미: dp 변환, 둥근 배경, 알약 버튼, 글자 스타일, 색 투명도 */
object Ui {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    /** 모서리가 둥근 배경. strokeColor를 주면 테두리를 그린다 */
    fun rounded(color: Int, radius: Int, strokeColor: Int? = null, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
        if (strokeColor != null) setStroke(strokeW, strokeColor)
    }

    /** 차 화면에서 누르기 쉬운 큰 알약 버튼(최소 높이 56dp) */
    fun pill(c: Context, text: String, bg: Int, fg: Int, onClick: (View) -> Unit) = TextView(c).apply {
        this.text = text; textSize = 18f; typeface = MEDIUM; setTextColor(fg)
        gravity = Gravity.CENTER; minHeight = dp(c, 56); minWidth = dp(c, 88)
        setPadding(dp(c, 16), 0, dp(c, 16), 0)
        background = RippleDrawable(ColorStateList.valueOf(alpha(fg, 0.12f)), rounded(bg, dp(c, 14)), null)
        isFocusable = true
        setOnClickListener(onClick)
    }

    fun title(c: Context, text: String, color: Int, size: Float = 30f) = TextView(c).apply {
        this.text = text; textSize = size; typeface = MEDIUM; setTextColor(color)
    }

    fun body(c: Context, text: String, color: Int, size: Float = 18f) = TextView(c).apply {
        this.text = text; textSize = size; setTextColor(color)
    }

    /** 색의 투명도만 바꾼다(0~1) */
    fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}
