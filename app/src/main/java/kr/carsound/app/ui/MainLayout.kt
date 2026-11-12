package kr.carsound.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kr.carsound.app.core.CarEvent
import kr.carsound.app.settings.Prefs
import kr.carsound.app.theme.ThemeColors

/**
 * 앱 화면의 뷰를 만들어 들고 있는 곳(배치만 담당).
 * 위에서부터: 머리말(앱 이름·기어 상태·전체 스위치) → 권한 안내 → 테마 카드 줄 → 뒷좌석 자동 전환 → 이벤트 14칸.
 * 그 위에 가운데 아이콘 판(CenterStage)과 녹음 창(RecordLayer)을 겹쳐 둔다.
 * 눌렀을 때 무슨 일을 할지는 MainActivity가 넘겨 준 콜백이 정한다.
 */
class MainLayout(
    private val context: Context,
    private val prefs: Prefs,
    private val actions: Actions,
) {
    /** 화면에서 일어나는 조작. MainActivity가 채워서 넘긴다 */
    class Actions(
        val onMaster: (Boolean) -> Unit,
        val onAskOverlay: () -> Unit,
        val onListen: (CarEvent) -> Unit,
        val onRecord: (CarEvent) -> Unit,
        val onToggle: (CarEvent, Boolean) -> Unit,
        val onSaveRecording: () -> Unit,
        val onCancelRecording: () -> Unit,
    )

    /** 스크롤되는 본문 */
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(32)
        setPadding(pad, pad, pad, pad)
    }

    /** 기어 상태 알약("P · 편집 가능" 등) */
    lateinit var status: TextView; private set

    /** "다른 앱 위에 표시" 권한이 없을 때만 보이는 안내 줄 */
    lateinit var overlayHint: TextView; private set

    /** 테마 카드를 가로로 늘어놓는 줄. 카드는 MainActivity가 테마 목록에 맞춰 채운다 */
    val cardRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    /** 이벤트 칸 14개(CarEvent 순서) */
    val tiles = mutableListOf<EventTileView>()

    /** 화면 가운데에 이벤트 아이콘을 크게 띄우는 판 */
    val stage = CenterStage(context)

    /** 녹음 중에 뜨는 큰 녹음 창 */
    val recLayer = RecordLayer(context, actions.onSaveRecording, actions.onCancelRecording)

    /** Activity에 그대로 붙일 최상위 뷰 */
    val content: View

    init {
        buildHeader()
        buildOverlayHint()
        buildThemeSection()
        buildEventGrid()
        content = FrameLayout(context).apply {
            addView(ScrollView(context).apply { addView(root) })
            addView(stage, FrameLayout.LayoutParams(dp(STAGE_DP), dp(STAGE_DP), Gravity.CENTER))
            addView(recLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        }
    }

    /** 머리말: 앱 이름, 기어 상태, 전체 소리 스위치, 한 줄 설명 */
    private fun buildHeader() {
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.title(context, "차소리", 0, 32f).apply { tag = INK }, LinearLayout.LayoutParams(0, WRAP, 1f))
        status = Ui.body(context, "", 0, 18f).apply { setPadding(dp(18), dp(10), dp(18), dp(10)) }
        head.addView(status)
        // 전체 스위치: 끄면 이벤트별 스위치는 상태를 유지한 채 잠긴다
        val master = Switch(context).apply {
            isChecked = prefs.enabled; text = "  소리"; textSize = 18f; tag = INK
            setOnCheckedChangeListener { _, on -> actions.onMaster(on) }
        }
        head.addView(master, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(16) })
        root.addView(head)
        root.addView(Ui.body(context, "차량 동작에 맞춰 재생되는 나만의 소리", 0, 18f).apply { tag = SUB; setPadding(0, dp(6), 0, 0) })
    }

    /** 알림 띠 권한 안내. 누르면 시스템 설정 화면으로 간다 */
    private fun buildOverlayHint() {
        overlayHint = Ui.body(context, "화면 위 알림을 보려면 '다른 앱 위에 표시'를 허용해 주세요  ›", 0, 17f).apply {
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setOnClickListener { actions.onAskOverlay() }
        }
        root.addView(overlayHint, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
    }

    /** 테마 카드 줄과 뒷좌석 자동 전환 스위치 */
    private fun buildThemeSection() {
        root.addView(Ui.title(context, "테마", 0, 22f).apply { tag = INK; setPadding(0, dp(20), 0, dp(12)) })
        root.addView(HorizontalScrollView(context).apply { addView(cardRow); isHorizontalScrollBarEnabled = false })
        val auto = Switch(context).apply {
            isChecked = prefs.autoRattle; text = "뒷좌석에 사람이 타면 래틀박스로 바꾸기  "; textSize = 18f; tag = INK
            setOnCheckedChangeListener { _, on -> prefs.autoRattle = on }
        }
        root.addView(auto, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
    }

    /** 이벤트 칸을 한 줄에 두 개씩 놓는다 */
    private fun buildEventGrid() {
        root.addView(Ui.title(context, "이벤트별 소리", 0, 22f).apply { tag = INK; setPadding(0, dp(20), 0, 0) })
        val guide = "녹음은 P에서만 돼요. 기본 테마에 녹음하면 ✎ 복사본이 생겨요(길게 눌러 삭제)."
        root.addView(Ui.body(context, guide, 0, 16f).apply { tag = SUB; setPadding(0, dp(6), 0, dp(12)) })
        CarEvent.values().toList().chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { e ->
                val tile = EventTileView(context, e, actions.onListen, actions.onRecord, actions.onToggle)
                tiles += tile
                row.addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, dp(12), dp(12)) })
            }
            // 마지막 줄이 한 칸뿐이면 빈 자리를 채워 칸 폭을 맞춘다
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            root.addView(row)
        }
    }

    /** 테마 색을 본문 글자와 스위치에 입힌다(칸과 카드는 스스로 칠한다) */
    fun tint(colors: ThemeColors) {
        for (i in 0 until root.childCount) tint(root.getChildAt(i), colors)
    }

    /** tag로 표시해 둔 글자(INK = 본문색, SUB = 흐린 본문색)와 스위치를 찾아 색을 바꾼다 */
    private fun tint(v: View, c: ThemeColors) {
        when (v.tag) {
            INK -> (v as TextView).setTextColor(c.ink)
            SUB -> (v as TextView).setTextColor(Ui.alpha(c.ink, 0.65f))
        }
        if (v is Switch) {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            v.minHeight = dp(56)
            v.thumbTintList = ColorStateList(states, intArrayOf(c.accent, c.card))
            v.trackTintList = ColorStateList(states, intArrayOf(Ui.alpha(c.accent, 0.28f), Ui.alpha(c.ink, 0.20f)))
        }
        if (v is LinearLayout && v !is EventTileView && v !is ThemeCardView) {
            for (i in 0 until v.childCount) tint(v.getChildAt(i), c)
        }
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    companion object {
        private const val INK = "ink"
        private const val SUB = "sub"
        private const val STAGE_DP = 420
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
