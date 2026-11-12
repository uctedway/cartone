package kr.carsound.app.record

import kr.carsound.app.core.Gear

/**
 * 녹음·편집 잠금 규칙.
 * - 기어가 P일 때만 열린다. 기어를 모르면(신호 없음·오류) 닫힌 쪽으로 본다.
 * - P에서 벗어나는 순간 진행 중인 녹음을 취소한다.
 */
class ParkGate(private val recorder: VoiceRecorder) {
    var gear: Gear = Gear.UNKNOWN
        private set

    val isOpen get() = gear == Gear.PARK

    /** 잠금 상태가 바뀌었을 때 화면에 알려준다(열림 여부, 녹음이 취소됐는지) */
    val listeners = mutableListOf<(open: Boolean, cancelled: Boolean) -> Unit>()

    /** 기어가 바뀔 때마다 불린다. P에서 벗어났는데 녹음 중이면 녹음을 버린다 */
    fun onGear(g: Gear) {
        val prev = gear
        gear = g
        var cancelled = false
        if (!isOpen && recorder.isRecording) {
            recorder.cancel()
            cancelled = true
        }
        // 기어가 바뀌면 화면 상태 글자도 바뀌어야 하므로 매번 알린다
        if (prev != g || cancelled) listeners.forEach { it(isOpen, cancelled) }
    }
}
