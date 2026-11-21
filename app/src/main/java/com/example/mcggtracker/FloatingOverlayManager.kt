package com.example.mcggtracker

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

class FloatingOverlayManager(
    private val context: Context,
    private val onResetClicked: () -> Unit,
    private val onStopClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panelView: LinearLayout? = null
    private var calibrationView: CalibrationOverlayView? = null
    private lateinit var tvHeader: TextView
    private lateinit var tvCurrent: TextView
    private lateinit var tvNextList: TextView
    private var showCalibration = false

    fun show(config: TrackerConfig) {
        if (panelView != null) return
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 140
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            background = GradientDrawable().apply {
                setColor(Color.argb(215, 18, 22, 32))
                cornerRadius = 24f
                setStroke(2, Color.argb(180, 90, 220, 140))
            }
        }

        tvHeader = TextView(context).apply {
            text = "MCGG Tracker (Standby)"
            setTextColor(Color.parseColor("#80FF9E"))
            textSize = 12f
        }
        tvCurrent = TextView(context).apply {
            text = "Lawan ini: -"
            setTextColor(Color.YELLOW)
            textSize = 13f
            setPadding(0, 6, 0, 4)
        }
        tvNextList = TextView(context).apply {
            text = "Kandidat Next:\n-"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, 0, 10)
        }

        val btnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val btnReset = Button(context).apply {
            text = "Reset"; textSize = 10f; setPadding(16, 4, 16, 4)
            setOnClickListener { onResetClicked() }
        }
        val btnCalib = Button(context).apply {
            text = "Kotak"; textSize = 10f; setPadding(16, 4, 16, 4)
            setOnClickListener {
                showCalibration = !showCalibration
                toggleCalibrationBox(config)
            }
        }
        val btnStop = Button(context).apply {
            text = "Stop"; textSize = 10f; setPadding(16, 4, 16, 4)
            setOnClickListener { onStopClicked() }
        }
        btnRow.addView(btnReset); btnRow.addView(btnCalib); btnRow.addView(btnStop)

        root.addView(tvHeader); root.addView(tvCurrent); root.addView(tvNextList); root.addView(btnRow)

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0; private var initY = 0
            private var touchX = 0f; private var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = layoutParams.x; initY = layoutParams.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initX + (event.rawX - touchX).toInt()
                        layoutParams.y = initY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(root, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        panelView = root
        windowManager.addView(root, layoutParams)
    }

    fun updateState(state: TrackerState) {
        panelView?.post {
            tvHeader.text = "Ronde #${state.roundCount} | Kamu: ${state.selfName}"
            tvCurrent.text = "Vs Sekarang: ${state.currentOpponent}"
            val nextText = if (state.nextCandidates.isEmpty()) "Belum ada data"
            else state.nextCandidates.joinToString("\n") { "• $it" }
            tvNextList.text = "Kandidat Next (${state.nextCandidates.size}):\n$nextText"
        }
    }

    private fun toggleCalibrationBox(config: TrackerConfig) {
        if (showCalibration) {
            if (calibrationView == null) {
                calibrationView = CalibrationOverlayView(context, config)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                windowManager.addView(calibrationView, params)
            }
        } else {
            calibrationView?.let { windowManager.removeView(it); calibrationView = null }
        }
    }

    fun hide() {
        showCalibration = false
        calibrationView?.let { windowManager.removeView(it) }; calibrationView = null
        panelView?.let { windowManager.removeView(it) }; panelView = null
    }

    private class CalibrationOverlayView(context: Context, private val config: TrackerConfig) : View(context) {
        private val namePaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 3f }
        private val swordPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= height) return
            for (i in 0..7) canvas.drawRect(config.getNameBox(i, width, height), namePaint)
            for (p in 0..3) canvas.drawRect(config.getSwordBox(p, width, height), swordPaint)
        }
    }
}
