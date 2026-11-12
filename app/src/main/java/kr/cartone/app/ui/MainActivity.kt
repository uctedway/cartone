package kr.cartone.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import kr.cartone.app.CarToneApp
import kr.cartone.app.core.CarEvent
import kr.cartone.app.core.Gear
import kr.cartone.app.service.SoundService
import kr.cartone.app.theme.Theme

/**
 * 앱 화면의 동작 담당: 테마 고르기, 이벤트별 듣기·녹음·켜고 끄기, 전체 켜고 끄기.
 * - 뷰 배치는 MainLayout이 하고, 여기서는 눌렀을 때 할 일과 상태 반영만 한다.
 * - 주행 중에는 Pleos가 이 화면 자체를 막는다. 이 화면은 정차(P) 중에 설정하는 곳이다.
 * - 그래도 기어를 직접 보고 P가 아니면 녹음과 테마 삭제를 잠근다(이중 안전장치).
 */
class MainActivity : Activity() {
    private val app get() = application as CarToneApp
    private lateinit var ui: MainLayout

    /** 마이크 권한을 묻는 동안 기억해 두는 "녹음하려던 이벤트" */
    private var pendingRecord: CarEvent? = null

    /** 지금 소리가 나고 있는 이벤트(듣기 버튼이 정지 모양인 칸) */
    private var playingEvent: CarEvent? = null
    private val endPlaying = Runnable { finishPlaying() }

    // ---- 화면이 보이는 동안만 앱 부품에 연결하는 리스너들 ----

    /** 기어가 바뀌면 잠금 표시를 고치고, 녹음이 취소됐으면 녹음 창을 닫는다 */
    private val onGate: (Boolean, Boolean) -> Unit = { _, cancelled ->
        refreshLock()
        if (cancelled) { ui.recLayer.hide(); toast("출발해서 녹음을 취소했어요", long = true) }
    }

    /** 실제 차량 이벤트가 와서 소리가 나면 [듣기]를 눌렀을 때와 같은 재생 표시를 보여준다 */
    private val onEvent: (CarEvent) -> Unit = { e ->
        ui.root.post {
            val audible = app.prefs.enabled && app.prefs.isOn(e.key) && app.sound.hasSound(e) && !app.recorder.isRecording
            // 깜빡이는 꺼질 때까지, 나머지는 이벤트별 시간만큼 보여준다
            if (audible) showPlaying(e, if (e.sustained) null else app.holdFor(e))
        }
    }

    /** 이어지던 이벤트(깜빡이)가 끝나면 재생 표시를 내린다 */
    private val onEventEnd: (CarEvent) -> Unit = { e -> if (playingEvent == e) finishPlaying() }

    /** 테마가 바뀌면(자동 전환 포함) 화면 전체를 다시 칠한다 */
    private val onTheme: () -> Unit = { refreshAll() }

    /** 녹음이 끝나면 녹음 창을 닫고 결과를 알린다 */
    private val onRecorded: (CarEvent, Boolean) -> Unit = { e, ok ->
        ui.recLayer.hide()
        if (ok) toast("${e.label}에 내 목소리를 걸었어요", long = true)
        refreshAll()
    }

    // ---- 생명주기 ----

    /** 백그라운드 서비스를 켜고 화면을 만든다 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundService.start(this)
        ui = MainLayout(this, app.prefs, MainLayout.Actions(
            onMaster = { on -> app.prefs.enabled = on; refreshAll() },
            onAskOverlay = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
            onListen = ::listen,
            onRecord = ::record,
            onToggle = ::toggle,
            onSaveRecording = { app.recorder.stop() },
            onCancelRecording = { app.recorder.cancel() },
        ))
        setContentView(ui.content)
    }

    /** 화면이 보이면 리스너를 연결하고 최신 상태로 다시 그린다 */
    override fun onResume() {
        super.onResume()
        app.gate.listeners += onGate
        app.eventListeners += onEvent
        app.eventEndListeners += onEventEnd
        app.recordListeners += onRecorded
        app.themeListeners += onTheme
        app.recorder.onProgress = { p, _ -> if (app.recorder.isRecording) ui.recLayer.progress(p) }
        refreshAll()
    }

    /** 화면을 벗어나면 녹음은 버리고 리스너를 뗀다 */
    override fun onPause() {
        if (app.recorder.isRecording) app.recorder.cancel()
        finishPlaying()
        app.gate.listeners -= onGate
        app.eventListeners -= onEvent
        app.eventEndListeners -= onEventEnd
        app.recordListeners -= onRecorded
        app.themeListeners -= onTheme
        super.onPause()
    }

    // ---- 상태를 화면에 반영 ----

    /** 테마 색·녹음 상태·스위치·잠금을 화면 전체에 다시 반영한다 */
    private fun refreshAll() {
        val t = app.theme
        window.decorView.setBackgroundColor(t.colors.bg)
        ui.tint(t.colors)
        rebuildCards(t.id)
        ui.tiles.forEach { it.bind(t, app.prefs.isOn(it.event.key), app.prefs.enabled) }
        ui.overlayHint.visibility = if (app.overlay.canShow()) View.GONE else View.VISIBLE
        ui.overlayHint.background = Ui.rounded(t.colors.card, Ui.dp(this, 18), t.colors.accent, Ui.dp(this, 2))
        ui.overlayHint.setTextColor(t.colors.ink)
        refreshLock()
    }

    /** 테마 카드를 다시 그린다(내 테마가 생기거나 녹음 개수가 달라지므로 매번) */
    private fun rebuildCards(selectedId: String) {
        ui.cardRow.removeAllViews()
        app.allThemes.forEach { t ->
            val card = ThemeCardView(this, t, { picked -> app.selectTheme(picked.id) }, if (t.isCustom) ::confirmDelete else null)
            card.isSelected = t.id == selectedId
            val lp = LinearLayout.LayoutParams(Ui.dp(this, CARD_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
            ui.cardRow.addView(card, lp.apply { marginEnd = Ui.dp(this@MainActivity, 16) })
        }
    }

    /** 기어 상태 알약과 녹음 버튼 잠금을 기어에 맞춘다 */
    private fun refreshLock() {
        val open = app.gate.isOpen
        val c = app.theme.colors
        ui.status.text = when {
            open -> "P · 편집 가능"
            app.gate.gear == Gear.UNKNOWN -> "기어 확인 중 · 녹음 잠김"
            else -> "주행 중 · 녹음 잠김"
        }
        ui.status.setTextColor(if (open) c.accent else c.ink)
        ui.status.background = Ui.rounded(if (open) Ui.alpha(c.accent, 0.10f) else Ui.alpha(c.ink, 0.08f), Ui.dp(this, 12))
        ui.tiles.forEach { it.setLocked(!open) }
    }

    // ---- 사용자가 누른 동작 ----

    /** 내 테마 삭제 확인(녹음도 함께 지워짐). P단에서만 된다 */
    private fun confirmDelete(t: Theme) {
        if (!app.gate.isOpen) return
        ConfirmDialog.show(this, app.theme.colors, "'${t.name}' 삭제", "이 테마와 테마 안의 녹음이 지워지고 되돌릴 수 없어요.", "삭제") {
            app.deleteTheme(t.id)
        }
    }

    /** 듣기: 재생 중인 칸을 다시 누르면 멈춘다 */
    private fun listen(e: CarEvent) {
        if (playingEvent == e) { app.sound.stop(); finishPlaying(); return }
        if (!app.sound.play(e, force = true)) { toast("이 테마에는 소리가 없어요. 녹음해 보세요"); return }
        showPlaying(e, app.sound.lastDurationMs.coerceIn(MIN_PLAYING_MS, MAX_PLAYING_MS))
    }

    /**
     * 소리가 나는 동안: 그 칸의 듣기 버튼을 정지 모양으로, 화면 가운데에 아이콘을 크게(빠르게 페이드 인).
     * holdMs 뒤에 내린다. null이면 끝났다는 알림이 올 때까지 둔다
     */
    private fun showPlaying(e: CarEvent, holdMs: Long?) {
        ui.root.removeCallbacks(endPlaying)
        playingEvent?.let { tileOf(it)?.showPlaying(false) }
        playingEvent = e
        val t = app.theme
        tileOf(e)?.apply { showPlaying(true); ping(t) }
        ui.stage.show(e.glyph, t.colors)
        if (holdMs != null) ui.root.postDelayed(endPlaying, holdMs)
    }

    /** 소리가 끝나면 버튼을 되돌리고 아이콘을 빠르게 페이드 아웃 */
    private fun finishPlaying() {
        if (!::ui.isInitialized) return
        ui.root.removeCallbacks(endPlaying)
        playingEvent?.let { tileOf(it)?.showPlaying(false) }
        playingEvent = null
        ui.stage.hide()
    }

    /** 이벤트 켜고 끄기: 가운데에 아이콘과 함께 켬(스피커)·끔(금지) 표시를 잠깐 보여준다 */
    private fun toggle(e: CarEvent, on: Boolean) {
        app.prefs.setOn(e.key, on)
        stopPlaying()
        val t = app.theme
        tileOf(e)?.bind(t, on, app.prefs.enabled)
        refreshLock()
        ui.stage.show(e.glyph, t.colors, if (on) CenterStage.Badge.SPEAKER else CenterStage.Badge.BAN, holdMs = BADGE_MS)
    }

    /** 녹음 시작. P단이 아니면 막고, 마이크 권한이 없으면 먼저 요청한다. 멈추기·취소는 가운데 녹음 창에서 한다 */
    private fun record(e: CarEvent) {
        if (app.recorder.isRecording) return
        if (!app.gate.isOpen) { toast("녹음은 기어가 P일 때만 할 수 있어요"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecord = e
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        stopPlaying()
        // 기본 테마에서 누르면 이 순간 복사본 테마가 생기고 그쪽으로 바뀐다
        if (app.beginRecord(e)) toast("기본 테마는 그대로 두고 '${app.theme.name}'을 만들었어요", long = true)
        ui.recLayer.show(e, app.theme)
    }

    /** 마이크 권한을 허용받으면 하려던 녹음을 이어서 시작한다 */
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_MIC && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) pendingRecord?.let { record(it) }
        pendingRecord = null
    }

    // ---- 작은 도우미 ----

    /** 나고 있는 소리가 있으면 멈추고 재생 표시도 지운다 */
    private fun stopPlaying() {
        if (playingEvent != null) { app.sound.stop(); finishPlaying() }
    }

    private fun tileOf(e: CarEvent) = ui.tiles.firstOrNull { it.event == e }

    private fun toast(text: String, long: Boolean = false) =
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_MIC = 7

        /** 테마 카드 한 장의 폭(dp) */
        private const val CARD_DP = 380

        /** [듣기]를 눌렀을 때 재생 표시를 유지하는 시간 범위(ms). 소리가 짧아도 아이콘을 알아볼 만큼은 보여준다 */
        private const val MIN_PLAYING_MS = 1600L
        private const val MAX_PLAYING_MS = 3600L

        /** 켬·끔 표시를 보여주는 시간(ms) */
        private const val BADGE_MS = 900L
    }
}
