package kr.cartone.app.theme

import android.graphics.Color
import kr.cartone.app.core.CarEvent

/** 알림 띠 그림이 움직이는 방식. 테마마다 다르게 해서 같은 아이콘도 느낌이 달라진다 */
enum class Motion { BOUNCE, PULSE }

/** 테마 색. 앱 화면과 알림 띠가 같이 쓴다 */
data class ThemeColors(val bg: Int, val card: Int, val accent: Int, val ink: Int)

/**
 * 소리 테마 하나.
 * - sounds: 이벤트마다 소리 경로 목록(여러 개면 번갈아 재생). assets 경로 또는 "file:" 로 시작하는 녹음 파일 경로
 * - baseId: 내 테마(커스텀)일 때 바탕이 된 기본 테마 id. 기본 테마는 null
 */
data class Theme(
    val id: String,
    val name: String,
    val subtitle: String,
    val colors: ThemeColors,
    val motion: Motion,
    val sounds: Map<CarEvent, List<String>>,
    val baseId: String? = null,
) {
    val isCustom get() = baseId != null

    /** 이 이벤트 소리가 녹음한 목소리인지 */
    fun isVoice(e: CarEvent) = sounds[e]?.firstOrNull()?.startsWith(FILE) == true

    companion object {
        const val FILE = "file:"

        /** 테마를 못 읽었을 때 쓰는 기본 색 */
        val FALLBACK_COLORS = ThemeColors(Color.parseColor("#1B1F27"), Color.parseColor("#262C36"), Color.parseColor("#4FC3F7"), Color.WHITE)
    }
}
