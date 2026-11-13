package kr.cartone.app.settings

import android.content.Context

/** 사용자 설정: 고른 테마, 켜고 끄기, 뒷좌석 탑승 시 래틀박스 자동 전환 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cartone", Context.MODE_PRIVATE)

    /** 고른 테마 id. 처음에는 null(첫 번째 기본 테마를 쓴다) */
    var themeId: String?
        get() = sp.getString("theme", null)
        set(v) = sp.edit().putString("theme", v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** 뒷좌석에 사람이 타면 래틀박스로 바꿀지. 어른 동승자 오작동을 막으려고 기본은 꺼짐 */
    var autoRattle: Boolean
        get() = sp.getBoolean("autoRattle", false)
        set(v) = sp.edit().putBoolean("autoRattle", v).apply()

    /** 이벤트별 켜고 끄기. 기본은 모두 켜짐이고, 끈 이벤트 키만 저장한다 */
    fun isOn(key: String) = key !in sp.getStringSet("off", emptySet()).orEmpty()

    fun setOn(key: String, on: Boolean) {
        val off = sp.getStringSet("off", emptySet()).orEmpty().toMutableSet()
        if (on) off -= key else off += key
        sp.edit().putStringSet("off", off).apply()
    }

    /** 앱 안 소리 크기(0~1). 아직 조절 화면은 없고 기본값 1을 쓴다 */
    var volume: Float
        get() = sp.getFloat("volume", 1f)
        set(v) = sp.edit().putFloat("volume", v.coerceIn(0f, 1f)).apply()
}
