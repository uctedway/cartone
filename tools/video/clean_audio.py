"""
시연 영상용 깨끗한 소리 트랙 만들기.
- 에뮬레이터 녹화에 들어간 소리는 차량 기본 볼륨 때문에 아주 작게(-29dB) 녹음된다.
  이것을 27dB 키우면 압축 잡음까지 같이 커져 지글거린다. 그래서 녹화된 소리는 시점을 찾는 데만 쓴다.
- 앱이 남긴 재생 기록(logcat의 "PLAY <이벤트> <파일> l= r=")으로 어떤 파일이 났는지 알아내고,
  그 원본 파일(앱에 든 테마 소리, 목소리 파일)을 녹화에서 소리가 실제로 난 시점에 그대로 놓는다.
- 좌우 음량(깜빡이 방향)도 기록대로 넣는다. 순정 작동음과 화면 터치음은 넣지 않는다.
결과: <작업 폴더>/clean_<파트>.wav (녹화 파일과 같은 시간축, 스테레오 48kHz)
사용: python3 clean_audio.py <작업 폴더>   (raw_<파트>.webm, play_C.log, voice/depart.wav가 있는 곳)
"""
import os
import re
import subprocess
import sys
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
THEMES = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "themes")
D = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else HERE

# 재생 기록 시각과 녹화 속 소리 시작이 이 안에서 맞으면 같은 소리로 본다(초)
NEAR = 0.15
# 테마를 불러온 뒤 늦게 난 소리는 기록보다 이만큼까지 늦을 수 있다(초)
LATE = 1.2


def plays():
    """재생 기록을 (시각 초, 파일 경로, 왼쪽, 오른쪽, 늦게 났는지) 목록으로 읽는다. 테마 전환 기록으로 어느 테마 파일인지 가린다"""
    out, theme = [], "rattle"
    for line in open(os.path.join(D, "play_C.log"), encoding="utf8", errors="ignore"):
        m = re.match(r"\d\d-\d\d (\d\d:\d\d:\d\d\.\d+).*CarTone\s*: (.*)", line)
        if not m:
            continue
        t = datetime.strptime(m.group(1), "%H:%M:%S.%f")
        sec = t.hour * 3600 + t.minute * 60 + t.second + t.microsecond / 1e6
        msg = m.group(2)
        load = re.match(r"테마 소리 로드: (\S+)", msg)
        if load:
            # 내 테마(복사본)는 래틀박스 소리를 바탕으로 한다
            theme = load.group(1) if load.group(1) in ("rattle", "drum") else "rattle"
            continue
        p = re.match(r"PLAY (\S+) (\S+) l=([\d.]+) r=([\d.]+)(.*)", msg)
        if not p:
            continue
        name = p.group(2)
        # 이벤트 이름 그대로인 파일(depart.wav)은 녹음한 목소리다
        path = os.path.join(D, "voice", name) if name == p.group(1) + ".wav" else os.path.join(THEMES, theme, name)
        out.append((sec, path, float(p.group(3)), float(p.group(4)), "로딩 후" in p.group(5)))
    return out


def onsets(raw):
    """녹화 파일에서 소리가 시작된 시각들(초)"""
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", raw, "-af", "silencedetect=n=-55dB:d=0.08", "-f", "null", "-"],
                       capture_output=True, text=True)
    return [float(x) for x in re.findall(r"silence_end: ([0-9.]+)", r.stderr)]


def duration(raw):
    r = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "packet=pts_time", "-of", "csv=p=0", raw],
                       capture_output=True, text=True)
    return float(r.stdout.split()[-1])


def align(log, ons, dur):
    """기록 시각과 녹화 시각의 차이를 찾는다. 가장 많은 소리가 맞아떨어지는 차이를 고른다"""
    best, best_n = None, -1
    for t, *_ in log:
        for o in ons:
            off = o - t
            n = sum(1 for u, *_ in log if 0 <= u + off <= dur and any(abs(u + off - x) < NEAR for x in ons))
            if n > best_n:
                best, best_n = off, n
    return best, best_n


def build(part, log):
    """한 파트의 소리 트랙을 만든다. 이 파트에서 쓴 마지막 재생 기록의 시각을 돌려준다"""
    raw = os.path.join(D, f"raw_{part}.webm")
    ons, dur = onsets(raw), duration(raw)
    off, matched = align(log, ons, dur)
    hits = []
    for t, path, l, r, late in log:
        at = t + off
        if not 0 <= at <= dur:
            continue
        # 녹화에서 실제로 소리가 시작된 시각에 붙인다
        near = [x for x in ons if (0 <= x - at <= LATE if late else abs(x - at) < NEAR)]
        if near:
            at = min(near, key=lambda x: abs(x - at))
        hits.append((at, path, l, r))
    args, graph = [], []
    for i, (at, path, l, r) in enumerate(hits):
        args += ["-i", path]
        graph.append(f"[{i}:a]aresample=48000,pan=stereo|c0={l}*c0|c1={r}*c0,adelay={round(at * 1000)}:all=1[a{i}]")
    mix = "".join(f"[a{i}]" for i in range(len(hits)))
    graph.append(f"{mix}amix=inputs={len(hits)}:normalize=0,alimiter=limit=0.89,apad,atrim=duration={dur + 1:.3f}[o]")
    out = os.path.join(D, f"clean_{part}.wav")
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error"] + args + ["-filter_complex", ";".join(graph), "-map", "[o]",
                        "-ar", "48000", "-ac", "2", "-c:a", "pcm_s16le", out], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(r.stderr[-1500:])
    print(f"{part}: 소리 {len(hits)}개(녹화와 맞은 것 {matched}개), 길이 {dur:.1f}초")
    for at, path, l, r in hits:
        print(f"   {at:6.2f}s {os.path.basename(os.path.dirname(path))}/{os.path.basename(path)} l={l} r={r}")
    return max(t for t, *_ in log if 0 <= t + off <= dur)


def main():
    log = plays()
    # 파트는 A, B, C 순서로 찍으므로, 앞 파트에서 쓴 기록은 빼고 맞춘다(비슷한 소리 간격에 잘못 맞는 것을 막는다)
    for part in "ABC":
        last = build(part, log)
        log = [x for x in log if x[0] > last]


if __name__ == "__main__":
    main()
