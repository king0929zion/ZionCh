package com.zionchat.app.autosoul.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.zionchat.app.R
import com.zionchat.app.autosoul.runtime.AutoSoulStatusMode
import com.zionchat.app.autosoul.runtime.AutoSoulStatusState

object AutoSoulFloatingOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var statusDotView: View? = null
    private var statusTextView: TextView? = null
    private var stopIconView: ImageView? = null
    private var sendIconView: ImageView? = null
    private var actionButtonView: View? = null
    private var pulseAnimator: ValueAnimator? = null

    private var currentState = AutoSoulStatusState()
    private var onStopAction: (() -> Unit)? = null
    private var onSendAction: (() -> Unit)? = null

    fun isShowing(): Boolean = overlayView != null

    fun setActionCallbacks(
        onStop: () -> Unit,
        onSend: () -> Unit
    ) {
        onStopAction = onStop
        onSendAction = onSend
    }

    fun show(context: Context) {
        runOnMain {
            val appContext = context.applicationContext
            if (!Settings.canDrawOverlays(appContext)) {
                Toast.makeText(appContext, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@runOnMain
            }
            if (overlayView != null) {
                applyState(currentState)
                return@runOnMain
            }

            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val display = appContext.resources.displayMetrics
            val initialX = (display.widthPixels * 0.5f - 130f.dpPx(appContext)).toInt().coerceAtLeast(0)
            val initialY = (display.heightPixels * 0.72f).toInt().coerceAtLeast(0)

            val params =
                WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.TOP or Gravity.START
                    x = initialX
                    y = initialY
                    type =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_PHONE
                        }
                    flags =
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
                }
            layoutParams = params

            val view = LayoutInflater.from(appContext).inflate(R.layout.overlay_autosoul_control, null, false)
            overlayView = view
            statusDotView = view.findViewById(R.id.autosoulStatusDot)
            statusTextView = view.findViewById(R.id.autosoulStatusText)
            stopIconView = view.findViewById(R.id.autosoulStopIcon)
            sendIconView = view.findViewById(R.id.autosoulSendIcon)
            actionButtonView = view.findViewById(R.id.autosoulActionButton)

            actionButtonView?.setOnClickListener {
                if (currentState.stopped) {
                    onSendAction?.invoke()
                } else {
                    onStopAction?.invoke()
                }
            }

            attachDragListener(view, params)
            runCatching { wm.addView(view, params) }.onFailure { error ->
                cleanupOverlay()
                Toast.makeText(appContext, "悬浮窗创建失败：${error.message}", Toast.LENGTH_SHORT).show()
                return@runOnMain
            }

            applyState(currentState)
        }
    }

    fun hide() {
        runOnMain {
            stopPulse()
            val wm = windowManager
            val view = overlayView
            cleanupOverlay()
            if (wm != null && view != null) {
                runCatching { wm.removeView(view) }
            }
        }
    }

    fun updateState(state: AutoSoulStatusState) {
        currentState = state
        runOnMain { applyState(state) }
    }

    private fun applyState(state: AutoSoulStatusState) {
        statusTextView?.text = state.text
        stopIconView?.visibility = if (state.stopped) View.GONE else View.VISIBLE
        sendIconView?.visibility = if (state.stopped) View.VISIBLE else View.GONE
        val color =
            when (state.mode) {
                AutoSoulStatusMode.RUNNING -> 0xFF10B981.toInt()
                AutoSoulStatusMode.RECOGNIZING -> 0xFF3B82F6.toInt()
                AutoSoulStatusMode.THINKING -> 0xFF8B5CF6.toInt()
                AutoSoulStatusMode.STOPPED -> 0xFFEF4444.toInt()
            }
        statusDotView?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        if (state.stopped) stopPulse() else startPulse()
    }

    private fun startPulse() {
        val dot = statusDotView ?: return
        if (pulseAnimator != null) return
        pulseAnimator =
            ValueAnimator.ofFloat(1f, 0.72f, 1f).apply {
                duration = 1500L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    dot.scaleX = value
                    dot.scaleY = value
                    dot.alpha = 0.55f + value * 0.45f
                }
                start()
            }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        statusDotView?.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }

    private fun attachDragListener(
        root: View,
        params: WindowManager.LayoutParams
    ) {
        val dragTarget = statusTextView ?: root
        dragTarget.setOnTouchListener(
            object : View.OnTouchListener {
                private var downRawX = 0f
                private var downRawY = 0f
                private var startX = 0
                private var startY = 0

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val wm = windowManager ?: return false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            startX = params.x
                            startY = params.y
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = (event.rawX - downRawX).toInt()
                            val deltaY = (event.rawY - downRawY).toInt()
                            params.x = startX + deltaX
                            params.y = startY + deltaY
                            runCatching { wm.updateViewLayout(root, params) }
                            return true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> return true
                        else -> return false
                    }
                }
            }
        )
    }

    private fun cleanupOverlay() {
        overlayView = null
        layoutParams = null
        windowManager = null
        statusDotView = null
        statusTextView = null
        stopIconView = null
        sendIconView = null
        actionButtonView = null
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { runCatching { block() } }
        }
    }

    private fun Float.dpPx(context: Context): Float {
        return this * context.resources.displayMetrics.density
    }
}
