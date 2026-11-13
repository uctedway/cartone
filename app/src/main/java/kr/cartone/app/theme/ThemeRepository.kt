package kr.cartone.app.theme

import android.content.Context
import android.graphics.Color
import android.util.Log
import kr.cartone.app.core.CarEvent
import org.json.JSONObject

/**
 * assets/themes/<테마>/ 폴더에서 기본 테마를 읽는다.
 * - theme.json: 이름·색·움직임
 * - <이벤트키>_<번호>.wav: 이벤트별 소리(번호가 여러 개면 변형)
 */
class ThemeRepository(private val context: Context) {

    /** 기본 테마 목록(처음 쓸 때 한 번 읽는다) */
    val themes: List<Theme> by lazy { loadAll() }

    /** id로 기본 테마를 찾는다. 없으면 첫 번째 테마 */
    fun byId(id: String?): Theme = themes.firstOrNull { it.id == id } ?: themes.first()

    /** 테마 폴더를 모두 읽는다. 읽지 못한 테마는 건너뛴다 */
    private fun loadAll(): List<Theme> {
        val am = context.assets
        val ids = am.list(ROOT)?.sorted().orEmpty()
        // 정해진 순서(래틀박스, 드럼킷)를 먼저, 나머지는 이름순
        val ordered = ORDER.filter { it in ids } + ids.filter { it !in ORDER }
        return ordered.mapNotNull { id -> runCatching { load(id) }.onFailure { Log.w("CarTone", "테마 $id 읽기 실패 $it") }.getOrNull() }
    }

    /** 테마 폴더 하나를 읽어 테마로 만든다 */
    private fun load(id: String): Theme {
        val am = context.assets
        val json = JSONObject(am.open("$ROOT/$id/theme.json").bufferedReader().use { it.readText() })
        val c = json.getJSONObject("colors")
        val files = am.list("$ROOT/$id")?.filter { it.endsWith(".wav") }.orEmpty().sorted()
        // 파일 이름 앞부분(마지막 _번호 앞)이 이벤트 키
        val sounds = files.groupBy { it.substringBeforeLast('_') }
            .mapNotNull { (key, list) -> CarEvent.fromKey(key)?.let { it to list.map { f -> "$ROOT/$id/$f" } } }
            .toMap()
        return Theme(
            id = id,
            name = json.getString("name"),
            subtitle = json.optString("subtitle"),
            colors = ThemeColors(
                Color.parseColor(c.getString("bg")), Color.parseColor(c.getString("card")),
                Color.parseColor(c.getString("accent")), Color.parseColor(c.getString("ink")),
            ),
            motion = if (json.optString("motion") == "pulse") Motion.PULSE else Motion.BOUNCE,
            sounds = sounds,
        )
    }

    companion object {
        private const val ROOT = "themes"
        private val ORDER = listOf("rattle", "drum")
    }
}
