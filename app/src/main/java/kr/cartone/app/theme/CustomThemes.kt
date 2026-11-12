package kr.cartone.app.theme

import android.content.Context
import java.io.File
import kr.cartone.app.core.CarEvent
import kr.cartone.app.record.VoiceStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 사용자가 만든 테마(커스텀 테마) 보관함. 개수 제한 없음.
 * - 내장 테마(래틀박스·드럼킷)는 읽기 전용이다. 수정하려고 하면 그 테마의 "복사본"을 여기에 새로 만들고 복사본을 고친다.
 * - 커스텀 테마는 언제든 고칠 수 있다.
 * - picks: 이벤트별로 녹음한 목소리. "voice:<테마id>/<이벤트키>"
 * 저장 위치: files/custom_themes.json (차 안에만 저장)
 */
class CustomThemes(context: Context, private val voices: VoiceStore) {
    /** 커스텀 테마 하나의 저장 내용 */
    data class Entry(val id: String, val name: String, val base: String, val picks: LinkedHashMap<CarEvent, String>)

    private val file = File(context.filesDir, "custom_themes.json")
    private val entries = mutableListOf<Entry>()

    init { load() }

    val all: List<Entry> get() = entries

    /** id로 내 테마를 찾는다. 기본 테마 id거나 없으면 null */
    fun get(id: String?) = entries.firstOrNull { it.id == id }

    /** 내장 테마의 복사본을 만든다. 이름은 "<테마> 복사본", 겹치면 번호를 붙인다 */
    fun copyOf(base: Theme): Entry {
        val stem = "${base.name} 복사본"
        val name = generateSequence(1) { it + 1 }.map { if (it == 1) stem else "$stem $it" }.first { n -> entries.none { it.name == n } }
        val e = Entry("c" + System.currentTimeMillis(), name, base.id, LinkedHashMap())
        entries += e; save(); return e
    }

    /** 이벤트에 걸 소리(녹음)를 기록한다 */
    fun setPick(id: String, e: CarEvent, source: String) { get(id)?.picks?.put(e, source); save() }

    /** 내 테마와 그 안의 녹음을 지운다 */
    fun delete(id: String) { entries.removeAll { it.id == id }; voices.deleteTheme(id); save() }

    /** 저장된 내용을 실제 테마(녹음 파일 경로 포함)로 만든다 */
    fun build(entry: Entry, repo: ThemeRepository): Theme {
        val b = repo.byId(entry.base)
        val sounds = HashMap(b.sounds)
        for ((e, src) in entry.picks) {
            if (!src.startsWith("voice:")) continue
            val (tid, key) = src.removePrefix("voice:").split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val owner = CarEvent.fromKey(key) ?: continue
            if (voices.has(tid, owner)) sounds[e] = listOf(Theme.FILE + voices.fileFor(tid, owner).absolutePath)
        }
        val n = sounds.count { (e, _) -> entry.picks.containsKey(e) && sounds[e]?.firstOrNull()?.startsWith(Theme.FILE) == true }
        return Theme(
            id = entry.id, name = entry.name, subtitle = "${b.name} 기반 · 내 녹음 ${n}개",
            colors = b.colors, motion = b.motion, sounds = sounds, baseId = b.id,
        )
    }

    /** 저장 파일을 읽는다. 파일이 깨졌으면 빈 목록으로 시작한다 */
    private fun load() {
        runCatching {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val picks = LinkedHashMap<CarEvent, String>()
                j.optJSONObject("picks")?.let { p -> for (k in p.keys()) CarEvent.fromKey(k)?.let { picks[it] = p.getString(k) } }
                entries += Entry(j.getString("id"), j.getString("name"), j.getString("base"), picks)
            }
        }
    }

    /** 목록 전체를 JSON으로 다시 쓴다 */
    private fun save() {
        val arr = JSONArray()
        for (e in entries) {
            val p = JSONObject(); e.picks.forEach { (ev, s) -> p.put(ev.key, s) }
            arr.put(JSONObject().put("id", e.id).put("name", e.name).put("base", e.base).put("picks", p))
        }
        file.writeText(arr.toString())
    }
}
