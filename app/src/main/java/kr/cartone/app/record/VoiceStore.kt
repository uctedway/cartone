package kr.cartone.app.record

import android.content.Context
import java.io.File
import kr.cartone.app.core.CarEvent

/**
 * 직접 녹음한 목소리 보관함. 차 안(앱 전용 저장소)에만 저장하고 밖으로 보내지 않는다.
 * - 테마마다 따로 보관: files/voices/<테마id>/<이벤트키>.wav (다른 테마의 같은 이벤트 녹음을 덮어쓰지 않게)
 * - 녹음 중 임시 파일과, 녹음이 끝났지만 아직 어느 테마에 넣을지 정하기 전 파일은 voices/ 바로 아래에 둔다
 */
class VoiceStore(context: Context) {
    private val dir = File(context.filesDir, "voices").apply { mkdirs() }

    /** 테마의 이벤트 녹음 파일 위치 */
    fun fileFor(themeId: String, e: CarEvent) = File(File(dir, themeId).apply { mkdirs() }, "${e.key}.wav")

    /** 그 녹음이 실제로 있는지(헤더 44바이트뿐인 빈 파일은 없는 것으로 본다) */
    fun has(themeId: String, e: CarEvent) = fileFor(themeId, e).let { it.exists() && it.length() > 44 }

    /** 녹음 중 임시 파일. 취소되면 지운다 */
    fun tempFor(e: CarEvent) = File(dir, "${e.key}.recording")

    /** 녹음이 끝난 파일(아직 테마에 넣기 전) */
    fun doneFor(e: CarEvent) = File(dir, "${e.key}.new.wav")

    /** 녹음이 끝난 파일을 테마 보관함으로 옮긴다 */
    fun moveDone(e: CarEvent, themeId: String): Boolean {
        val target = fileFor(themeId, e)
        target.delete()
        return doneFor(e).renameTo(target)
    }

    /** 테마를 지우면 그 테마의 녹음도 함께 지운다 */
    fun deleteTheme(themeId: String) = File(dir, themeId).deleteRecursively()
}
