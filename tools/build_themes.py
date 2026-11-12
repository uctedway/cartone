"""
기본 테마 소리 만들기 도구.

- 래틀박스: 딸랑이 효과음(CC0 녹음·자체 합성음)을 그대로 쓰거나 이어 붙인다.
- 드럼킷: CC0 드럼 샘플을 박자 위치에 맞춰 섞어 짧은 필인을 만든다.
- 주행 중 주의 분산을 줄이려고 모든 소리는 1.5초 이내로 만든다(build 끝에서 검사).
- 결과: app/src/main/assets/themes/<테마>/<이벤트>_<번호>.wav (모노 44.1kHz 16bit)

실행: CARTONE_RATTLE_SRC=<딸랑이 원본 폴더> CARTONE_DRUM_SRC=<드럼 샘플 폴더> python3 tools/build_themes.py   (ffmpeg 필요)
원본 녹음은 저장소에 넣지 않았다. 만들어진 결과(assets/themes)만 들어 있다.
"""
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "themes")
# 원본 녹음 폴더는 환경 변수로 받는다(없으면 저장소 옆 sources 폴더를 본다)
RATTLE = os.environ.get("CARTONE_RATTLE_SRC", os.path.join(ROOT, "sources", "rattle"))
DRUM = os.environ.get("CARTONE_DRUM_SRC", os.path.join(ROOT, "sources", "drum"))


# 깜빡이 소리의 두 번째 종 위치와 그 뒤 여운(ms). 합쳐서 1초(점멸 간격)를 넘지 않게 한다
BLINK_SECOND_MS = 410
BLINK_TAIL_MS = 540


def ffmpeg(args):
    """ffmpeg를 조용히 실행하고 실패하면 바로 멈춘다"""
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error"] + args, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("ffmpeg 실패: " + r.stderr)


def single(src, dst, gain_db=0.0):
    """원본 하나를 모노 44.1kHz로 맞춰 복사한다(앞뒤 무음 정리, 끝은 짧게 페이드)"""
    ffmpeg(["-i", src, "-af",
            f"silenceremove=start_periods=1:start_threshold=-50dB,volume={gain_db}dB,"
            "areverse,silenceremove=start_periods=1:start_threshold=-55dB,afade=t=in:d=0.02,areverse,"
            "atrim=0:1.5,afade=t=out:st=1.25:d=0.25",  # 1.5초 상한, 끝은 부드럽게
            "-ac", "1", "-ar", "44100", "-sample_fmt", "s16", dst])


def sequence(hits, dst, tail_ms=600):
    """(파일, 시작 ms, 음량 dB) 목록을 한 소리로 섞는다. 박자 맞춘 필인·아르페지오용"""
    args, chains = [], []
    for i, (src, at, db) in enumerate(hits):
        args += ["-i", src]
        chains.append(f"[{i}:a]aformat=sample_rates=44100:channel_layouts=mono,volume={db}dB,adelay={at}:all=1[a{i}]")
    end = max(at for _, at, _ in hits) + tail_ms
    mix = "".join(f"[a{i}]" for i in range(len(hits)))
    graph = ";".join(chains) + f";{mix}amix=inputs={len(hits)}:normalize=0,alimiter=limit=0.8,atrim=0:{end / 1000},afade=t=out:st={(end - 150) / 1000}:d=0.15[o]"
    ffmpeg(args + ["-filter_complex", graph, "-map", "[o]", "-ac", "1", "-ar", "44100", "-sample_fmt", "s16", dst])


def r(name):
    return os.path.join(RATTLE, name)


def d(name):
    return os.path.join(DRUM, name)


def build_rattle(out):
    """래틀박스: 이벤트마다 장난감 소리 하나, 변형이 있으면 여러 개"""
    variants = {
        "window_down": ["spring_down_mid_0", "spring_down_mid_1"],
        "window_up": ["spring_up_mid_0", "spring_up_mid_1"],
        "door_open": ["squeak_in_mid_0", "squeak_in_mid_1"],
        "door_close": ["squeak_out_0", "squeak_out_1"],
        "belt_on": ["castanet_a_mid", "castanet_b_mid"],
        "wiper_on": ["maraca_fwd_mid_0", "maraca_fwd_mid_1", "maraca_fwd_mid_2"],
        "reverse": ["handbell_mid_0", "handbell_mid_1"],
        "ac_on": ["accordion_open_mid_0", "accordion_open_mid_1"],
        "ac_off": ["accordion_close_mid_0", "accordion_close_mid_1"],
    }
    for ev, names in variants.items():
        for i, n in enumerate(names):
            single(r(n + ".wav"), os.path.join(out, f"{ev}_{i}.wav"))
    # 깜빡이: 작은 종 두 개로 "딩-동". 점멸 한 번(1초)에 한 번씩 되풀이되므로 1초 안에 끝나게 만든다.
    # 왼쪽은 딩→동, 오른쪽은 동→딩. 두 번째 종은 순정 작동음의 두 번째 박(약 0.41초)에 맞춘다
    for i in range(2):
        a_, b_ = r(f"twinbell_a_soft_{i}.wav"), r(f"twinbell_b_soft_{i}.wav")
        sequence([(a_, 0, 0), (b_, BLINK_SECOND_MS, 0)], os.path.join(out, f"turn_left_{i}.wav"), BLINK_TAIL_MS)
        sequence([(b_, 0, 0), (a_, BLINK_SECOND_MS, 0)], os.path.join(out, f"turn_right_{i}.wav"), BLINK_TAIL_MS)
    # 출발: 실로폰 도-미-솔-도 올라가기 / 주차: 칼림바 내려오기 / 뒷좌석 탑승: 풍경 반짝임
    sequence([(r(f"xylo_{n}_hard.wav"), i * 140, -2) for i, n in enumerate([0, 2, 3, 5])], os.path.join(out, "depart_0.wav"), 900)
    sequence([(r(f"kalimba_{n}_soft.wav"), i * 180, 0) for i, n in enumerate([5, 3, 2, 0])], os.path.join(out, "park_0.wav"), 900)
    sequence([(r(f"chime_{n}_soft.wav"), i * 110, -1) for i, n in enumerate([0, 2, 4, 3, 4])], os.path.join(out, "rear_seat_0.wav"), 1000)


def build_drum(out):
    """드럼킷: 실제 드럼 녹음을 박자(120bpm 16분음표 = 125ms)에 맞춰 섞는다"""
    s16 = 125
    kick, snare, crash = d("clear_exp_snarekick_studio_kick_3_1.wav"), "clear_snarekick_snare_{}_{}.wav", d("cymbals_crash_3_1.wav")
    # 출발: 스네어 4번 → 탐 하이·미드·플로어 → 크래시+킥
    depart = [(d(snare.format(2, k % 3 + 1)), i * s16, -4 + i) for i, k in enumerate(range(4))]
    depart += [(d("toms_hi_3_1.wav"), 4 * s16, -2), (d("toms_mid_3_1.wav"), 5 * s16, -2), (d("toms_floor_3_1.wav"), 6 * s16, -1)]
    depart += [(kick, 8 * s16, 0), (crash, 8 * s16, -3)]
    sequence(depart, os.path.join(out, "depart_0.wav"), 500)
    # 주차: 킥 두 번 + 스플래시로 마무리
    sequence([(kick, 0, -1), (kick, 2 * s16, -1), (d("cymbals_splash_2_1.wav"), 2 * s16, -4)], os.path.join(out, "park_0.wav"), 1000)
    # 창문: 탐이 내려가거나(내림) 올라간다(올림)
    sequence([(d("toms_hi_2_1.wav"), 0, -2), (d("toms_mid_2_1.wav"), s16, -2), (d("toms_floor_2_1.wav"), 2 * s16, -2)], os.path.join(out, "window_down_0.wav"), 700)
    sequence([(d("toms_floor_2_1.wav"), 0, -2), (d("toms_mid_2_1.wav"), s16, -2), (d("toms_hi_2_1.wav"), 2 * s16, -2)], os.path.join(out, "window_up_0.wav"), 700)
    # 뒷좌석 탑승: 콩가 셋잇단
    sequence([(d(f"conga_conga_{k}_1.wav"), i * 110, -2) for i, k in enumerate([2, 2, 3])], os.path.join(out, "rear_seat_0.wav"), 700)
    # 한 방 소리들(변형 2개씩)
    singles = {
        "turn_left": ["exp_hihat_studio_closed_1_1", "exp_hihat_studio_closed_1_2"],
        "turn_right": ["exp_hihat_studio_closed_2_1", "exp_hihat_studio_closed_2_2"],
        "door_open": ["exp_snarekick_rimshot_snare_2_1", "exp_snarekick_rimshot_snare_2_2"],
        "door_close": ["clear_exp_snarekick_studio_kick_2_1", "clear_exp_snarekick_studio_kick_2_2"],
        "belt_on": ["clap_hit_2_1", "clap_hit_3_1"],
        "wiper_on": ["exp_shaker_double_down_1_1", "exp_shaker_double_up_1_1"],
        "reverse": ["cowbell_open_1_1", "cowbell_open_2_1"],
        "ac_on": ["cymbals_ride_2_1", "cymbals_ride_2_2"],
        "ac_off": ["cymbals_ride_1_1", "cymbals_ride_1_2"],
    }
    for ev, names in singles.items():
        for i, n in enumerate(names):
            single(d(n + ".wav"), os.path.join(out, f"{ev}_{i}.wav"))


def level(out):
    """
    모든 소리의 크기를 맞춘다. 차량 기본 볼륨이 낮아(-25dB) 작은 원본은 거의 안 들리기 때문.
    봉우리를 -1dB로 올리고, 그래도 평균이 작으면 리미터를 걸고 최대 12dB까지 더 올린다.
    """
    for f in sorted(os.listdir(out)):
        if not f.endswith(".wav"):
            continue
        path = os.path.join(out, f)
        r_ = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af", "volumedetect", "-f", "null", "-"], capture_output=True, text=True)
        val = lambda k: float([l for l in r_.stderr.splitlines() if k in l][0].split(":")[1].split()[0])
        peak, mean = val("max_volume"), val("mean_volume")
        base = -1.0 - peak
        extra = max(0.0, min(12.0, -18.0 - (mean + base)))
        tmp = path + ".tmp.wav"
        ffmpeg(["-i", path, "-af", f"volume={base + extra:.1f}dB,alimiter=limit=0.89:attack=1:release=40:level=false",
                "-ac", "1", "-ar", "44100", "-sample_fmt", "s16", tmp])
        os.replace(tmp, path)


def check_length(out, limit=1.5):
    """1.5초를 넘는 소리가 있으면 알려준다"""
    for f in sorted(os.listdir(out)):
        if not f.endswith(".wav"):
            continue
        r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", os.path.join(out, f)], capture_output=True, text=True)
        if float(r.stdout.strip() or 0) > limit:
            print("  1.5초 초과:", f, r.stdout.strip())


def main():
    for theme, fn in (("rattle", build_rattle), ("drum", build_drum)):
        out = os.path.join(OUT, theme)
        # theme.json 은 손으로 관리하므로 지우지 않고 소리만 새로 만든다
        os.makedirs(out, exist_ok=True)
        for f in os.listdir(out):
            if f.endswith(".wav"):
                os.remove(os.path.join(out, f))
        fn(out)
        print(theme, len([f for f in os.listdir(out) if f.endswith(".wav")]), "개")
        level(out)
        check_length(out)
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg 없음")


if __name__ == "__main__":
    main()
