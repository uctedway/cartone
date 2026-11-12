package kr.carsound.app.glyph

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kr.carsound.app.core.Glyph

/**
 * 이벤트 아이콘을 코드로 그린다(이미지 파일 없이 어떤 크기·색에서도 선명하게).
 * 모든 그림은 100x100 칸을 기준으로 그리고, 테마 색만 바꿔 쓴다.
 */
object GlyphPainter {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** size 크기의 정사각형 안에 그림을 그린다 */
    fun draw(c: Canvas, g: Glyph, size: Float, color: Int) {
        c.save()
        c.scale(size / 100f, size / 100f)
        stroke.color = color; stroke.strokeWidth = 8f
        fill.color = color; text.color = color
        when (g) {
            Glyph.GEAR_D -> gear(c, "D")
            Glyph.GEAR_P -> gear(c, "P")
            Glyph.GEAR_R -> gear(c, "R")
            Glyph.ARROW_LEFT -> arrow(c, left = true)
            Glyph.ARROW_RIGHT -> arrow(c, left = false)
            Glyph.WINDOW_DOWN -> window(c, down = true)
            Glyph.WINDOW_UP -> window(c, down = false)
            Glyph.DOOR_OPEN -> door(c, open = true)
            Glyph.DOOR_CLOSE -> door(c, open = false)
            Glyph.BELT -> belt(c)
            Glyph.WIPER -> wiper(c)
            Glyph.SNOW -> snow(c, off = false)
            Glyph.SNOW_OFF -> snow(c, off = true)
            Glyph.BABY -> baby(c)
        }
        c.restore()
    }

    /** 기어: 둥근 네모 안에 큰 글자 */
    private fun gear(c: Canvas, letter: String) {
        c.drawRoundRect(RectF(14f, 14f, 86f, 86f), 22f, 22f, stroke)
        text.textSize = 56f
        c.drawText(letter, 50f, 70f, text)
    }

    /** 깜빡이: 굵은 화살표(계기판 깜빡이 모양) */
    private fun arrow(c: Canvas, left: Boolean) {
        val p = Path().apply {
            moveTo(10f, 50f); lineTo(46f, 18f); lineTo(46f, 36f); lineTo(90f, 36f)
            lineTo(90f, 64f); lineTo(46f, 64f); lineTo(46f, 82f); close()
        }
        if (!left) { c.save(); c.scale(-1f, 1f, 50f, 50f) }
        c.drawPath(p, fill)
        if (!left) c.restore()
    }

    /** 창문: 옆 유리창 모양 + 위/아래 화살표, 유리는 반만 칠해 열린 정도를 보여준다 */
    private fun window(c: Canvas, down: Boolean) {
        val frame = Path().apply { moveTo(16f, 78f); lineTo(16f, 36f); quadTo(18f, 20f, 36f, 18f); lineTo(70f, 18f); lineTo(70f, 78f); close() }
        c.drawPath(frame, stroke)
        val glassTop = if (down) 56f else 30f
        c.drawRect(24f, glassTop, 62f, 72f, fill)
        stroke.strokeWidth = 7f
        val y1 = if (down) 32f else 70f; val y2 = if (down) 70f else 32f
        c.drawLine(86f, y1, 86f, y2, stroke)
        c.drawLine(86f, y2, 79f, y2 + if (down) -8f else 8f, stroke)
        c.drawLine(86f, y2, 93f, y2 + if (down) -8f else 8f, stroke)
    }

    /** 문: 차 문 옆모습. 열림은 문이 바깥으로 벌어진 모양 */
    private fun door(c: Canvas, open: Boolean) {
        if (open) { c.save(); c.skew(0f, -0.18f); c.translate(0f, 10f) }
        val p = Path().apply { moveTo(22f, 84f); lineTo(22f, 40f); lineTo(42f, 16f); lineTo(80f, 16f); lineTo(80f, 84f); close() }
        c.drawPath(p, stroke)
        c.drawLine(28f, 44f, 74f, 44f, stroke)
        c.drawLine(60f, 58f, 70f, 58f, stroke)
        if (open) c.restore()
        if (open) { stroke.strokeWidth = 5f; c.drawLine(14f, 30f, 14f, 88f, stroke) }
    }

    /** 안전벨트: 사람 머리와 어깨, 대각선 벨트 */
    private fun belt(c: Canvas) {
        c.drawCircle(50f, 24f, 12f, fill)
        c.drawRoundRect(RectF(26f, 42f, 74f, 92f), 18f, 18f, stroke)
        stroke.strokeWidth = 10f
        c.drawLine(32f, 46f, 70f, 88f, stroke)
    }

    /** 와이퍼: 앞유리 곡선, 와이퍼 팔, 빗방울 */
    private fun wiper(c: Canvas) {
        val glass = Path().apply { moveTo(8f, 80f); quadTo(50f, 6f, 92f, 80f) }
        c.drawPath(glass, stroke)
        c.drawLine(50f, 84f, 26f, 40f, stroke)
        c.drawCircle(50f, 84f, 6f, fill)
        c.drawCircle(62f, 44f, 5f, fill); c.drawCircle(74f, 58f, 5f, fill)
    }

    /** 에어컨: 눈송이. 끄기는 사선으로 지운다 */
    private fun snow(c: Canvas, off: Boolean) {
        for (a in 0 until 3) {
            c.save(); c.rotate(a * 60f, 50f, 50f)
            c.drawLine(50f, 14f, 50f, 86f, stroke)
            c.drawLine(50f, 26f, 40f, 16f, stroke); c.drawLine(50f, 26f, 60f, 16f, stroke)
            c.drawLine(50f, 74f, 40f, 84f, stroke); c.drawLine(50f, 74f, 60f, 84f, stroke)
            c.restore()
        }
        if (off) { stroke.strokeWidth = 10f; c.drawLine(14f, 86f, 86f, 14f, stroke) }
    }

    /** 뒷좌석 아이: 아기 얼굴과 앞머리 한 가닥 */
    private fun baby(c: Canvas) {
        c.drawCircle(50f, 54f, 32f, stroke)
        val curl = Path().apply { moveTo(50f, 22f); quadTo(62f, 10f, 56f, 6f) }
        c.drawPath(curl, stroke)
        c.drawCircle(39f, 50f, 5f, fill); c.drawCircle(61f, 50f, 5f, fill)
        val smile = Path().apply { moveTo(38f, 66f); quadTo(50f, 76f, 62f, 66f) }
        c.drawPath(smile, stroke)
    }
}
