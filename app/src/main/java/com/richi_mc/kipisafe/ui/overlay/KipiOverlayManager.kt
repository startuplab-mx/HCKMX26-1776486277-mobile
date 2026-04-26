package com.richi_mc.kipisafe.ui.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.richi_mc.kipisafe.R
import kotlin.math.abs

/**
 * Muestra un overlay flotante tipo "Chat Head" (Burbuja) con los mensajes de Kipi.
 */
class KipiOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Asegura que la burbuja sea visible.
     */
    fun ensureBubbleVisible() {
        if (overlayView != null) return
        mainHandler.post { createOverlay() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        val themed = ContextThemeWrapper(context, R.style.Theme_KipiSafe)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_kipi_bubble, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        this.params = layoutParams

        val bubble = view.findViewById<View>(R.id.kipi_bubble)
        val dismissButton = view.findViewById<View>(R.id.button_dismiss_message)

        // Animación de entrada con rebote (Spring effect)
        bubble.scaleX = 0f
        bubble.scaleY = 0f
        bubble.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(2f))
            .start()

        // Variables para el arrastre
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        dismissButton.setOnClickListener {
            hideMessageWithAnimation()
        }

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {
            overlayView = null
        }
    }

    /**
     * Actualiza el globo de texto con un mensaje de advertencia y cambia el color del círculo.
     */
    fun showKipiAdvice(message: String, riskLevel: Int = 1) {
        mainHandler.post {
            if (overlayView == null) {
                createOverlay()
            }

            val view = overlayView ?: return@post
            val messageContainer = view.findViewById<View>(R.id.kipi_message_container)
            val textView = view.findViewById<TextView>(R.id.text_kipi_advice)
            val bubbleContainer = view.findViewById<CardView>(R.id.kipi_bubble_container)

            textView.text = message

            // Actualizar el color del círculo según el nivel de riesgo
            val color = when (riskLevel) {
                1 -> Color.parseColor("#00687B") // Informativo
                2 -> Color.parseColor("#F59E0B") // Riesgo Medio (Naranja/Amarillo)
                3 -> Color.parseColor("#EF4444") // Riesgo Alto (Rojo)
                else -> Color.parseColor("#00687B")
            }
            bubbleContainer.setCardBackgroundColor(color)

            // Animación de aparición (Fade + Slide)
            messageContainer.visibility = View.VISIBLE
            messageContainer.alpha = 0f
            messageContainer.translationX = -20f

            messageContainer.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(500)
                .setListener(null)
                .start()

            // Ya no se auto-oculta para que el niño pueda leerlo con calma
            // El niño debe usar el botón 'X'
        }
    }

    private fun hideMessageWithAnimation() {
        val view = overlayView ?: return
        val messageContainer = view.findViewById<View>(R.id.kipi_message_container)

        if (messageContainer.visibility == View.GONE) return

        messageContainer.animate()
            .alpha(0f)
            .translationX(-20f)
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    messageContainer.visibility = View.GONE

                    mainHandler.postDelayed({
                        // Solo la eliminamos si el contenedor sigue oculto
                        // (por si llegó un mensaje nuevo en ese lapso)
                        if (messageContainer.visibility == View.GONE) {
                            removeOverlayWithAnimation()
                        }
                    }, 4000)
                }
            })
            .start()
    }

    private fun removeOverlayWithAnimation() {
        val view = overlayView ?: return
        val bubble = view.findViewById<View>(R.id.kipi_bubble)

        bubble.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(500)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeOverlay() // Llama a tu función existente que hace el removeView
                }
            })
            .start()
    }

    fun removeOverlay() {
        mainHandler.post {
            val view = overlayView ?: return@post
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
            } finally {
                overlayView = null
            }
        }
    }
}
