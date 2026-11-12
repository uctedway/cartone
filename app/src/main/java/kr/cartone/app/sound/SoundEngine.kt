package kr.cartone.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kr.cartone.app.core.CarEvent
import kr.cartone.app.theme.Theme

/**
 * 지금 테마의 소리(기본 테마 소리 + 내 테마의 녹음 목소리)를 미리 불러 두고, 이벤트가 오면 바로 재생한다.
 * - 변형이 여러 개면 직전에 낸 것과 다른 걸 고른다(같은 소리 반복 피로 줄이기).
 * - 깜빡이는 방향 쪽 스피커를 더 크게 낸다.
 * - 같은 이벤트가 짧은 시간에 몰려 오면 한 번만 낸다.
 * - 알림음 용도(SONIFICATION)로 재생해 내비 안내·통화와 시스템 오디오 정책을 따른다.
 */
class SoundEngine(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    ).build()

    private val themeIds = HashMap<CarEvent, List<Int>>()
    private val lastPick = HashMap<CarEvent, Int>()
    private val lastAt = HashMap<CarEvent, Long>()
    /** 마지막으로 불러온 테마의 소리 구성. 내 테마는 같은 id라도 구성이 바뀌므로 내용으로 비교한다 */
    private var loadedKey: String? = null

    /** 소리 id → 원본 경로. 재생 기록(로그)에 어떤 파일을 냈는지 남기는 용도 */
    private val names = HashMap<Int, String>()

    /** 소리 id → 길이(ms). 화면이 "재생 중" 표시를 소리 길이만큼만 유지하는 데 쓴다 */
    private val lengths = HashMap<Int, Long>()

    /** 방금 재생한 소리의 길이(ms)와 스트림. 화면의 재생 중 표시·정지에 쓴다 */
    var lastDurationMs = DEFAULT_MS
        private set
    private var lastStream = 0

    /** 앱 안 소리 크기(0~1). 좌우 음량에 곱한다 */
    var volume = 0.9f

    /** 다 불러온 소리 id. 테마를 바꾼 직후에는 아직 로딩 중인 소리가 있다 */
    private val ready = HashSet<Int>()

    /** 로딩이 끝나면 낼 소리(id → 좌우 음량). 테마 전환과 동시에 온 이벤트를 놓치지 않기 위함 */
    private val pending = HashMap<Int, Pair<Float, Float>>()

    /** 소리 파일 읽기는 메인 스레드를 막지 않도록 별도 스레드에서 한다(느린 차량 기기에서 화면 멈춤 방지) */
    private val worker = Handler(HandlerThread("sound-load").apply { start() }.looper)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    /** 테마를 불러오는 중에 들어온 재생 요청. 다 불러오면 새 테마 소리로 낸다 */
    @Volatile private var loading = false
    private val queued = mutableListOf<Pair<CarEvent, Boolean>>()

    init {
        pool.setOnLoadCompleteListener { p, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val waiting = synchronized(lock) { ready += id; pending.remove(id) }
            waiting?.let { (l, r) -> p.play(id, l, r, 1, 0, 1f) }
        }
    }

    /** 테마(또는 내 테마의 구성)가 바뀌면 이전 소리를 내리고 새 소리를 올린다 */
    fun loadTheme(theme: Theme, force: Boolean = false) {
        val key = theme.id + theme.sounds.hashCode()
        // 같은 파일에 다시 녹음하면 경로가 같으므로, 그때는 force로 새로 읽는다
        if (!force && loadedKey == key) return
        loadedKey = key
        loading = true
        worker.post {
            // 새 소리를 먼저 다 읽은 뒤 한 번에 바꿔 끼운다
            val fresh = HashMap<CarEvent, List<Int>>()
            for ((e, paths) in theme.sounds) {
                fresh[e] = paths.mapNotNull { p ->
                    val id = runCatching { load(p) }.getOrNull() ?: return@mapNotNull null
                    val ms = lengthOf(p)
                    synchronized(lock) { names[id] = p.substringAfterLast('/'); lengths[id] = ms }
                    id
                }
            }
            val old = synchronized(lock) { val o = themeIds.values.flatten(); themeIds.clear(); themeIds.putAll(fresh); o }
            old.forEach { pool.unload(it); synchronized(lock) { ready -= it; pending -= it } }
            Log.i(TAG, "테마 소리 로드: ${theme.id} ${fresh.values.sumOf { it.size }}개")
            main.post {
                loading = false
                val q = synchronized(lock) { queued.toList().also { queued.clear() } }
                q.forEach { (e, f) -> play(e, f) }
            }
        }
    }

    /** 앱에 들어 있는 소리(assets) 또는 차 안에 저장된 녹음 파일(file:)을 불러온다 */
    private fun load(path: String): Int =
        if (path.startsWith(Theme.FILE)) pool.load(path.removePrefix(Theme.FILE), 1)
        else context.assets.openFd(path).use { pool.load(it, 1) }

    /** 소리 파일 길이(ms). 못 읽으면 기본값 */
    private fun lengthOf(path: String): Long = runCatching {
        val r = MediaMetadataRetriever()
        try {
            if (path.startsWith(Theme.FILE)) r.setDataSource(path.removePrefix(Theme.FILE))
            else context.assets.openFd(path).use { r.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: DEFAULT_MS
        } finally { r.release() }
    }.getOrDefault(DEFAULT_MS)

    /** 방금 낸 소리를 멈춘다(듣기 버튼을 다시 눌렀을 때) */
    fun stop() { if (lastStream != 0) pool.stop(lastStream); lastStream = 0 }

    /** 이 이벤트에 낼 소리가 있는지 */
    fun hasSound(e: CarEvent) = synchronized(lock) { !themeIds[e].isNullOrEmpty() }

    /** 이벤트 소리 재생. 재생했으면 true */
    fun play(e: CarEvent, force: Boolean = false): Boolean {
        // 테마를 불러오는 중이면 다 불러온 뒤 새 테마 소리로 낸다
        if (loading) { synchronized(lock) { queued += e to force }; lastDurationMs = DEFAULT_MS; return true }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - (lastAt[e] ?: 0L) < MIN_GAP_MS) return false
        lastAt[e] = now
        val id = pickVariant(e) ?: return false
        lastDurationMs = synchronized(lock) { lengths[id] } ?: DEFAULT_MS
        val (l, r) = when (e) {
            CarEvent.TURN_LEFT -> 1f to 0.35f
            CarEvent.TURN_RIGHT -> 0.35f to 1f
            else -> 1f to 1f
        }
        // 아직 로딩 중이면 끝나는 대로 재생하도록 예약한다
        if (synchronized(lock) { id !in ready }) {
            synchronized(lock) { pending[id] = (l * volume) to (r * volume) }
            Log.i(TAG, "PLAY ${e.key} ${names[id]} l=$l r=$r (로딩 후 재생)")
            return true
        }
        lastStream = pool.play(id, l * volume, r * volume, 1, 0, 1f)
        val ok = lastStream != 0
        // 시연 영상 소리 맞추기와 문제 추적용: 어떤 파일을 냈는지 남긴다
        if (ok) Log.i(TAG, "PLAY ${e.key} ${names[id]} l=$l r=$r")
        return ok
    }

    /** 변형 소리 중 하나를 고른다. 직전에 낸 것과 같으면 다음 것으로 바꾼다 */
    private fun pickVariant(e: CarEvent): Int? {
        val ids = synchronized(lock) { themeIds[e].orEmpty() }
        if (ids.isEmpty()) return null
        var i = ids.indices.random()
        if (ids.size > 1 && i == lastPick[e]) i = (i + 1) % ids.size
        lastPick[e] = i
        return ids[i]
    }

    /** 앱이 끝날 때 소리 자원을 내려놓는다 */
    fun release() = pool.release()

    companion object {
        private const val TAG = "CarTone"
        private const val MIN_GAP_MS = 400L
        private const val DEFAULT_MS = 1500L
    }
}
