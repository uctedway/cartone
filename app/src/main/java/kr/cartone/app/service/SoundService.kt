package kr.cartone.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kr.cartone.app.CarToneApp
import kr.cartone.app.R
import kr.cartone.app.ui.MainActivity

/**
 * 앱 화면이 뒤에 있거나 주행 중이어도 차량 이벤트를 받아 소리를 내도록 살아 있는 서비스.
 * 실제 처리는 CarToneApp의 부품들이 하고, 서비스는 프로세스를 유지하는 역할만 한다.
 */
class SoundService : Service() {

    /** 상시 알림을 띄워 포그라운드 서비스로 올리고 차량 이벤트 감시를 켠다 */
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "카톤 실행 중", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("차량 이벤트에 소리를 입히는 중")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(open)
            .build()
        // API 29부터는 서비스 종류(미디어 재생)를 함께 알려야 한다
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
        (application as CarToneApp).startWatching()
    }

    // 시스템이 서비스를 내렸다가도 다시 살리도록 한다
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    /** 서비스가 내려가면 감시도 끈다 */
    override fun onDestroy() {
        (application as CarToneApp).stopWatching()
        super.onDestroy()
    }

    /** 바인딩은 쓰지 않는다 */
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "run"
        /** 화면이 열릴 때 서비스를 켠다 */
        fun start(c: Context) = c.startForegroundService(Intent(c, SoundService::class.java))
    }
}
