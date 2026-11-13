package kr.cartone.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import kr.cartone.app.core.CarEvent
import kr.cartone.app.core.Gear

/**
 * Pleos Vehicle SDK 리스너를 등록하고, 값의 "변화"를 차량 이벤트로 바꿔 알려준다.
 * - 등록 직후 들어오는 첫 값은 현재 상태일 뿐이라 이벤트로 내보내지 않는다.
 * - SDK 콜백은 다른 스레드에서 오므로 메인 스레드로 옮겨서 전달한다.
 */
class VehicleWatcher(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    /** 리스너 15개 등록(기기 서비스 호출)은 메인 스레드를 막지 않도록 별도 스레드에서 한다 */
    private val worker = Handler(HandlerThread("vehicle-register").apply { start() }.looper)
    private val edges = EdgeTracker()

    var onEvent: ((CarEvent) -> Unit)? = null

    /** 켜져 있는 동안 이어지는 이벤트(깜빡이)가 끝났을 때 */
    var onEventEnd: ((CarEvent) -> Unit)? = null

    /** 지금 켜져 있는 깜빡이 이벤트(없으면 null). 메인 스레드에서만 만진다 */
    private var turning: CarEvent? = null

    /** 기어가 바뀌었을 때(첫 값 포함). 녹음 잠금이 이 값을 따른다 */
    var onGear: ((Gear) -> Unit)? = null

    var gear: Gear = Gear.UNKNOWN
        private set

    /** SDK에 연결하고 리스너를 등록한다 */
    fun start() {
        vehicle.initialize()
        // 서비스 연결 직후 바로 등록하면 놓칠 수 있어 잠깐 기다린다
        worker.postDelayed({ registerAll() }, 1500)
    }

    fun stop() = runCatching { vehicle.release() }

    /** 이벤트를 메인 스레드로 옮겨 알린다 */
    private fun emit(e: CarEvent) = main.post { onEvent?.invoke(e) }

    /** 리스너 하나가 실패해도 나머지는 계속 등록한다 */
    private fun safe(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "$name 등록 실패: $it") }
    }

    /** 도메인별로 리스너를 등록한다 */
    private fun registerAll() {
        watchGear()
        watchTurnSignal()
        watchWindows()
        watchDoors()
        watchSeatBelt()
        watchRearSeats()
        watchWiper()
        watchAirConditioner()
    }

    /** 기어: 출발·주차·후진과 녹음 잠금의 기준 */
    private fun watchGear() = safe("기어") {
        // 리스너는 바뀔 때만 알려줄 수 있어, 지금 기어를 먼저 한 번 읽어 둔다
        vehicle.getGear().getCurrentGearSelectionState(
            { g -> main.post { onGearChanged(toGear(g)) } },
            { e -> Log.w(TAG, "현재 기어 읽기 실패 $e") },
        )
        vehicle.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(g: VehicleGear?) = main.post { onGearChanged(toGear(g)) }.let {}
            override fun onFailed(e: Exception) = Log.w(TAG, "기어 오류 $e").let {}
        })
    }

    /** 깜빡이: 켜질 때 이벤트, 꺼지거나 반대쪽으로 바뀔 때 끝 알림 */
    private fun watchTurnSignal() = safe("깜빡이") {
        vehicle.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(v: TurnSignalValue?) {
                if (!edges.changed("turn", v?.name)) return
                val now = when (v) {
                    TurnSignalValue.LEFT -> CarEvent.TURN_LEFT
                    TurnSignalValue.RIGHT -> CarEvent.TURN_RIGHT
                    else -> null
                }
                // 꺼지거나 반대쪽으로 바뀌면 이전 깜빡이를 먼저 끝내고, 새로 켜진 쪽을 알린다
                main.post {
                    turning?.takeIf { it != now }?.let { onEventEnd?.invoke(it) }
                    turning = now
                    now?.let { onEvent?.invoke(it) }
                }
            }
            override fun onFailed(e: Exception) = Log.w(TAG, "깜빡이 오류 $e").let {}
        })
    }

    /** 창문: 운전석·조수석 두 곳을 본다. 값이 커지면 내려가는(열리는) 것 */
    private fun watchWindows() {
        for (w in listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT)) safe("창문 $w") {
            vehicle.getWindow().registerWindowPosition(WindowArea(w), object : WindowPositionListener {
                override fun onWindowPositionUpdated(pos: Int?) {
                    edges.direction("window-$w", pos)?.let { up -> emit(if (up) CarEvent.WINDOW_DOWN else CarEvent.WINDOW_UP) }
                }
                override fun onFailed(e: Exception) = Log.w(TAG, "창문 오류 $e").let {}
            })
        }
    }

    /** 문: 네 곳 모두 본다. 값이 0보다 크면 열린 것 */
    private fun watchDoors() {
        for (d in listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)) safe("문 $d") {
            vehicle.getDoor().registerDoorPosition(DoorArea(d), object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) {
                    val open = (pos ?: 0) > 0
                    if (edges.changed("door-$d", open)) emit(if (open) CarEvent.DOOR_OPEN else CarEvent.DOOR_CLOSE)
                }
                override fun onFailed(e: Exception) = Log.w(TAG, "문 오류 $e").let {}
            })
        }
    }

    /** 안전벨트: 운전석을 맬 때만 알린다 */
    private fun watchSeatBelt() = safe("안전벨트") {
        vehicle.getSeat().registerSeatBeltBuckledState(SeatArea(SeatValue.ROW_1_LEFT), object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(on: Boolean?) {
                if (edges.changed("belt", on) && on == true) emit(CarEvent.BELT_ON)
            }
            override fun onFailed(e: Exception) = Log.w(TAG, "벨트 오류 $e").let {}
        })
    }

    /** 뒷좌석 탑승: 아이 테마 자동 전환에 쓴다 */
    private fun watchRearSeats() {
        for (s in listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)) safe("뒷좌석 $s") {
            vehicle.getSeat().registerSeatOccupancy(SeatArea(s), object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(on: Boolean?) {
                    Log.d(TAG, "좌석 $s 탑승값 $on")
                    if (edges.changed("seat-$s", on) && on == true) emit(CarEvent.REAR_SEAT)
                }
                override fun onFailed(e: Exception) = Log.w(TAG, "좌석 오류 $e").let {}
            })
        }
    }

    /** 와이퍼: 켜질 때만 알린다 */
    private fun watchWiper() = safe("와이퍼") {
        vehicle.getWiper().registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), object : WiperStateListener {
            override fun onWiperStateUpdated(s: WiperState?) {
                val on = s == WiperState.ON
                if (edges.changed("wiper", on) && on) emit(CarEvent.WIPER_ON)
            }
            override fun onFailed(e: Exception) = Log.w(TAG, "와이퍼 오류 $e").let {}
        })
    }

    /** 에어컨: 켜질 때와 꺼질 때를 따로 알린다 */
    private fun watchAirConditioner() = safe("에어컨") {
        vehicle.getHVAC().registerHvacAcState(SeatArea(SeatValue.ROW_1_LEFT), object : HvacAcStateListener {
            override fun onHvacAcStateUpdated(on: Boolean?) {
                if (on != null && edges.changed("ac", on)) emit(if (on) CarEvent.AC_ON else CarEvent.AC_OFF)
            }
            override fun onFailed(e: Exception) = Log.w(TAG, "에어컨 오류 $e").let {}
        })
    }

    /** 기어 변화를 출발·주차·후진 이벤트로 바꾼다 */
    private fun onGearChanged(g: Gear) {
        val before = gear
        if (before == g) return
        Log.i(TAG, "기어 $before → $g")
        gear = g
        onGear?.invoke(g)
        if (before == Gear.UNKNOWN || before == g) return
        when {
            before == Gear.PARK && g == Gear.DRIVE -> onEvent?.invoke(CarEvent.DEPART)
            g == Gear.PARK -> onEvent?.invoke(CarEvent.PARK)
            g == Gear.REVERSE -> onEvent?.invoke(CarEvent.REVERSE)
        }
    }

    /** SDK 기어 값을 앱의 기어 값으로 바꾼다 */
    private fun toGear(g: VehicleGear?): Gear = when (g) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE // 수동 기어 1~9단도 주행으로 본다
    }

    companion object { const val TAG = "CarTone" }
}
