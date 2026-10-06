/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.roundToInt

class ScrollingChildEditText : AppCompatEditText {
    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        init()
    }

    private var isUpdatingTabSpans = false

    private var cursorOffsetToReveal = -1

    private val cursorRevealRect = Rect()

    private var basePaddingLeft = -1

    private val lineNumberGap by lazy { 8f * resources.displayMetrics.density }

    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    private fun init() {
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        updateLineNumberGutter()

        addTextChangedListener(object : TextWatcher {
            private var changeStart = 0
            private var changeAfter = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                changeStart = start
                changeAfter = after
            }

            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable?) {
                updateLineNumberGutter()
                if (s == null || isUpdatingTabSpans) return
                isUpdatingTabSpans = true
                try {
                    val end = (changeStart + changeAfter).coerceAtMost(s.length)
                    val existingSpans = s.getSpans(changeStart, end, TabReplacementSpan::class.java)
                    for (span in existingSpans) {
                        s.removeSpan(span)
                    }
                    for (i in changeStart until end) {
                        if (s[i] == '\t') {
                            s.setSpan(
                                TabReplacementSpan(4),
                                i,
                                i + 1,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                } finally {
                    isUpdatingTabSpans = false
                }
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB && event.action == KeyEvent.ACTION_DOWN) {
            val start = selectionStart
            val end = selectionEnd
            if (start >= 0 && end >= 0) {
                text?.replace(minOf(start, end), maxOf(start, end), "\t")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)

        if (selEnd >= 0) {
            cursorOffsetToReveal = selEnd
        }
    }

    // onMeasure() calls registerForPreDraw() and onPreDraw() calls bringPointIntoView(), which
    // results in unwanted scroll when the keyboard is shown or hidden. Follow the cursor only
    // after it moves, and clear the keyboard inset so the new line stays above it.
    override fun onPreDraw(): Boolean {
        val offset = cursorOffsetToReveal
        val textLayout = layout
        if (offset >= 0 && textLayout != null && !isLayoutRequested) {
            cursorOffsetToReveal = -1
            revealCursor(textLayout, offset)
        }
        return true
    }

    private fun revealCursor(textLayout: Layout, offset: Int) {
        if (offset > textLayout.text.length) {
            return
        }
        val line = textLayout.getLineForOffset(offset)
        val lineTop = textLayout.getLineTop(line)
        val lineBottom = textLayout.getLineBottom(line)
        cursorRevealRect.set(0, lineTop, width, lineBottom)
        cursorRevealRect.offset(0, extendedPaddingTop)
        val scrollView = parent as? View
        if (scrollView != null) {
            cursorRevealRect.bottom += scrollView.paddingBottom + (lineBottom - lineTop)
        }
        requestRectangleOnScreen(cursorRevealRect, true)
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        val content = text
        if (textLayout != null && content != null && content.length <= MAX_LINE_NUMBER_TEXT_LENGTH) {
            lineNumberPaint.color = currentHintTextColor
            lineNumberPaint.textSize = textSize
            lineNumberPaint.typeface = typeface
            val x = paddingLeft - lineNumberGap
            // Only the lines in view are drawn, but numbering has to count from the first line.
            val clip = canvas.clipBounds
            val firstVisibleLine = textLayout.getLineForVertical(
                (clip.top - extendedPaddingTop).coerceAtLeast(0)
            )
            val lastVisibleLine = textLayout.getLineForVertical(
                (clip.bottom - extendedPaddingTop).coerceAtLeast(0)
            )
            var logicalLine = 1
            for (i in 0..lastVisibleLine.coerceAtMost(textLayout.lineCount - 1)) {
                val start = textLayout.getLineStart(i)
                val isLogicalStart = start == 0 || content[start - 1] == '\n' ||
                    content[start - 1] == '\r'
                if (!isLogicalStart) {
                    continue
                }
                if (i >= firstVisibleLine) {
                    val baseline = textLayout.getLineBaseline(i) + extendedPaddingTop
                    canvas.drawText(logicalLine.toString(), x, baseline.toFloat(), lineNumberPaint)
                }
                logicalLine++
            }
        }
        super.onDraw(canvas)
    }

    private fun updateLineNumberGutter() {
        if (basePaddingLeft < 0) {
            basePaddingLeft = paddingLeft
        }
        val content = text
        var lines = 1
        if (content != null && content.length <= MAX_LINE_NUMBER_TEXT_LENGTH) {
            for (index in content.indices) {
                if (content[index] == '\n') {
                    lines++
                }
            }
        }
        val digits = lines.toString().length.coerceAtLeast(2)
        lineNumberPaint.textSize = textSize
        lineNumberPaint.typeface = typeface
        val width = lineNumberPaint.measureText("0") * digits + lineNumberGap
        val newLeft = basePaddingLeft + width.toInt()
        if (paddingLeft != newLeft) {
            setPadding(newLeft, paddingTop, paddingRight, paddingBottom)
        }
    }

    private class TabReplacementSpan(private val tabWidthSpaces: Int = 4) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val spaceWidth = paint.measureText(" ")
            return (spaceWidth * tabWidthSpaces).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
        }
    }
}

// Line numbers walk the whole text, so they are skipped for very large documents.
private const val MAX_LINE_NUMBER_TEXT_LENGTH = 500_000
