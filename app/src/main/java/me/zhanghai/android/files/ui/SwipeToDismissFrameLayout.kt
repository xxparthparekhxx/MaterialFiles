package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A [FrameLayout] that can be dragged vertically to dismiss, as long as [isSwipeEnabled] is true.
 */
class SwipeToDismissFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    var isSwipeEnabled = true

    var onDismiss: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isSwipeEnabled) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> isDragging = false
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !isDragging) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 2) {
                        isDragging = true
                    }
                }
            }
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDragging) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                translationY = dy
                alpha = (1f - abs(dy) / height).coerceIn(0.3f, 1f)
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                if (abs(translationY) > height / 5f) {
                    onDismiss?.invoke()
                } else {
                    animate().translationY(0f).alpha(1f).setDuration(150).start()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                animate().translationY(0f).alpha(1f).setDuration(150).start()
            }
        }
        return true
    }
}
