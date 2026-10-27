package odon.utils.oskeyboarddisabler

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

class FloatingButton(
    private val context: Context,
    private val onTap: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val sizePx = (BUTTON_SIZE_DP * density).toInt()
    private val edgeThresholdPx = (EDGE_THRESHOLD_DP * density).toInt()
    private val visiblePx = (VISIBLE_DP * density).toInt()
    private val marginPx = (4 * density).toInt()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())
    private val tuckRunnable = Runnable { tuck() }
    private var animator: ValueAnimator? = null

    private var imageView: ImageView? = null
    private var circle: GradientDrawable? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(hidden: Boolean) {
        if (imageView != null) {
            setHidden(hidden)
            return
        }

        val metrics = context.resources.displayMetrics
        val pad = (PADDING_DP * density).toInt()

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorFor(hidden))
        }

        val view = ImageView(context).apply {
            setPadding(pad, pad, pad, pad)
            background = bg
            contentDescription = context.getString(R.string.button_toggle)
            setImageResource(iconFor(hidden))
            setOnClickListener { onTap() }
            setOnTouchListener(touchListener)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val lp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, metrics.widthPixels - sizePx - (8 * density).toInt())
            y = prefs.getInt(KEY_Y, metrics.heightPixels / 3)
        }
        clamp(lp)

        windowManager.addView(view, lp)
        imageView = view
        circle = bg
        params = lp

        scheduleTuck()
    }

    fun setHidden(hidden: Boolean) {
        imageView?.setImageResource(iconFor(hidden))
        circle?.setColor(colorFor(hidden))
    }

    fun keepOnScreen() {
        val view = imageView ?: return
        val lp = params ?: return
        clamp(lp)
        windowManager.updateViewLayout(view, lp)
        scheduleTuck()
    }

    fun remove() {
        handler.removeCallbacks(tuckRunnable)
        animator?.cancel()
        imageView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        imageView = null
        circle = null
        params = null
    }

    private fun iconFor(hidden: Boolean) =
    if (hidden) R.drawable.ic_no_keyboard else R.drawable.ic_keyboard

    private fun colorFor(hidden: Boolean) =
    if (hidden) COLOR_HIDDEN else COLOR_ALLOWED

    private fun clamp(lp: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        lp.x = lp.x.coerceIn(visiblePx - sizePx, metrics.widthPixels - visiblePx)
        lp.y = lp.y.coerceIn(visiblePx - sizePx, (metrics.heightPixels - sizePx).coerceAtLeast(0))
    }

    private fun scheduleTuck() {
        handler.removeCallbacks(tuckRunnable)
        handler.postDelayed(tuckRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun tuckTarget(lp: WindowManager.LayoutParams): Pair<Int, Int>? {
        val width = context.resources.displayMetrics.widthPixels
        var x = lp.x
        var y = lp.y
        var near = false

        if (lp.x <= edgeThresholdPx) {
            x = visiblePx - sizePx
            near = true
        } else if (lp.x >= width - sizePx - edgeThresholdPx) {
            x = width - visiblePx
            near = true
        }
        if (lp.y <= edgeThresholdPx) {
            y = visiblePx - sizePx
            near = true
        }
        return if (near) Pair(x, y) else null
    }

    private fun tuck() {
        val view = imageView ?: return
        val lp = params ?: return
        val target = tuckTarget(lp) ?: return

        val startX = lp.x
        val startY = lp.y
        val startAlpha = view.alpha

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TUCK_DURATION_MS
            addUpdateListener {
                val t = it.animatedFraction
                lp.x = (startX + (target.first - startX) * t).toInt()
                lp.y = (startY + (target.second - startY) * t).toInt()
                view.alpha = startAlpha + (TUCKED_ALPHA - startAlpha) * t
                windowManager.updateViewLayout(view, lp)
            }
            start()
        }
    }

    private fun reveal(lp: WindowManager.LayoutParams) {
        val width = context.resources.displayMetrics.widthPixels
        if (lp.x < 0) lp.x = marginPx
        if (lp.x > width - sizePx) lp.x = width - sizePx - marginPx
        if (lp.y < 0) lp.y = marginPx
    }

    private val touchListener = object : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(tuckRunnable)
                    animator?.cancel()
                    v.alpha = 1f
                    reveal(lp)
                    windowManager.updateViewLayout(v, lp)

                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        clamp(lp)
                        windowManager.updateViewLayout(v, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        prefs.edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                    } else {
                        v.performClick()
                    }
                    scheduleTuck()
                }
                MotionEvent.ACTION_CANCEL -> scheduleTuck()
            }
            return true
        }
    }

    companion object {
        const val BUTTON_SIZE_DP = 40
        private const val PADDING_DP = 5
        private const val EDGE_THRESHOLD_DP = 24
        private const val VISIBLE_DP = 12
        private const val AUTO_HIDE_DELAY_MS = 1500L
        private const val TUCKED_ALPHA = 0.5f
        private const val TUCK_DURATION_MS = 200L
        private val COLOR_ALLOWED = Color.parseColor("#CC2E7D32") // keyboard allowed: green
        private val COLOR_HIDDEN = Color.parseColor("#CCC62828")  // keyboard hidden: red
        private const val PREFS = "keyboard_toggle"
        private const val KEY_X = "button_x"
        private const val KEY_Y = "button_y"
    }
}
