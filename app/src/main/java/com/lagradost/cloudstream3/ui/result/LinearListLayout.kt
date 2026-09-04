package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.FocusDirection
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import kotlin.math.abs

const val FOCUS_SELF = View.NO_ID - 1
const val FOCUS_INHERIT = FOCUS_SELF - 1

/**
 * Yatay kategori listelerinde kullanıcının sağa/sola hızlı kaydırması sırasında
 * dikey üst RecyclerView'ın (ana sayfa dikey listesi) dokunma hareketini kesmesini (intercept)
 * önleyen ve dikey kaydırma ile yatay kaydırma arasındaki titreme/çatışmayı çözen dinleyici.
 */
fun RecyclerView.attachNestedHorizontalTouchListener() {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
        private var startX = 0f
        private var startY = 0f
        private var isHorizontalDragging = false

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    isHorizontalDragging = false
                    // İlk basış anında ebeveyn dikey listenin olası yatay kaydırmayı bölmesini engelle
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(e.x - startX)
                    val dy = abs(e.y - startY)
                    if (!isHorizontalDragging) {
                        if (dx > touchSlop && dx > dy) {
                            // Yatay hareket dikeyden belirgin şekilde fazla; kaydırma sahipliğini yatayda tut
                            isHorizontalDragging = true
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        } else if (dy > touchSlop && dy > dx) {
                            // Dikey kaydırma baskınsa ebeveyn dikey listeye izin ver
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHorizontalDragging = false
                    rv.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return false
        }
    })
}

fun RecyclerView?.setLinearListLayout(
    isHorizontal: Boolean = true,
    nextLeft: Int = FOCUS_INHERIT,
    nextRight: Int = FOCUS_INHERIT,
    nextUp: Int = FOCUS_INHERIT,
    nextDown: Int = FOCUS_INHERIT,
    coalesceTvScroll: Boolean = false,
) {
    if (this == null) return
    val ctx = this.context ?: return
    this.layoutManager = (this.layoutManager as? LinearListLayout ?: LinearListLayout(ctx)).apply {
        if (isHorizontal) setHorizontal() else setVertical()
        nextFocusLeft =
            if (nextLeft == FOCUS_INHERIT) this@setLinearListLayout.nextFocusLeftId else nextLeft
        nextFocusRight =
            if (nextRight == FOCUS_INHERIT) this@setLinearListLayout.nextFocusRightId else nextRight
        nextFocusUp =
            if (nextUp == FOCUS_INHERIT) this@setLinearListLayout.nextFocusUpId else nextUp
        nextFocusDown =
            if (nextDown == FOCUS_INHERIT) this@setLinearListLayout.nextFocusDownId else nextDown
        this.coalesceTvScroll = coalesceTvScroll
    }
}

open class LinearListLayout(context: Context?) :
    LinearLayoutManager(context) {

    var nextFocusLeft: Int = View.NO_ID
    var nextFocusRight: Int = View.NO_ID
    var nextFocusUp: Int = View.NO_ID
    var nextFocusDown: Int = View.NO_ID

    private var pendingFocusPosition = RecyclerView.NO_POSITION
    var coalesceTvScroll: Boolean = false
    private var pendingHorizontalScroll = 0
    private var horizontalScrollPosted = false
    private var horizontalScrollParent: RecyclerView? = null

    fun setHorizontal() {
        orientation = HORIZONTAL
    }

    fun setVertical() {
        orientation = VERTICAL
    }

    private fun getCorrectParent(focused: View?): View? {
        var current = focused ?: return null
        while (current.parent is View && current.parent !is RecyclerView) {
            current = current.parent as View
        }
        return current.takeIf { it.parent is RecyclerView }
    }

    private fun getPosition(view: View?): Int? {
        return (view?.layoutParams as? RecyclerView.LayoutParams?)?.absoluteAdapterPosition
    }

    private fun getViewFromPos(pos: Int): View? = findViewByPosition(pos)

    private fun focusAfterLayout(
        recyclerView: RecyclerView,
        focused: View,
        position: Int,
    ): View {
        if (pendingFocusPosition != position) {
            pendingFocusPosition = position
            recyclerView.doOnNextLayout {
                if (pendingFocusPosition != position) return@doOnNextLayout
                pendingFocusPosition = RecyclerView.NO_POSITION
                if (!focused.hasFocus()) return@doOnNextLayout
                findViewByPosition(position)?.takeIf { it.isFocusable }?.requestFocus()
            }
        }
        // TV ve emülatörde henüz ekranda olmayan kart hizalanırken bir sonraki layout döngüsünde
        // görünümün kesin olarak üretilmesi ve odak kaybı/geri atma yaşanmaması için anında konumlandır
        if (isLayout(TV or EMULATOR) && orientation == HORIZONTAL) {
            val offset = if (isLayoutRTL) 0 else recyclerView.paddingLeft
            scrollToPositionWithOffset(position, offset)
        } else {
            scrollToPosition(position)
        }
        return focused
    }

    /*
    private fun scrollTo(position: Int) {
        val linearSmoothScroller = LinearSmoothScroller(recyclerView.context)
        linearSmoothScroller.targetPosition = position
        startSmoothScroll(linearSmoothScroller)
    }*/

    /** from the current focus go to a direction */
    private fun getNextDirection(focused: View?, direction: FocusDirection): View? {
        val id = when (direction) {
            FocusDirection.Start -> if (isLayoutRTL) nextFocusRight else nextFocusLeft
            FocusDirection.End -> if (isLayoutRTL) nextFocusLeft else nextFocusRight
            FocusDirection.Up -> nextFocusUp
            FocusDirection.Down -> nextFocusDown
        }

        return when (id) {
            View.NO_ID -> null
            FOCUS_SELF -> focused
            else -> CommonActivity.continueGetNextFocus(
                activity ?: focused,
                focused ?: return null,
                direction,
                id
            )
        }
    }

    fun redirectRecycleToFirstItem(focused: View): View? {
        return when (focused) {
            is RecyclerView -> {
                (focused.layoutManager as? LinearListLayout)?.let { focusedLayoutManager ->
                    val firstPosition = focusedLayoutManager.findFirstVisibleItemPosition()
                    val firstView = focusedLayoutManager.findViewByPosition(firstPosition)
                    firstView
                } ?: focused
            }

            else -> focused
        }
    }

    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        val dir = if (orientation == HORIZONTAL) {
            if (direction == View.FOCUS_DOWN) getNextDirection(
                focused,
                FocusDirection.Down
            )?.let { newFocus ->
                return redirectRecycleToFirstItem(newFocus)
            }
            if (direction == View.FOCUS_UP) getNextDirection(
                focused,
                FocusDirection.Up
            )?.let { newFocus ->
                return redirectRecycleToFirstItem(newFocus)
            }

            if (direction == View.FOCUS_DOWN || direction == View.FOCUS_UP) {
                // This scrolls the recyclerview before doing focus search, which
                // allows the focus search to work better.

                // Without this the recyclerview focus location on the screen
                // would change when scrolling between recyclerviews.
                (focused.parent as? RecyclerView)?.focusSearch(direction)
                return null
            }
            var ret = if (direction == View.FOCUS_RIGHT) 1 else -1
            // only flip on horizontal layout
            if (isLayoutRTL) {
                ret = -ret
            }
            ret
        } else {
            if (direction == View.FOCUS_RIGHT) getNextDirection(
                focused,
                FocusDirection.End
            )?.let { newFocus ->
                return newFocus
            }
            if (direction == View.FOCUS_LEFT) getNextDirection(
                focused,
                FocusDirection.Start
            )?.let { newFocus ->
                return newFocus
            }

            if (direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT) {
                (focused.parent as? RecyclerView)?.focusSearch(direction)
                return null
            }

            //if (direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT) return null
            if (direction == View.FOCUS_DOWN) 1 else -1
        }

        try {
            val position = getPosition(getCorrectParent(focused)) ?: return null
            val lookFor = dir + position

            // if out of bounds then refocus as specified
            return if (lookFor >= itemCount) {
                getNextDirection(
                    focused,
                    if (orientation == HORIZONTAL) FocusDirection.End else FocusDirection.Down
                )
            } else if (lookFor < 0) {
                getNextDirection(
                    focused,
                    if (orientation == HORIZONTAL) FocusDirection.Start else FocusDirection.Up
                )
            } else {
                getViewFromPos(lookFor)?.also {
                    pendingFocusPosition = RecyclerView.NO_POSITION
                } ?: run {
                    val recyclerView = getCorrectParent(focused)?.parent as? RecyclerView
                        ?: return null
                    focusAfterLayout(recyclerView, focused, lookFor)
                }
            }
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: android.graphics.Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean
    ): Boolean {
        if (isLayout(TV or EMULATOR) && orientation == HORIZONTAL) {
            val position = getPosition(child)
            if (position == RecyclerView.NO_POSITION) return false

            val density = child.resources.displayMetrics.density
            val leadMargin = (child.width * 0.75f).toInt().coerceAtLeast((60 * density).toInt())
            val paddingThreshold = (12 * density).toInt()

            // Kartın halihazırda güvenli izleme alanında olup olmadığını hesapla
            val dx = if (!isLayoutRTL) {
                val start = parent.paddingLeft
                val end = parent.width - parent.paddingRight - leadMargin
                val left = getDecoratedLeft(child)
                val right = getDecoratedRight(child)
                when {
                    position == 0 -> left - start
                    left < start + paddingThreshold -> left - start
                    right > end -> right - end
                    else -> 0
                }
            } else {
                val start = parent.width - parent.paddingRight
                val end = parent.paddingLeft + leadMargin
                val left = getDecoratedLeft(child)
                val right = getDecoratedRight(child)
                when {
                    position == 0 -> right - start
                    right > start - paddingThreshold -> right - start
                    left < end -> left - end
                    else -> 0
                }
            }

            // Dikey ebeveyn listeye (homeMasterRecycler) odak dikdörtgenini ileterek
            // MainActivity.centerView dikey merkezleme çağrısının üst RecyclerView tarafından
            // algılanıp dikeyde akıcı olarak ortalanmasını sağla
            val parentConsumed = try {
                if (parent.parent is android.view.ViewParent) {
                    val parentRect = android.graphics.Rect(rect)
                    parentRect.offset(child.left, child.top)
                    parent.parent.requestChildRectangleOnScreen(parent, parentRect, immediate)
                } else false
            } catch (_: Throwable) {
                false
            }

            // Kart zaten ekranda rahatça görünüyorsa listeyi yatayda gereksiz yere kaydırıp titreme yaratma
            if (dx == 0) {
                return parentConsumed
            }

            if (immediate) {
                if (parent.isComputingLayout) {
                    parent.post {
                        if (!parent.isComputingLayout) {
                            parent.stopScroll()
                            parent.scrollBy(dx, 0)
                        }
                    }
                } else {
                    parent.stopScroll()
                    parent.scrollBy(dx, 0)
                }
                return true
            }

            // TV yatay kategorisinde hızlı kumanda basışlarında animasyonları birleştir (coalesce)
            // ve yerel akıcı kaydırma ile pürüzsüz geçiş sağla
            if (coalesceTvScroll) {
                pendingHorizontalScroll = dx
                horizontalScrollParent = parent
                parent.stopScroll()
                if (!horizontalScrollPosted) {
                    horizontalScrollPosted = true
                    parent.postOnAnimation {
                        horizontalScrollPosted = false
                        val scrollParent = horizontalScrollParent ?: return@postOnAnimation
                        val pendingScroll = pendingHorizontalScroll
                        pendingHorizontalScroll = 0
                        if (pendingScroll != 0) scrollParent.smoothScrollBy(pendingScroll, 0)
                    }
                }
            } else {
                parent.smoothScrollBy(dx, 0)
            }
            return true
        } else {
            return super.requestChildRectangleOnScreen(
                parent,
                child,
                rect,
                immediate,
                focusedChildVisible
            )
        }
    }

    /*override fun onRequestChildFocus(
        parent: RecyclerView,
        state: RecyclerView.State,
        child: View,
        focused: View?
    ): Boolean {
        return super.onRequestChildFocus(parent, state, child, focused)
        getPosition(getCorrectParent(focused ?: return true))?.let {
            val startView = findFirstVisibleChildClosestToStart(true,true)
            val endView = findFirstVisibleChildClosestToEnd(true,true)
            val start = getPosition(startView)
            val end = getPosition(endView)
            fill(parent,LayoutState())

            val helper = mOrientationHelper ?: return false
            val laidOutArea: Int = abs(
                helper.getDecoratedEnd(startView)
                        - helper.getDecoratedStart(endView)
            )
            val itemRange: Int = abs(
                (start
                        - end)
            ) + 1

            val avgSizePerRow = laidOutArea.toFloat() / itemRange

            return Math.round(
                itemsBefore * avgSizePerRow + ((orientation.getStartAfterPadding()
                        - orientation.getDecoratedStart(startChild)))
            )
            recyclerView.scrollToPosition(it)
        }
        return true*/

    //return super.onRequestChildFocus(parent, state, child, focused)
    /* if (focused == null || focused == child) {
         return super.onRequestChildFocus(parent, state, child, focused)
     }

     try {
         val pos = getPosition(getCorrectParent(focused) ?: return true)
         scrollToPosition(pos)
     } catch (e: Exception) {
         logError(e)
     }
     return true
}*/
}