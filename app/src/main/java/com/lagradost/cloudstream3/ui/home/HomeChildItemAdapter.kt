package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.HomeRemoveGridBinding
import com.lagradost.cloudstream3.databinding.HomeRemoveGridExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class HomeScrollViewHolderState(view: ViewBinding) : ViewHolderState<Boolean>(view) {
    // very shitty that we cant store the state when the view clears,
    // but this is because the focus clears before the view is removed
    // so we have to manually store it
    var wasFocused: Boolean = false
    var itemKey: String? = null
    var restoreFocusEnabled: Boolean = true
    override fun save(): Boolean = wasFocused
    override fun restore(state: Boolean) {
        wasFocused = false
        if (state && restoreFocusEnabled && isLayout(TV or EMULATOR)) {
            itemView.requestFocus()
        }
    }
}

class ResumeItemAdapter(
    nextFocusUp: Int? = null,
    nextFocusDown: Int? = null,
    clickCallback: (SearchClickCallback) -> Unit,
    private val removeCallback: (View) -> Unit,
) : HomeChildItemAdapter(
    id = "resumeAdapter".hashCode(),
    nextFocusUp = nextFocusUp,
    nextFocusDown = nextFocusDown,
    clickCallback = clickCallback
) {
    // As there is no popup on TV we instead use the footer to clear
    override val footers = if (isLayout(TV or EMULATOR)) 1 else 0

    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Boolean> {
        // Boyutların henüz hesaplanmadığı ilk yükleme anlarında gereksiz layout tetiklemelerini önle
        if (minPosterSize <= 0 || maxPosterSize <= 0) {
            updatePosterSize(parent.context)
            updateCachedPosterSize()
        }
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeRemoveGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeRemoveGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    override fun onClearView(holder: ViewHolderState<Boolean>) {
        // Clear the image, idk if this saves ram or not, but I guess?
        clearImage(holder.view.root.findViewById(R.id.imageView))
    }

    override fun onBindFooter(holder: ViewHolderState<Boolean>) {
        this.applyBinding(holder, false)
        when (val binding = holder.view) {
            is HomeRemoveGridBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)
            }

            is HomeRemoveGridExpandedBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)
            }
        }
        holder.itemView.apply {
            if (isLayout(TV)) {
                isFocusableInTouchMode = true
                isFocusable = true
            }
            nextFocusUp?.let {
                nextFocusUpId = it
            }
            nextFocusDown?.let {
                nextFocusDownId = it
            }

            setOnClickListener { v ->
                removeCallback.invoke(v ?: return@setOnClickListener)
            }
        }
    }
}

/** Remember to set `updatePosterSize` to cache the poster size,
 * otherwise the width and height is unset */
open class HomeChildItemAdapter(
    id: Int,
    var nextFocusUp: Int? = null,
    var nextFocusDown: Int? = null,
    var primaryAction: Int = SEARCH_ACTION_LOAD,
    var clickCallback: (SearchClickCallback) -> Unit,
) :
    BaseAdapter<SearchResponse, Boolean>(
        id, diffCallback = BaseDiffCallback(
            itemSame = { a, b ->
                a.url == b.url && a.name == b.name
            },
            contentSame = { a, b ->
                a == b
            })
    ) {
    var hasNext: Boolean = false
    var isHorizontal: Boolean = false
        set(value) {
            field = value
            updateCachedPosterSize()
        }

    protected fun updateCachedPosterSize() {
        setWidth = if (!isHorizontal) {
            minPosterSize
        } else {
            maxPosterSize
        }
        setHeight = if (!isHorizontal) {
            maxPosterSize
        } else {
            minPosterSize
        }
    }

    init {
        updateCachedPosterSize()
    }

    protected var setWidth = 0
    protected var setHeight = 0
    private var fallbackFocusPending = false

    internal var automaticFocusRestoreEnabled: Boolean = true
    internal var verticalFocusCallback: ((moveDown: Boolean) -> Boolean)? = null

    internal fun clearSavedFocusStates(recyclerView: RecyclerView? = null) {
        layoutManagerStates[id]?.entries?.removeAll { it.value as? Boolean == true }
        recyclerView?.let { view ->
            for (index in 0 until view.childCount) {
                (view.getChildViewHolder(view.getChildAt(index)) as? HomeScrollViewHolderState)
                    ?.wasFocused = false
            }
        }
    }

    internal fun focusKey(item: SearchResponse): String = homeFocusKey(item)

    protected override fun stateKey(holder: ViewHolderState<Boolean>): Any? =
        (holder as? HomeScrollViewHolderState)?.itemKey ?: super.stateKey(holder)

    private fun prepareFocusFallback(list: List<SearchResponse>?) {
        val focusedKey = findSavedStateKey { it } ?: return
        if (list?.any { focusKey(it) == focusedKey } != true) {
            removeSavedState(focusedKey)
            fallbackFocusPending = true
        }
    }

    override fun submitList(list: Collection<SearchResponse>?, commitCallback: Runnable?) {
        prepareFocusFallback(list?.toList())
        super.submitList(list, Runnable {
            commitCallback?.run()
        })
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Boolean> {
        // Boyutların henüz hesaplanmadığı ilk yükleme anlarında gereksiz layout tetiklemelerini önle
        if (minPosterSize <= 0 || maxPosterSize <= 0) {
            updatePosterSize(parent.context)
            updateCachedPosterSize()
        }
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeResultGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeResultGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    companion object {
        // The vast majority of the lag comes from creating the view
        // This simply shares the views between all HomeChildItemAdapter
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 20) }

        var minPosterSize: Int = 0
        var maxPosterSize: Int = 0

        fun updatePosterSize(context: Context, value: Int? = null) {
            val scale = value ?: PreferenceManager.getDefaultSharedPreferences(context)
                ?.getInt(context.getString(R.string.poster_size_key), 0) ?: 0
            // Scale by +10% per step
            val mul = 1.0f + scale * 0.1f
            minPosterSize = (114.toPx.toFloat() * mul).toInt()
            maxPosterSize = (180.toPx.toFloat() * mul).toInt()
        }

        fun updateLayoutParms(layout: FrameLayout, width: Int, height: Int) {
            val params = layout.layoutParams
            if (params.height == height && params.width == width) return

            params.width = width
            params.height = height

            layout.layoutParams = params
        }
    }

    protected fun applyBinding(holder: ViewHolderState<Boolean>, isFirstItem: Boolean) {
        when (val binding = holder.view) {
            is HomeResultGridBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)
            }

            is HomeResultGridExpandedBinding -> {
                updateLayoutParms(binding.backgroundCard, setWidth, setHeight)

                if (isFirstItem) { // to fix tv
                    binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
                }
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Boolean>,
        item: SearchResponse,
        position: Int
    ) {
        applyBinding(holder, position == 0)
        (holder as? HomeScrollViewHolderState)?.apply {
            // Recycled holders must not carry a previous category's transient focus state.
            wasFocused = false
            itemKey = focusKey(item)
            restoreFocusEnabled = automaticFocusRestoreEnabled
        }

        if (fallbackFocusPending && automaticFocusRestoreEnabled && position == 0 && isLayout(TV or EMULATOR)) {
            holder.itemView.doOnLayout {
                holder.itemView.post {
                    if (fallbackFocusPending && holder.itemView.rootView.findFocus() == null) {
                        fallbackFocusPending = false
                        holder.itemView.requestFocus()
                    }
                }
            }
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN || !isLayout(TV or EMULATOR)) {
                return@setOnKeyListener false
            }
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> verticalFocusCallback?.invoke(true) == true
                KeyEvent.KEYCODE_DPAD_UP -> verticalFocusCallback?.invoke(false) == true
                else -> false
            }
        }

        SearchResultBuilder.bind(
            clickCallback = { click ->
                // ok, so here we hijack the callback to fix the focus
                when (click.action) {
                    SEARCH_ACTION_LOAD -> (holder as? HomeScrollViewHolderState)?.wasFocused = true
                }
                clickCallback(click)
            },
            item,
            position,
            holder.itemView,
            nextFocusUp,
            nextFocusDown,
            primaryAction = primaryAction
        )

        holder.itemView.tag = position
    }
}
