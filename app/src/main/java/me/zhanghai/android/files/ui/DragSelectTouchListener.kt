/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * A [RecyclerView.OnItemTouchListener] that supports selecting a range of items by dragging over
 * them after a long press.
 *
 * The listener doesn't detect the long press by itself. [startDragSelect] should be called when the
 * user long presses an item, usually from the item's long click listener. The gesture is then taken
 * over from the [RecyclerView], so that the list doesn't scroll while a range of items is selected.
 */
class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val listener: Listener
) : RecyclerView.SimpleOnItemTouchListener() {
    interface Listener {
        /**
         * Called when the dragged position changes to the item at [position]. The item the drag
         * started at, [anchorPosition], is selected together with it.
         */
        fun onDragSelectMove(anchorPosition: Int, position: Int)

        /**
         * Called when the drag selection ends.
         */
        fun onDragSelectEnd()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val autoScrollEdgeSize =
        (AUTO_SCROLL_EDGE_SIZE_DP * recyclerView.resources.displayMetrics.density).roundToInt()
    private val autoScrollMaxSpeed =
        (AUTO_SCROLL_MAX_SPEED_DP * recyclerView.resources.displayMetrics.density).roundToInt()
    private var anchorPosition = RecyclerView.NO_POSITION
    private var currentPosition = RecyclerView.NO_POSITION
    private var isDragging = false
    private var isAutoScrolling = false
    private var autoScrollSpeed = 0
    private var lastX = 0F
    private var lastY = 0F

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (!isDragging || autoScrollSpeed == 0) {
                isAutoScrolling = false
                return
            }
            recyclerView.scrollBy(0, autoScrollSpeed)
            updatePosition()
            handler.postDelayed(this, AUTO_SCROLL_INTERVAL_MS)
        }
    }

    /**
     * Starts a drag selection at the given adapter position. Should be called when the user long
     * presses an item.
     */
    fun startDragSelect(anchorPosition: Int) {
        if (anchorPosition == RecyclerView.NO_POSITION) {
            return
        }
        this.anchorPosition = anchorPosition
        currentPosition = anchorPosition
        isDragging = true
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    // Take over the gesture, so that the list doesn't scroll while selecting.
                    lastX = e.x
                    lastY = e.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    // The user lifted the finger without dragging, so the drag selection never
                    // started.
                    endDragSelect()
                }
            }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastX = e.x
                lastY = e.y
                updatePosition()
                updateAutoScroll()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endDragSelect()
            }
        }
    }

    private fun updatePosition() {
        val child = recyclerView.findChildViewUnder(lastX, lastY) ?: return
        val position = recyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION || position == currentPosition) {
            return
        }
        currentPosition = position
        listener.onDragSelectMove(anchorPosition, position)
    }

    private fun updateAutoScroll() {
        val height = recyclerView.height
        val speed = when {
            lastY < autoScrollEdgeSize ->
                -((autoScrollEdgeSize - lastY) * autoScrollMaxSpeed / autoScrollEdgeSize)
            lastY > height - autoScrollEdgeSize ->
                (lastY - (height - autoScrollEdgeSize)) * autoScrollMaxSpeed / autoScrollEdgeSize
            else -> 0F
        }.roundToInt()
        autoScrollSpeed = speed
        if (speed != 0 && !isAutoScrolling) {
            isAutoScrolling = true
            handler.post(autoScrollRunnable)
        }
    }

    private fun endDragSelect() {
        isDragging = false
        isAutoScrolling = false
        anchorPosition = RecyclerView.NO_POSITION
        currentPosition = RecyclerView.NO_POSITION
        autoScrollSpeed = 0
        handler.removeCallbacks(autoScrollRunnable)
        listener.onDragSelectEnd()
    }

    companion object {
        private const val AUTO_SCROLL_EDGE_SIZE_DP = 48
        private const val AUTO_SCROLL_MAX_SPEED_DP = 16
        private const val AUTO_SCROLL_INTERVAL_MS = 16L
    }
}
