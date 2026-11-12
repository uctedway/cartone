package kr.carsound.app.sound

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kr.carsound.app.core.CarEvent

/**
 * 켜져 있는 동안 이어지는 이벤트(깜빡이)의 소리를 꺼질 때까지 되풀이한다.
 * - 소리 하나가 끝나고 잠깐 쉰 뒤 다음 소리를 낸다.
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
    private var ending = false
    private val tick = Runnable { repeat() }
    private val stopNow = Runnable { finish() }

    /** 같은 이벤트를 이미 잇고 있으면 끝내려던 것을 취소하고 true(새로 소리를 낼 필요 없음) */
    fun resume(e: CarEvent): Boolean {
        if (active != e) return false
        if (ending) {
            ending = false
            main.removeCallbacks(stopNow)
            main.postDelayed(tick, (soundEndsAt - now()).coerceAtLeast(0) + GAP_MS)
        }
        return true
    }

    /** 첫 소리를 방금 냈다. 그 길이(ms)를 받아 다음 소리를 예약한다 */
    fun start(e: CarEvent, firstMs: Long) {
        finish()
        active = e
        startedAt = now()
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
        main.postDelayed(tick, (soundMs + GAP_MS).coerceAtLeast(MIN_PERIOD_MS))
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
        /** 소리와 소리 사이 쉬는 시간(ms) */
        private const val GAP_MS = 350L

        /** 소리가 아주 짧아도 이보다 자주 내지는 않는다(ms) */
        private const val MIN_PERIOD_MS = 1200L

        /** 꺼진 뒤 표시를 남겨 두는 시간 범위(ms). 값이 잠깐 끊긴 것인지 기다리는 시간이기도 하다 */
        private const val GRACE_MS = 600L
        private const val MAX_TAIL_MS = 2500L

        /** 한 번 켜진 깜빡이에 소리를 되풀이하는 최대 시간(ms) */
        private const val MAX_MS = 60_000L
    }
}
