"""
차소리 시연 영상 편집(ffmpeg).
- raw_<파트>.webm(에뮬레이터 화면+소리 녹화)에서 cuts.txt에 적힌 구간만 잘라 이어 붙인다.
- cuts.txt 한 줄: <파트> <자막 이름> <시작 초> <끝 초>. 시각은 녹화 파일 안의 시각이다.
  소리 장면은 녹화에 실제로 들어간 소리 시작 시점(silencedetect로 찾음)의 1.3초쯤 앞에서 시작한다.
  촬영 스크립트가 적어 둔 시각은 에뮬레이터 지연 때문에 최대 2초 어긋나서 쓰지 않는다.
- 조각을 따로 만들어 붙이면 조각마다 소리 길이가 조금씩 달라 뒤로 갈수록 화면과 어긋난다.
  그래서 한 번의 ffmpeg 실행 안에서 화면과 소리를 같은 길이로 잘라 함께 이어 붙인다.
- 화면 아래 180px 자막 띠, 소리는 에뮬레이터에서 녹음된 그대로이고 음량만 키운다.
사용: python3 build.py
"""
import os
import subprocess
import sys

D = os.path.dirname(os.path.abspath(__file__))
FPS = 30
FOOT = "Pleos Connect 에뮬레이터 화면과 소리를 그대로 녹화 · 차량 값은 에뮬레이터 차량 시뮬레이터로 입력 · 목소리 예시는 합성 음성"

CAP = {
    "intro": ("차소리 · 운전 동작마다 우리 가족의 소리", "깜빡이·창문·기어 같은 차량 신호(Pleos Vehicle SDK)에 소리를 입히는 앱"),
    "listen": ("P단에서 테마 소리 미리 듣기", "[듣기]를 누르면 소리가 나는 동안 가운데에 아이콘 · 창문 내림 → 용수철 · 오른쪽 깜빡이 → 덴덴북"),
    "rec": ("[녹음]을 누르면 가운데에 녹음 창 · 최대 3초", "기본 테마(래틀박스)는 그대로 두고 '래틀박스 복사본' 테마가 새로 생겨 내 목소리가 걸림"),
    "toggle": ("이벤트마다 켜고 끄기 · 위쪽 스위치로 전체 끄기", "와이퍼 소리만 끔 → 금지 표시 · 전체를 끄면 칸 스위치는 잠기고 상태는 그대로 남음"),
    "voice": ("녹음한 칸에는 '내 녹음' 표시 · [듣기]로 확인", "에뮬레이터에는 마이크 입력이 없어, 이 장면부터는 녹음 파일을 예시 합성 음성으로 바꿔 넣었습니다"),
    "drum": ("테마를 드럼킷으로 바꾸면", "같은 동작, 다른 소리 · 주차 → 킥+심벌 · 창문 → 탐 · 깜빡이 → 하이햇"),
    "record": ("녹음 중에 기어를 D로 바꾸면", "녹음은 즉시 취소되고 저장되지 않음 · 앱 화면은 주행 중 잠기고, 출발 순간 가족 목소리"),
    "drive": ("주행 중: 내비 위에 알림 띠 + 소리", "에뮬레이터에는 지도 데이터가 없어 지도가 비어 보입니다(실제 차량에서는 지도 위에 표시)"),
    "turn_left": ("왼쪽 깜빡이", "왼쪽 스피커에서 더 크게 · 왼쪽 위 카메라 화면은 순정 기능"),
    "window_down": ("창문 내림", "용수철 '보잉~' · 순정 경고음은 그대로 두고 그 위에 덧붙임"),
    "wiper_off": ("와이퍼 켜기 → 조용", "P단에서 꺼 둔 이벤트는 차량 신호가 와도 소리도 알림 띠도 내지 않음"),
    "turn_right": ("오른쪽 깜빡이", "덴덴북 · 오른쪽 스피커에서 더 크게"),
    "window_up": ("창문 올림", "용수철 '보잉~' (위로)"),
    "park": ("도착해서 P단으로", "칼림바가 내려오며 마무리"),
    "rear": ("뒷좌석 탑승 → 래틀박스 자동 전환 (사용자가 켠 경우)", "드럼킷이던 소리가 래틀박스로 바뀌고 풍경 소리 · 알림 띠도 래틀박스 색"),
}

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(" ".join(cmd[:4]) + "\n" + r.stderr[-1500:])


def cuts():
    """cuts.txt를 읽어 (파트, 자막 이름, 시작 초, 길이 초) 목록으로 만든다. 길이는 프레임 단위로 맞춘다"""
    out = []
    for line in open(os.path.join(D, "cuts.txt"), encoding="utf8"):
        if not line.strip():
            continue
        part, cap, s, e = line.split()
        frames = round((float(e) - float(s)) * FPS)
        out.append((part, cap, float(s), frames / FPS))
    return out


def caption(tmp, cap):
    """자막 띠 그림을 한 번만 만들어 돌려쓴다"""
    png = os.path.join(tmp, f"cap_{cap}.png")
    if not os.path.exists(png):
        t, sub = CAP[cap]
        run(["swift", os.path.join(D, "mkcap.swift"), png, t, sub, FOOT])
    return png


def main():
    segs = cuts()
    tmp = os.path.join(D, "cut"); os.makedirs(tmp, exist_ok=True)
    inputs, graph, pads = [], [], ""
    for k, (part, cap, s, dur) in enumerate(segs):
        # 구간 앞뒤로 여유를 두고 읽은 뒤, 필터에서 화면과 소리를 정확히 같은 길이로 자른다
        inputs += ["-ss", f"{s:.3f}", "-t", f"{dur + 1:.3f}", "-i", os.path.join(D, f"raw_{part}.webm"), "-i", caption(tmp, cap)]
        v, c = 2 * k, 2 * k + 1
        graph.append(f"[{v}:v]fps={FPS},scale=-2:900,pad=1920:1080:(ow-iw)/2:0:black[v{k}];[{c}:v]scale=1920:180[c{k}];"
                     f"[v{k}][c{k}]overlay=0:900,trim=duration={dur:.4f},setpts=PTS-STARTPTS,format=yuv420p[o{k}]")
        graph.append(f"[{v}:a]aresample=48000:async=1:first_pts=0,aformat=channel_layouts=stereo,apad,"
                     f"atrim=duration={dur:.4f},asetpts=PTS-STARTPTS[a{k}]")
        pads += f"[o{k}][a{k}]"
        print(f"{k:02d} {part} {cap:12s} {s:7.2f}s +{dur:.2f}s")
    graph.append(f"{pads}concat=n={len(segs)}:v=1:a=1[v][a]")
    joined = os.path.join(tmp, "joined.mkv")
    run(["ffmpeg", "-y", "-loglevel", "error"] + inputs + ["-filter_complex", ";".join(graph), "-map", "[v]", "-map", "[a]",
         "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-c:a", "pcm_s16le", joined])
    # 에뮬레이터 차 볼륨이 낮게 녹음되므로 전체 음량만 올린다(봉우리 -2dB 기준)
    r = subprocess.run(["ffmpeg", "-i", joined, "-af", "volumedetect", "-vn", "-f", "null", "-"], capture_output=True, text=True)
    peak = float([l for l in r.stderr.splitlines() if "max_volume" in l][0].split(":")[1].split()[0])
    gain = -2.0 - peak
    final = os.path.join(D, "차소리_시연영상.mp4")
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", joined, "-c:v", "copy", "-af", f"volume={gain:.1f}dB", "-c:a", "aac", "-b:a", "160k",
         "-movflags", "+faststart", final])
    print("peak", peak, "gain", round(gain, 1), "→", final)


if __name__ == "__main__":
    main()
