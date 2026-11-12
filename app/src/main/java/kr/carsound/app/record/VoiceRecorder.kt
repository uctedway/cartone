package kr.carsound.app.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kr.carsound.app.core.CarEvent

/**
 * 이벤트 하나에 걸 짧은 목소리를 녹음한다(최대 3초, 모노 44.1kHz 16bit WAV).
 * - stop(): 저장하고 콜백으로 알린다.
 * - cancel(): 녹음하던 파일을 버린다. 차가 출발하면 ParkGate가 이걸 부른다.
 */
class VoiceRecorder(private val store: VoiceStore) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var keep = false
    private var thread: Thread? = null

    /** 진행률(0~1)과 소리 크기(0~1)를 화면에 보여주기 위한 콜백 */
    var onProgress: ((Float, Float) -> Unit)? = null

    /** 녹음이 끝났을 때(저장됐으면 true) */
    var onFinished: ((CarEvent, Boolean) -> Unit)? = null

    val isRecording get() = running

    /** 녹음 시작. 별도 스레드에서 마이크를 읽어 임시 파일에 쓴다 */
    @SuppressLint("MissingPermission") // 권한은 화면에서 먼저 확인한다
    fun start(e: CarEvent) {
        if (running) return
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096))
        val tmp = store.tempFor(e)
        running = true; keep = false
        thread = Thread {
            val buf = ShortArray(2048)
            val bytes = ByteArray(buf.size * 2)
            var total = 0
            FileOutputStream(tmp).use { out ->
                out.write(ByteArray(44)) // 헤더 자리. 끝에서 채운다
                rec.startRecording()
                while (running && total < RATE * MAX_SEC) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var peak = 0
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        bytes[i * 2] = (s and 0xff).toByte(); bytes[i * 2 + 1] = (s shr 8 and 0xff).toByte()
                        if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
                    }
                    out.write(bytes, 0, n * 2)
                    total += n
                    val p = total / (RATE * MAX_SEC).toFloat()
                    main.post { onProgress?.invoke(p, peak / 32768f) }
                }
                rec.stop(); rec.release()
            }
            // 시간이 다 차서 끝난 경우도 저장으로 본다
            val save = keep || running
            running = false
            finish(e, tmp, total, save)
        }.apply { start() }
    }

    /** 녹음을 마치고 저장한다 */
    fun stop() { keep = true; running = false }

    /** 녹음을 버린다(차량 출발 등) */
    fun cancel() { keep = false; running = false }

    /** 녹음 스레드가 끝난 뒤 정리: 저장할지 버릴지 정하고 메인 스레드로 결과를 알린다 */
    private fun finish(e: CarEvent, tmp: File, samples: Int, save: Boolean) {
        val ok = save && samples > RATE / 5 // 0.2초보다 짧으면 버린다
        if (ok) {
            writeHeader(tmp, samples)
            // 어느 테마에 넣을지는 앱이 정한다(내장 테마면 복사본을 만들고 거기에 넣음)
            store.doneFor(e).delete()
            tmp.renameTo(store.doneFor(e))
        } else tmp.delete()
        main.post { onFinished?.invoke(e, ok) }
    }

    /** 표준 WAV 헤더(PCM 16bit 모노)를 파일 앞 44바이트에 쓴다 */
    private fun writeHeader(f: File, samples: Int) {
        val data = samples * 2
        RandomAccessFile(f, "rw").use { raf ->
            fun i32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun i16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            raf.seek(0)
            raf.write("RIFF".toByteArray()); raf.write(i32(36 + data)); raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray()); raf.write(i32(16)); raf.write(i16(1)); raf.write(i16(1))
            raf.write(i32(RATE)); raf.write(i32(RATE * 2)); raf.write(i16(2)); raf.write(i16(16))
            raf.write("data".toByteArray()); raf.write(i32(data))
        }
    }

    companion object {
        const val RATE = 44100
        const val MAX_SEC = 3
    }
}
