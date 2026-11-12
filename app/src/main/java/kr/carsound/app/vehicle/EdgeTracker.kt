package kr.carsound.app.vehicle

import android.os.SystemClock

/**
 * 차량 값의 "바뀐 순간"만 골라내는 도우미.
 * - 첫 값은 기억만 하고 변화로 치지 않는다(앱을 켤 때마다 소리가 나지 않게).
 * - 창문처럼 조금씩 여러 번 바뀌는 값은 한 번 움직일 때 한 번만 알린다.
 */
class EdgeTracker {
    private val last = HashMap<String, Any?>()
    private val moving = HashMap<String, Pair<Boolean, Long>>()

    /** 이전 값과 다르면 true. 첫 값이면 기록만 하고 false */
    @Synchronized
    fun changed(key: String, value: Any?): Boolean {
        val first = !last.containsKey(key)
        val prev = last[key]
        last[key] = value
        return !first && prev != value
    }

    /**
     * 숫자가 커지는지(true) 작아지는지(false) 알려준다.
     * 같은 방향 움직임이 이어지는 동안(1.5초 안)은 다시 알리지 않는다.
     */
    @Synchronized
    fun direction(key: String, value: Int?, quietMs: Long = 1500): Boolean? {
        if (value == null) return null
        val first = !last.containsKey(key)
        val prev = last[key] as? Int
        last[key] = value
        if (first || prev == null || prev == value) return null
        val up = value > prev
        val now = SystemClock.elapsedRealtime()
        val m = moving[key]
        moving[key] = up to now
        return if (m != null && m.first == up && now - m.second < quietMs) null else up
    }
}
