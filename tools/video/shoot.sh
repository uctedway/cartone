#!/bin/zsh
# 카톤 시연 촬영(이벤트 확인형): 동작 → 앱이 실제로 소리를 냈다는 로그(PLAY) 확인 → 다음.
# 사용: shoot.sh A   (P단 화면·테마·녹음·켜고 끄기·녹음 취소·출발)
#       shoot.sh B   (주행 중 이벤트)
#       shoot.sh C   (주차·뒷좌석 자동 전환)
# 결과: raw_<파트>.webm(에뮬레이터 바깥 녹화, 소리 포함), seg_<파트>.txt(라벨 시작 소리 끝, 녹화 시작 기준 ms)
set -u
P=$1
D=${0:a:h}
A(){ adb -s emulator-5558 "$@"; }
U=http://127.0.0.1:18088
post(){ curl -s -m 5 -X POST "$U$1" >/dev/null; }
tap(){ A shell input tap $1 $2; }
ms(){ perl -MTime::HiRes -e 'printf "%d", Time::HiRes::time()*1000'; }
# 오른쪽 화면에 카톤이 보이는지(카톤 작업 visible=true, 내비 작업 visible=false)
appvis(){ A shell dumpsys activity activities | grep -E "^ +\* Task\{" | grep -q "kr.cartone.app U=.*visible=true" && ! A shell dumpsys activity activities | grep -E "^ +\* Task\{" | grep -q "navigation.app U=.*visible=true"; }
front(){ for i in 1 2 3 4; do appvis && return; tap 1462 1372; sleep 3; done; }
plays(){ A logcat -d -s CarTone:I | grep -c " PLAY $1 "; }
SEG=$D/seg_$P.txt
# 동작 하나: act <라벨> <PLAY키> <명령...>
act(){
  local label=$1 key=$2; shift 2
  local before=${PRE:-$(plays $key)} t1=$(( $(ms) - T0 ))
  eval "$@"
  local n=0
  while [ $(plays $key) -le $before ] && [ $n -lt 60 ]; do
    sleep 0.5; n=$((n+1))
    # 20초 안에 안 오면(에뮬레이터 지연) 같은 동작을 한 번 더 보낸다
    [ $n -eq 40 ] && [ -n "${RETRY:-}" ] && eval "$RETRY"
  done
  local t2=$(( $(ms) - T0 ))
  sleep 2.6
  echo "$label $t1 $t2 $(( $(ms) - T0 ))" >> $SEG
  [ $n -ge 60 ] && echo "경고: $label 소리 확인 실패"
}
note(){ echo "$1 $(( $(ms) - T0 )) $(( $(ms) - T0 )) $(( $(ms) - T0 + ${2:-2500} ))" >> $SEG; sleep $(( ${2:-2500} / 1000.0 )); }

: > $SEG
if [ $P = A ]; then
  post "/vehicle/controls?speed_kph=0"; post "/vehicle/gear?gear=Park"; post "/vehicle/turn-signal?mode=Off"
  post "/vehicle/body?window_fl=0"; post "/vehicle/body?front_wiper=Off"
  post "/vehicle/seat?area=rear_right&kind=occupied&value=true"
  A shell am force-stop --user 10 kr.cartone.app
  A shell run-as kr.cartone.app --user 10 rm -rf /data/user/10/kr.cartone.app/shared_prefs /data/user/10/kr.cartone.app/files/voices /data/user/10/kr.cartone.app/files/custom_themes.json
  A shell am start --user 10 -n kr.cartone.app/.ui.MainActivity >/dev/null; sleep 5; front; sleep 3
fi
A emu screenrecord start --time-limit 178 --size 1600x900 --bit-rate 6M --fps 30 $D/raw_$P.webm >/dev/null
sleep 1.0; T0=$(ms)

if [ $P = A ]; then
  note intro 3500
  act listen_window window_down "tap 2324 823"
  act listen_turn turn_right "tap 1526 823"
  tap 1495 346; sleep 1.5
  act drum_park park "tap 2324 623"
  act drum_window window_down "tap 2324 823"
  act drum_turn turn_right "tap 1526 823"
  tap 1098 346; sleep 1.5
  # 녹음: 기본 테마(래틀박스)에서 출발 칸 녹음 → 가운데 녹음 창 → 3초 뒤 자동 저장 → 복사본 테마가 생김
  t=$(( $(ms) - T0 )); tap 1635 623; sleep 7.5
  echo "rec $t $t $(( $(ms) - T0 ))" >> $SEG
  # 화면 밖: 에뮬레이터는 마이크 입력이 없어 녹음 파일을 예시 합성 음성으로 바꾸고 앱을 다시 띄운다
  id=$(A shell run-as kr.cartone.app --user 10 ls /data/user/10/kr.cartone.app/files/voices | tr -d '\r' | grep '^c' | head -1)
  A push $D/voice/depart.wav /data/local/tmp/depart.wav >/dev/null
  A shell "cat /data/local/tmp/depart.wav | run-as kr.cartone.app --user 10 sh -c 'cat > /data/user/10/kr.cartone.app/files/voices/$id/depart.wav'"
  A shell am force-stop --user 10 kr.cartone.app
  A shell am start --user 10 -n kr.cartone.app/.ui.MainActivity >/dev/null; sleep 5; front; sleep 3
  act listen_voice depart "tap 1526 623"
  # 켜고 끄기: 와이퍼 칸만 끔(금지 표시) → 전체 스위치 끔(칸 스위치 잠김) → 전체 다시 켬. 와이퍼는 꺼 둔 채로 주행한다
  t=$(( $(ms) - T0 )); tap 1430 1123; sleep 2.4; tap 2475 150; sleep 2.6; tap 2475 150; sleep 1.8
  echo "toggle $t $t $(( $(ms) - T0 ))" >> $SEG
  PRE=$(plays depart)   # 로그 확인(약 3초)을 미리 해 둔다: 최대 3초 녹음이 끝나기 전에 출발해야 '취소'를 보여줄 수 있다
  note record_start 0; tap 2431 823; sleep 1.0
  act depart depart 'post "/vehicle/gear?gear=Drive"'
  PRE=
  sleep 1.5
elif [ $P = B ]; then
  # 시작 상태: 기어 D, 내비 앞
  tap 1093 1357; sleep 3
  post "/vehicle/controls?speed_kph=60"; sleep 3
  note drive 2500
  # 깜빡이는 켜 둔 동안 소리가 되풀이되고 알림 띠가 유지된다. 5초쯤 켜 두었다가 끄고, 띠가 내려갈 때까지 기다린다
  act turn_left turn_left 'post "/vehicle/turn-signal?mode=BlinkLeft"'
  sleep 2.4; post "/vehicle/turn-signal?mode=Off"; sleep 3
  act window_down window_down 'post "/vehicle/body?window_fl=80"'
  # 와이퍼는 P단에서 꺼 두었으므로 신호가 와도 조용해야 한다(소리 확인 대신 4초 보여 준다)
  t=$(( $(ms) - T0 )); post "/vehicle/body?front_wiper=Low"; sleep 4
  echo "wiper_off $t $t $(( $(ms) - T0 ))" >> $SEG
  act turn_right turn_right 'post "/vehicle/turn-signal?mode=BlinkRight"'
  sleep 2.4; post "/vehicle/turn-signal?mode=Off"; post "/vehicle/body?front_wiper=Off"; sleep 3
  act window_up window_up 'post "/vehicle/body?window_fl=0"'
  sleep 1
else
  # C: 주차와 뒷좌석 자동 전환
  post "/vehicle/controls?speed_kph=0"; sleep 2
  act park park 'post "/vehicle/gear?gear=Park"'
  front; sleep 1.5
  tap 1495 346; sleep 1.5; tap 1258 456; sleep 2
  act rear rear_seat 'post "/vehicle/seat?area=rear_right&kind=occupied&value=false"'
  sleep 1.5
fi
A emu screenrecord stop >/dev/null; sleep 4
A logcat -d -s CarTone:I > $D/play_$P.log
cat $SEG
