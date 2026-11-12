package kr.carsound.app.core

/**
 * 소리를 걸 수 있는 차량 이벤트 목록.
 * key는 테마 폴더의 소리 파일 이름(<key>_<번호>.wav)과 녹음 파일 이름에 그대로 쓰인다.
 */
enum class CarEvent(val key: String, val label: String, val glyph: Glyph) {
    DEPART("depart", "출발 (P→D)", Glyph.GEAR_D),
    PARK("park", "주차 (→P)", Glyph.GEAR_P),
    REVERSE("reverse", "후진 (R)", Glyph.GEAR_R),
    TURN_LEFT("turn_left", "왼쪽 깜빡이", Glyph.ARROW_LEFT),
    TURN_RIGHT("turn_right", "오른쪽 깜빡이", Glyph.ARROW_RIGHT),
    WINDOW_DOWN("window_down", "창문 내림", Glyph.WINDOW_DOWN),
    WINDOW_UP("window_up", "창문 올림", Glyph.WINDOW_UP),
    DOOR_OPEN("door_open", "문 열림", Glyph.DOOR_OPEN),
    DOOR_CLOSE("door_close", "문 닫힘", Glyph.DOOR_CLOSE),
    BELT_ON("belt_on", "안전벨트 착용", Glyph.BELT),
    WIPER_ON("wiper_on", "와이퍼 켜기", Glyph.WIPER),
    AC_ON("ac_on", "에어컨 켜기", Glyph.SNOW),
    AC_OFF("ac_off", "에어컨 끄기", Glyph.SNOW_OFF),
    REAR_SEAT("rear_seat", "뒷좌석 탑승", Glyph.BABY);

    companion object {
        /** 파일 이름·설정에 저장된 key로 이벤트를 찾는다 */
        fun fromKey(key: String): CarEvent? = values().firstOrNull { it.key == key }
    }
}

/** 알림 띠와 앱 화면에 그리는 이벤트 그림 종류(그림은 glyph/GlyphPainter가 그린다) */
enum class Glyph {
    GEAR_D, GEAR_P, GEAR_R, ARROW_LEFT, ARROW_RIGHT, WINDOW_DOWN, WINDOW_UP,
    DOOR_OPEN, DOOR_CLOSE, BELT, WIPER, SNOW, SNOW_OFF, BABY
}

/** 기어 상태. 녹음·편집 잠금 판단의 기준 */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }
