package kr.carsound.app

import android.app.Application
import android.util.Log
import kr.carsound.app.core.CarEvent
import kr.carsound.app.core.Gear
import kr.carsound.app.overlay.EventOverlay
import kr.carsound.app.record.ParkGate
import kr.carsound.app.record.VoiceRecorder
import kr.carsound.app.record.VoiceStore
import kr.carsound.app.settings.Prefs
import kr.carsound.app.sound.SoundEngine
import kr.carsound.app.theme.CustomThemes
import kr.carsound.app.theme.Theme
import kr.carsound.app.theme.ThemeRepository
import kr.carsound.app.vehicle.VehicleWatcher

/**
 * 앱 전체에서 하나만 있는 부품들을 만들고 서로 연결한다.
 * 화면(MainActivity)과 백그라운드 서비스(SoundService)가 같은 부품을 함께 쓴다.
 */
class CarSoundApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var themes: ThemeRepository; private set
    lateinit var voices: VoiceStore; private set
    lateinit var custom: CustomThemes; private set
    lateinit var sound: SoundEngine; private set
    lateinit var overlay: EventOverlay; private set
    lateinit var recorder: VoiceRecorder; private set
    lateinit var gate: ParkGate; private set
    private var watcher: VehicleWatcher? = null

    /** 이번 녹음을 시작하면서 새로 만든 복사본 테마 id. 녹음을 버리면 빈 복사본도 함께 지운다 */
    private var freshCopy: String? = null

    /** 지금 쓰는 테마(내장 또는 커스텀) */
    val theme: Theme get() = custom.get(prefs.themeId)?.let { custom.build(it, themes) } ?: themes.byId(prefs.themeId)

    /** 테마 카드에 보여줄 목록: 내장 테마들 + 커스텀 테마들 */
    val allThemes: List<Theme> get() = themes.themes + custom.all.map { custom.build(it, themes) }

    /** 화면이 최근 이벤트를 보여줄 수 있게 알려준다 */
    val eventListeners = mutableListOf<(CarEvent) -> Unit>()

    /** 테마가 바뀌었을 때(자동 전환 포함) 화면에 알려준다 */
    val themeListeners = mutableListOf<() -> Unit>()

    /** 녹음이 끝났을 때 화면에 알려준다(이벤트, 저장됐는지) */
    val recordListeners = mutableListOf<(CarEvent, Boolean) -> Unit>()

    /** 부품을 만들고, 녹음이 끝났을 때 할 일을 연결한다 */
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        themes = ThemeRepository(this)
        voices = VoiceStore(this)
        custom = CustomThemes(this, voices)
        sound = SoundEngine(this).apply { volume = prefs.volume; loadTheme(theme) }
        overlay = EventOverlay(this)
        recorder = VoiceRecorder(voices)
        gate = ParkGate(recorder)
        // 녹음이 끝나면 고칠 테마(내장 테마면 복사본)에 목소리를 넣고 그 이벤트에 건다
        recorder.onFinished = { e, ok ->
            val id = custom.get(prefs.themeId)?.id
            if (ok && id != null && voices.moveDone(e, id)) {
                custom.setPick(id, e, "voice:$id/${e.key}")
                selectTheme(id, force = true) // 같은 경로에 다시 녹음했을 수 있어 새로 읽는다
            } else {
                // 녹음을 버렸으면 이번에 만든 빈 복사본은 남기지 않는다
                freshCopy?.let { if (custom.get(it)?.picks.isNullOrEmpty()) deleteTheme(it) }
            }
            freshCopy = null
            recordListeners.forEach { it(e, ok) }
        }
    }

    /** 서비스가 시작될 때 차량 이벤트 감시를 켠다(한 번만) */
    fun startWatching() {
        if (watcher != null) return
        watcher = VehicleWatcher(this).apply {
            onGear = { g: Gear -> gate.onGear(g) }
            onEvent = { e -> handle(e) }
            start()
        }
    }

    /** 서비스가 내려갈 때 차량 이벤트 감시를 끈다 */
    fun stopWatching() { watcher?.stop(); watcher = null }

    /** 테마를 바꾸고 그 테마의 소리를 불러온다. force = 구성이 같아 보여도 소리를 새로 읽기 */
    fun selectTheme(id: String, force: Boolean = false) {
        prefs.themeId = id
        sound.loadTheme(theme, force)
        themeListeners.forEach { it() }
    }

    /**
     * 고칠 대상 테마. 지금 커스텀 테마면 그대로, 내장 테마면 복사본을 새로 만들어 그쪽으로 전환한다.
     * (내장 테마는 읽기 전용) 반환: 테마 id, 새로 만들었는지
     */
    private fun editTarget(): Pair<String, Boolean> {
        custom.get(prefs.themeId)?.let { return it.id to false }
        val copy = custom.copyOf(theme)
        prefs.themeId = copy.id
        return copy.id to true
    }

    /**
     * 녹음 시작. 기본 테마는 읽기 전용이라, 기본 테마에서 누르면 먼저 복사본 테마를 만들어 그쪽으로 바꾼 뒤 녹음한다.
     * 복사본을 새로 만들었으면 true
     */
    fun beginRecord(e: CarEvent): Boolean {
        val (id, created) = editTarget()
        freshCopy = if (created) id else null
        if (created) selectTheme(id)
        recorder.start(e)
        return created
    }

    /** 커스텀 테마 삭제(녹음 포함). 쓰던 테마였으면 바탕 테마로 돌아간다 */
    fun deleteTheme(id: String) {
        val base = custom.get(id)?.base ?: return
        custom.delete(id)
        if (prefs.themeId == id) selectTheme(base) else themeListeners.forEach { it() }
    }

    /** 차량 이벤트 처리: 테마 자동 전환 → 소리 → 알림 띠 */
    private fun handle(e: CarEvent) {
        Log.i("CarSound", "이벤트 ${e.key}")
        eventListeners.forEach { it(e) }
        if (!prefs.enabled) return
        val off = !prefs.isOn(e.key)
        // 내 테마가 래틀박스 기반이면 그대로 둔다(가족 목소리를 지키기 위해)
        if (e == CarEvent.REAR_SEAT && prefs.autoRattle && (theme.baseId ?: theme.id) != "rattle") selectTheme("rattle")
        if (off) return // 사용자가 끈 이벤트는 소리도 알림도 내지 않는다
        if (recorder.isRecording) return // 녹음 중에는 소리를 내지 않는다
        val played = sound.play(e)
        Log.d("CarSound", "재생 ${e.key} → $played")
        if (played) overlay.show(e, theme, theme.isVoice(e))
    }
}
