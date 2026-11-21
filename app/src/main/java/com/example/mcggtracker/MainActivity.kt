package com.example.mcggtracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var config: TrackerConfig

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Tracker aktif! Buka game MCGG.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Izin rekam layar ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = TrackerConfig.load(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply { text = "MCGG Opponent Tracker"; textSize = 20f })
        root.addView(Button(this).apply { text = "1. Mulai Screen Tracker"; setOnClickListener { startTrackerFlow() } })
        root.addView(Button(this).apply {
            text = "Hentikan Tracker"
            setOnClickListener { stopService(Intent(this@MainActivity, ScreenCaptureService::class.java)) }
        })

        addSlider(root, "Posisi Kiri Nama (${(config.nameLeftPct * 100).toInt()}%)", 70, 92, (config.nameLeftPct * 100).toInt()) {
            config.nameLeftPct = it / 100f; config.save(this)
        }
        addSlider(root, "Posisi Atas Slot 1 (${(config.firstSlotTopPct * 100).toInt()}%)", 5, 25, (config.firstSlotTopPct * 100).toInt()) {
            config.firstSlotTopPct = it / 100f; config.save(this)
        }
        addSlider(root, "Jarak Antar Slot (${(config.slotStepPct * 1000).toInt()})", 70, 120, (config.slotStepPct * 1000).toInt()) {
            config.slotStepPct = it / 1000f; config.save(this)
        }
        addSlider(root, "Posisi Pedang Kanan (${(config.swordLeftPct * 100).toInt()}%)", 92, 99, (config.swordLeftPct * 100).toInt()) {
            config.swordLeftPct = it / 100f; config.save(this)
        }
        setContentView(root)
    }

    private fun startTrackerFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Display over other apps' dulu.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun addSlider(parent: LinearLayout, label: String, minVal: Int, maxVal: Int, current: Int, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply { text = label; setPadding(0, 24, 0, 4) }
        val sb = SeekBar(this).apply {
            max = maxVal - minVal
            progress = (current - minVal).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = minVal + p
                        tv.text = label.substringBefore("(") + "($value)"
                        onChange(value)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(tv); parent.addView(sb)
    }
}
