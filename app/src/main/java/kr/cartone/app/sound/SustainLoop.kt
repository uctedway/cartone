package kr.cartone.app.sound

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kr.cartone.app.core.CarEvent

/**
 * 켜져 있는 동안 이어지는 이벤트(깜빡이)의 소리를 꺼질 때까지 되풀이한다.
 * - 깜빡이가 한 번 점멸할 때마다 한 번씩, 일정한 간격으로 소리를 낸다(순정 작동음과 박자가 어긋나지 않게).
 * - 꺼졌다는 알림이 오면 더는 내지 않고, 나던 소리가 끝날 즈음 멈췄다고 알린다.
 * - 차량 값이 잠깐 끊겼다 다시 켜지면 새로 시작하지 않고 그대로 잇는다.
 * - 끄는 걸 잊은 깜빡이가 끝없이 울리지 않도록 최대 시간이 지나면 스스로 멈춘다.
 * 모두 메인 스레드에서 부른다.
 *
 * @param playAgain 소리를 한 번 더 낸다. 낸 소리 길이(ms)를 돌려주고, 낼 수 없으면 null
 * @param onStopped 반복이 끝났을 때(표시를 내리는 데 쓴다)
 */
class SustainLoop(
    private val playAgain: (CarEvent) -> Long?,
    private val onStopped: (CarEvent) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var active: CarEvent? = null
    private var startedAt = 0L
    private var soundEndsAt = 0L

    /** 지금까지 낸 횟수. 다음 소리 시각을 시작 시각 기준으로 계산해 간격이 밀리지 않게 한다 */
    private var count = 0
    private var ending = false
    private val tick = Runnable { repeat() }
    private val stopNow = Runnable { finish() }

    /** 같은 이벤트를 이미 잇고 있으면 끝내려던 것을 취소하고 true(새로 소리를 낼 필요 없음) */
    fun resume(e: CarEvent): Boolean {
        if (active != e) return false
        if (ending) {
            ending = false
            main.removeCallbacks(stopNow)
            main.postDelayed(tick, untilNextBeat())
        }
        return true
    }

    /** 첫 소리를 방금 냈다. 그 길이(ms)를 받아 다음 소리를 예약한다 */
    fun start(e: CarEvent, firstMs: Long) {
        finish()
        active = e
        startedAt = now()
        count = 0
        schedule(firstMs)
    }

    /** 이벤트가 꺼졌다. 나던 소리가 끝날 즈음 멈춘다 */
    fun end(e: CarEvent) {
        if (active != e || ending) return
        ending = true
        main.removeCallbacks(tick)
        main.postDelayed(stopNow, (soundEndsAt - now()).coerceIn(GRACE_MS, MAX_TAIL_MS))
    }

    /** 바로 멈춘다 */
    fun cancel() = finish()

    private fun repeat() {
        val e = active ?: return
        if (now() - startedAt > MAX_MS) { finish(); return }
        val ms = playAgain(e)
        if (ms == null) finish() else schedule(ms)
    }

    private fun schedule(soundMs: Long) {
        soundEndsAt = now() + soundMs
        count++
        main.postDelayed(tick, untilNextBeat())
    }

    /** 다음 점멸 박자까지 남은 시간(ms). 시작 시각에서 간격의 배수 위치를 쓴다 */
    private fun untilNextBeat(): Long {
        val elapsed = now() - startedAt
        if (count * PERIOD_MS <= elapsed) count = (elapsed / PERIOD_MS).toInt() + 1
        return count * PERIOD_MS - elapsed
    }

    private fun finish() {
        main.removeCallbacks(tick)
        main.removeCallbacks(stopNow)
        ending = false
        val e = active ?: return
        active = null
        onStopped(e)
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        /**
         * 깜빡이 점멸 간격(ms). Pleos 에뮬레이터의 순정 작동음(똑-딱 한 쌍)이 1초마다 나는 것에 맞췄다.
         * SDK는 켜짐·꺼짐만 알려주고 점멸 박자는 주지 않아서, 켜진 순간부터 이 간격으로 센다(실제 차량에서는 보정 필요).
         */
        private const val PERIOD_MS = 1000L

        /** 꺼진 뒤 표시를 남겨 두는 시간 범위(ms). 값이 잠깐 끊긴 것인지 기다리는 시간이기도 하다 */
        private const val GRACE_MS = 600L
        private const val MAX_TAIL_MS = 2500L

        /** 한 번 켜진 깜빡이에 소리를 되풀이하는 최대 시간(ms) */
        private const val MAX_MS = 60_000L
    }
}
