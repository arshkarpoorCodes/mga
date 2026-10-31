package com.example.myboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

enum class Layer { LETTERS, SYM1, SYM2 }
enum class Shift { OFF, ON, LOCK }
enum class Kind { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, TO_LETTERS, TO_SYM1, TO_SYM2, SPACER }

class Key(val label: String, val kind: Kind = Kind.CHAR, val weight: Float = 1f) {
    val rect = RectF()
}

private class Theme(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int, val pressed: Int)

private val LIGHT = Theme(0xFFE8EAED.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9CDD3.toInt(), 0xFF1B1B1F.toInt(), 0xFF3B82F6.toInt(), 0xFFB8BDC6.toInt())
private val DARK = Theme(0xFF1F2023.toInt(), 0xFF3A3C42.toInt(), 0xFF2B2D32.toInt(), 0xFFECEDEF.toInt(), 0xFF5B9BFF.toInt(), 0xFF5A5D66.toInt())

class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
    }

    private val density = resources.displayMetrics.density
    private val rowHeight = 52f * density
    private val gap = 3f * density
    private val corner = 8f * density
    private val rows = 5

    private val layouts = mapOf(Layer.LETTERS to letters(), Layer.SYM1 to sym1(), Layer.SYM2 to sym2())
    private var layer = Layer.LETTERS
    private var shift = Shift.OFF
    private var lastShiftTap = 0L

    private val pressed = HashSet<Key>()
    private val pointers = SparseArray<Key>()
    private val ui = Handler(Looper.getMainLooper())
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val tmp = RectF()

    private val repeatDelete = object : Runnable {
        override fun run() {
            listener.onBackspace()
            ui.postDelayed(this, 45)
        }
    }

    // ---- layouts ----
    private fun chars(s: String) = s.map { Key(it.toString()) }
    private fun spacer(w: Float) = Key("", Kind.SPACER, w)

    private fun letters() = listOf(
        chars("1234567890"),
        chars("qwertyuiop"),
        listOf(spacer(0.5f)) + chars("asdfghjkl") + spacer(0.5f),
        listOf(Key("⇧", Kind.SHIFT, 1.5f)) + chars("zxcvbnm") + Key("⌫", Kind.BACKSPACE, 1.5f),
        listOf(Key("?123", Kind.TO_SYM1, 1.5f), Key(","), Key(" ", Kind.SPACE, 5f), Key("."), Key("⏎", Kind.ENTER, 1.5f))
    )

    private fun sym1() = listOf(
        chars("1234567890"),
        chars("@#\$%&-+()/"),
        listOf(spacer(0.5f)) + chars("~*\"':;!?_") + spacer(0.5f),
        listOf(Key("=\\<", Kind.TO_SYM2, 1.5f)) + chars("^`|{}[]") + Key("⌫", Kind.BACKSPACE, 1.5f),
        listOf(Key("ABC", Kind.TO_LETTERS, 1.5f), Key(","), Key(" ", Kind.SPACE, 5f), Key("."), Key("⏎", Kind.ENTER, 1.5f))
    )

    private fun sym2() = listOf(
        chars("1234567890"),
        chars("£€¥°•√π÷×¶"),
        listOf(spacer(0.5f)) + chars("©®™✓<>=\\§") + spacer(0.5f),
        listOf(Key("?123", Kind.TO_SYM1, 1.5f)) + chars("≈≠±µ¿¡…") + Key("⌫", Kind.BACKSPACE, 1.5f),
        listOf(Key("ABC", Kind.TO_LETTERS, 1.5f), Key(","), Key(" ", Kind.SPACE, 5f), Key("."), Key("⏎", Kind.ENTER, 1.5f))
    )

    // ---- public state ----
    fun setLayer(l: Layer) {
        layer = l
        invalidate()
    }

    fun autoShift(on: Boolean) {
        if (shift == Shift.LOCK) return
        val n = if (on) Shift.ON else Shift.OFF
        if (n != shift) {
            shift = n
            invalidate()
        }
    }

    // ---- measuring ----
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (rows * rowHeight + 6f * density).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        for (rowsList in layouts.values) {
            rowsList.forEachIndexed { r, row ->
                val total = row.sumOf { it.weight.toDouble() }.toFloat()
                var x = 0f
                val top = r * rowHeight
                for (k in row) {
                    val kw = w * k.weight / total
                    k.rect.set(x, top, x + kw, top + rowHeight)
                    x += kw
                }
            }
        }
    }

    // ---- drawing ----
    override fun onDraw(canvas: Canvas) {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val t = if (dark) DARK else LIGHT
        canvas.drawColor(t.bg)
        val half = gap / 2f

        for (row in layouts.getValue(layer)) {
            for (k in row) {
                if (k.kind == Kind.SPACER) continue
                tmp.set(k.rect.left + half, k.rect.top + half, k.rect.right - half, k.rect.bottom - half)

                val isSpecial = k.kind != Kind.CHAR && k.kind != Kind.SPACE
                val shiftActive = k.kind == Kind.SHIFT && shift != Shift.OFF
                fillPaint.color = when {
                    k in pressed -> t.pressed
                    shiftActive -> t.accent
                    isSpecial -> t.special
                    else -> t.key
                }
                canvas.drawRoundRect(tmp, corner, corner, fillPaint)

                if (k.kind == Kind.SPACE) continue
                var label = k.label
                if (k.kind == Kind.CHAR && layer == Layer.LETTERS && shift != Shift.OFF) label = label.uppercase()
                if (k.kind == Kind.SHIFT && shift == Shift.LOCK) label = "⇪"

                textPaint.color = if (shiftActive) 0xFFFFFFFF.toInt() else t.text
                textPaint.textSize = (if (label.length > 1) 16f else 22f) * density
                val y = tmp.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, tmp.centerX(), y, textPaint)
            }
        }
    }

    // ---- touch ----
    private fun keyAt(x: Float, y: Float): Key? {
        for (row in layouts.getValue(layer)) {
            for (k in row) {
                if (k.kind != Kind.SPACER && k.rect.contains(x, y)) return k
            }
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = keyAt(e.getX(i), e.getY(i))
                if (k != null) {
                    pointers.put(e.getPointerId(i), k)
                    pressed.add(k)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    handleKey(k)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                val k = pointers.get(id)
                if (k != null) {
                    pressed.remove(k)
                    pointers.remove(id)
                    if (k.kind == Kind.BACKSPACE) ui.removeCallbacks(repeatDelete)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointers.clear()
                pressed.clear()
                ui.removeCallbacks(repeatDelete)
            }
        }
        invalidate()
        return true
    }

    private fun handleKey(k: Key) {
        when (k.kind) {
            Kind.CHAR -> {
                var s = k.label
                if (layer == Layer.LETTERS && shift != Shift.OFF) s = s.uppercase()
                if (shift == Shift.ON) shift = Shift.OFF
                listener.onText(s)
            }
            Kind.SPACE -> listener.onText(" ")
            Kind.BACKSPACE -> {
                listener.onBackspace()
                ui.postDelayed(repeatDelete, 380)
            }
            Kind.ENTER -> listener.onEnter()
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == Shift.LOCK -> Shift.OFF
                    shift == Shift.ON && now - lastShiftTap < 350 -> Shift.LOCK
                    shift == Shift.ON -> Shift.OFF
                    else -> Shift.ON
                }
                lastShiftTap = now
            }
            Kind.TO_LETTERS -> layer = Layer.LETTERS
            Kind.TO_SYM1 -> layer = Layer.SYM1
            Kind.TO_SYM2 -> layer = Layer.SYM2
            Kind.SPACER -> {}
        }
    }

    override fun onDetachedFromWindow() {
        ui.removeCallbacks(repeatDelete)
        super.onDetachedFromWindow()
    }
}
