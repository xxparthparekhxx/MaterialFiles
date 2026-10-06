/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.KeyEvent
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

    private fun init() {
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

        addTextChangedListener(object : TextWatcher {
            private var changeStart = 0
            private var changeAfter = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                changeStart = start
                changeAfter = after
            }

            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable?) {
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

    // onMeasure() calls registerForPreDraw() and onPreDraw() calls bringPointIntoView(), which
    // results in unwanted scroll when IME is toggled.
    override fun onPreDraw(): Boolean = true

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
