package com.example.mcggtracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "mcgg_capture_channel"
        private const val NOTIF_ID = 1001
    }

    private enum class BattleState { IDLE, IN_BATTLE_COOLDOWN }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val analyzer = SwordAndColorAnalyzer()
    private val trackerEngine = OpponentTrackerEngine()
    private lateinit var overlayManager: FloatingOverlayManager
    private lateinit var config: TrackerConfig
    private var captureWidth = 0
    private var captureHeight = 0
    private var densityDpi = 0
    private var battleState = BattleState.IDLE

    override fun onCreate() {
        super.onCreate()
        config = TrackerConfig.load(this)
        overlayManager = FloatingOverlayManager(
            context = this,
            onResetClicked = {
                trackerEngine.reset()
                battleState = BattleState.IDLE
                overlayManager.updateState(TrackerState())
            },
            onStopClicked = { stopSelf() }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { super.onStop(); stopSelf() }
        }, Handler(Looper.getMainLooper()))

        setupVirtualDisplay()
        overlayManager.show(config)
        startPollingLoop()
        return START_NOT_STICKY
    }

    private fun updateScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        captureWidth = metrics.widthPixels / 2
        captureHeight = metrics.heightPixels / 2
        densityDpi = metrics.densityDpi
    }

    private fun setupVirtualDisplay() {
        updateScreenMetrics()
        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MCGG_Capture", captureWidth, captureHeight, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        val oldReader = imageReader
        val newReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader = newReader
        virtualDisplay?.resize(captureWidth, captureHeight, densityDpi)
        virtualDisplay?.surface = newReader.surface
        oldReader?.close()
    }

    private fun startPollingLoop() {
        scope.launch {
            while (isActive) {
                delay(450)
                val bitmap = captureLatestBitmap() ?: continue
                try {
                    if (bitmap.width <= bitmap.height) continue
                    val activePairs = analyzer.detectActiveSwordPairs(bitmap, config)
                    when (battleState) {
                        BattleState.IDLE -> {
                            if (activePairs.isNotEmpty()) {
                                delay(250)
                                val stableBitmap = captureLatestBitmap() ?: bitmap
                                val stablePairs = analyzer.detectActiveSwordPairs(stableBitmap, config)
                                if (stablePairs.isNotEmpty()) {
                                    val slots = analyzer.readPairedSlots(stableBitmap, config, stablePairs)
                                    val newState = trackerEngine.processBattleSnapshot(slots)
                                    overlayManager.updateState(newState)
                                    battleState = BattleState.IN_BATTLE_COOLDOWN
                                }
                                if (stableBitmap !== bitmap) stableBitmap.recycle()
                            }
                        }
                        BattleState.IN_BATTLE_COOLDOWN -> {
                            if (activePairs.isEmpty()) battleState = BattleState.IDLE
                        }
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
    }

    private fun captureLatestBitmap(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * image.width
            val raw = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            val clean = Bitmap.createBitmap(raw, 0, 0, image.width, image.height)
            raw.recycle()
            clean
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "MCGG Screen Tracker", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCGG Tracker Aktif")
            .setContentText("Memantau ikon pedang di sidebar kanan...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        analyzer.close()
        overlayManager.hide()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
