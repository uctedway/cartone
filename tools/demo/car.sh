#!/bin/zsh
# 시연용 차량 조작 도구(앱 코드와 분리).
# Pleos 에뮬레이터 안의 "Pleos Connect Virtual Console"(포트 8088)에 값을 보내
# 실제 Vehicle SDK 이벤트를 발생시킨다.
#
# 준비: adb -s <에뮬레이터> forward tcp:18088 tcp:8088
# 사용: tools/demo/car.sh keepalive &   # 콘솔을 '접속 중'으로 유지(없으면 값이 안 바뀜)
#       tools/demo/car.sh gear D | turn left | turn off | window down | door open | rear on ...

U=${CONSOLE:-http://127.0.0.1:18088}
post() { curl -s -m 3 -X POST "$U$1" >/dev/null && echo "→ $1"; }

case "$1" in
  keepalive) # 콘솔은 접속 화면이 없으면 '비활성'이 되므로 1초마다 접속 신호를 보낸다
    while true; do curl -s -m 2 -X POST "$U/browser/session?client=demo" >/dev/null; sleep 1; done ;;
  gear)   # P R N D
    case "$2" in P) g=Park;; R) g=Reverse;; N) g=Neutral;; D) g=Drive;; esac; post "/vehicle/gear?gear=$g" ;;
  speed)  post "/vehicle/controls?speed_kph=$2" ;;
  turn)   # left right off
    case "$2" in left) m=BlinkLeft;; right) m=BlinkRight;; *) m=Off;; esac; post "/vehicle/turn-signal?mode=$m" ;;
  window) # down up (운전석)
    [[ "$2" == down ]] && post "/vehicle/body?window_fl=80" || post "/vehicle/body?window_fl=0" ;;
  door)   # open close (운전석)
    [[ "$2" == open ]] && post "/vehicle/body?door_fl=true" || post "/vehicle/body?door_fl=false" ;;
  wiper)  # on off
    [[ "$2" == on ]] && post "/vehicle/body?front_wiper=Low" || post "/vehicle/body?front_wiper=Off" ;;
  rear)   # on off  ※ 에뮬레이터 콘솔은 탑승 값이 SDK에 반대로 전달된다(확인됨). 그래서 on 은 false 를 보낸다
    [[ "$2" == on ]] && post "/vehicle/seat?area=rear_right&kind=occupied&value=false" || post "/vehicle/seat?area=rear_right&kind=occupied&value=true" ;;
  *) echo "사용법: $0 keepalive|gear P/R/N/D|speed 숫자|turn left/right/off|window down/up|door open/close|wiper on/off|rear on/off" ;;
esac
