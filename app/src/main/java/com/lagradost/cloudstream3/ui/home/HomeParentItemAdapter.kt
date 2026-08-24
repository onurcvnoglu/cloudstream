package com.lagradost.cloudstream3.ui.home

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.HomepageParentBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.isRecyclerScrollable

class LoadClickCallback(
    val action: Int = 0,
    val view: View,
    val position: Int,
    val response: LoadResponse
)

open class ParentItemAdapter(
    id: Int,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
    private val expandCallback: ((String) -> Unit)? = null,
    private val primaryAction: Int = SEARCH_ACTION_LOAD,
    private val focusTargetCallback: ((HomeFocusRestoreTarget) -> Unit)? = null,
) : BaseAdapter<HomeViewModel.ExpandableHomepageList, Bundle>(
    id,
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.list.name == b.list.name },
        contentSame = { a, b ->
            a.currentPage == b.currentPage &&
                    a.hasNext == b.hasNext &&
                    a.list.isHorizontalImages == b.list.isHorizontalImages &&
                    a.list.list == b.list.list
        })
) {
    companion object {
        private const val MAX_FOCUS_RESTORE_ATTEMPTS = 6

        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 4) }
    }

    private var pendingFocusRestore: HomeFocusRestoreTarget? = null
    private var focusRestoreRecyclerView: RecyclerView? = null
    private var focusRestoreCompletion: ((HomeFocusRestoreTarget) -> Unit)? = null
    private var focusRestoreAttemptPosted = false
    private var focusRestoreAttempts = 0
    private var focusRestoreGeneration = 0L

    data class ParentItemHolder(val binding: ViewBinding) : ViewHolderState<Bundle>(binding) {
        var itemKey: String? = null
        var item: HomeViewModel.ExpandableHomepageList? = null
        var lastExpansionPage: Int? = null
        var lastExpansionCategory: String? = null

        override fun save(): Bundle = Bundle().apply {
            val recyclerView = (binding as? HomepageParentBinding)?.homeChildRecyclerview
            putParcelable(
                "value",
                recyclerView?.layoutManager?.onSaveInstanceState()
            )
            (recyclerView?.adapter as? BaseAdapter<*, *>)?.save(recyclerView)
        }

        override fun restore(state: Bundle) {
            (binding as? HomepageParentBinding)?.homeChildRecyclerview?.layoutManager?.onRestoreInstanceState(
                state.getSafeParcelable<Parcelable>("value")
            )
        }
    }

    protected override fun stateKey(holder: ViewHolderState<Bundle>): Any? =
        (holder as? ParentItemHolder)?.itemKey ?: super.stateKey(holder)

    fun restoreFocus(
        recyclerView: RecyclerView,
        target: HomeFocusRestoreTarget,
        onComplete: ((HomeFocusRestoreTarget) -> Unit)? = null,
    ) {
        if (pendingFocusRestore == target && focusRestoreRecyclerView === recyclerView) return

        focusRestoreRecyclerView?.let { setChildAutomaticFocusRestore(it, enabled = true) }
        focusRestoreGeneration++
        focusRestoreAttemptPosted = false
        focusRestoreAttempts = 0
        pendingFocusRestore = target
        focusRestoreRecyclerView = recyclerView
        focusRestoreCompletion = onComplete
        setChildAutomaticFocusRestore(recyclerView, enabled = false)
        scheduleFocusRestore()
    }

    fun cancelFocusRestore() {
        focusRestoreGeneration++
        focusRestoreRecyclerView?.let { setChildAutomaticFocusRestore(it, enabled = true) }
        pendingFocusRestore = null
        focusRestoreRecyclerView = null
        focusRestoreCompletion = null
        focusRestoreAttemptPosted = false
        focusRestoreAttempts = 0
    }

    private fun orderedItems(): List<HomeViewModel.ExpandableHomepageList> = immutableCurrentList

    private fun scheduleFocusRestore() {
        val recyclerView = focusRestoreRecyclerView ?: return
        if (pendingFocusRestore == null || !recyclerView.isAttachedToWindow) return
        if (focusRestoreAttemptPosted) return

        val generation = focusRestoreGeneration
        focusRestoreAttemptPosted = true
        recyclerView.post {
            if (generation != focusRestoreGeneration) return@post
            focusRestoreAttemptPosted = false
            if (++focusRestoreAttempts > MAX_FOCUS_RESTORE_ATTEMPTS) {
                completeFocusRestore()
            } else {
                attemptFocusRestore()
            }
        }
    }

    private fun attemptFocusRestore() {
        val target = pendingFocusRestore ?: return
        val recyclerView = focusRestoreRecyclerView ?: return
        val items = orderedItems()
        if (items.isEmpty()) {
            scheduleFocusRestore()
            return
        }

        val categories = items.map { item ->
            HomeFocusRestoreCategory(
                key = item.list.name,
                cardKeys = item.list.list.map(::homeFocusKey),
            )
        }
        val selection = HomeFocusRestorePlanner.select(categories, target)
        if (selection == null) {
            completeFocusRestore()
            return
        }

        val parentAdapterPosition = HomeFocusRestorePlanner.adapterPosition(
            selection.categoryIndex,
            headers,
        )
        recyclerView.scrollToPosition(parentAdapterPosition)

        val parentHolder = recyclerView.findViewHolderForAdapterPosition(parentAdapterPosition)
            as? ParentItemHolder ?: run {
            scheduleFocusRestore()
            return
        }
        val binding = parentHolder.binding as? HomepageParentBinding ?: run {
            completeFocusRestore()
            return
        }
        val childRecyclerView = binding.homeChildRecyclerview
        val childAdapter = childRecyclerView.adapter as? HomeChildItemAdapter ?: run {
            scheduleFocusRestore()
            return
        }
        childAdapter.automaticFocusRestoreEnabled = false

        val category = items[selection.categoryIndex]
        val sourceKeys = category.list.list.map(::homeFocusKey)
        val childKeys = childAdapter.immutableCurrentList.map(::homeFocusKey)
        if (sourceKeys != childKeys) {
            childRecyclerView.post { scheduleFocusRestore() }
            return
        }

        val targetKey = sourceKeys.getOrNull(selection.cardIndex) ?: run {
            completeFocusRestore()
            return
        }
        val childPosition = childAdapter.immutableCurrentList.indexOfFirst {
            homeFocusKey(it) == targetKey
        }
        if (childPosition < 0) {
            childRecyclerView.post { scheduleFocusRestore() }
            return
        }

        childRecyclerView.scrollToPosition(childPosition)
        val childHolder = childRecyclerView.findViewHolderForAdapterPosition(childPosition)
            ?: run {
                childRecyclerView.post { scheduleFocusRestore() }
                return
            }

        if (childHolder.itemView.hasFocus() || childHolder.itemView.requestFocus()) {
            completeFocusRestore()
        } else {
            childRecyclerView.post { scheduleFocusRestore() }
        }
    }

    private fun setChildAutomaticFocusRestore(recyclerView: RecyclerView, enabled: Boolean) {
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
                as? ParentItemHolder ?: continue
            val childAdapter = (holder.binding as? HomepageParentBinding)
                ?.homeChildRecyclerview?.adapter as? HomeChildItemAdapter ?: continue
            childAdapter.automaticFocusRestoreEnabled = enabled
            if (enabled) childAdapter.clearSavedFocusStates()
        }
    }

    private fun completeFocusRestore() {
        val target = pendingFocusRestore ?: return
        val recyclerView = focusRestoreRecyclerView
        pendingFocusRestore = null
        focusRestoreRecyclerView = null
        recyclerView?.let { setChildAutomaticFocusRestore(it, enabled = true) }
        val completion = focusRestoreCompletion
        focusRestoreCompletion = null
        completion?.invoke(target)
    }

    private fun childClickCallback(holder: ParentItemHolder): (SearchClickCallback) -> Unit = { callback ->
        if (callback.action != SEARCH_ACTION_FOCUSED) {
            holder.itemKey?.let { categoryKey ->
                focusTargetCallback?.invoke(
                    HomeFocusRestoreTarget(categoryKey, homeFocusKey(callback.card))
                )
            }
        }
        clickCallback(callback)
    }

    override fun submitList(
        list: Collection<HomeViewModel.ExpandableHomepageList>?,
        commitCallback: Runnable?
    ) {
        val sortedList = list?.sortedBy { it.list.list.isEmpty() }
        if (sortedList != null && immutableCurrentList == sortedList) {
            commitCallback?.run()
            scheduleFocusRestore()
            return
        }
        super.submitList(sortedList, Runnable {
            commitCallback?.run()
            scheduleFocusRestore()
        })
    }

    private fun bindParentContent(
        holder: ParentItemHolder,
        item: HomeViewModel.ExpandableHomepageList,
    ) {
        val binding = holder.view as? HomepageParentBinding ?: return
        val info = item.list
        if (holder.itemKey != info.name) {
            holder.lastExpansionCategory = info.name
            holder.lastExpansionPage = null
        }
        holder.itemKey = info.name
        holder.item = item

        val childAdapter = (binding.homeChildRecyclerview.adapter as? HomeChildItemAdapter)
            ?: HomeChildItemAdapter(
                id = 31 * id + info.name.hashCode(),
                clickCallback = childClickCallback(holder),
                nextFocusUp = binding.homeChildRecyclerview.nextFocusUpId,
                nextFocusDown = binding.homeChildRecyclerview.nextFocusDownId,
                primaryAction = primaryAction,
            ).also { binding.homeChildRecyclerview.adapter = it }
        childAdapter.apply {
            automaticFocusRestoreEnabled = pendingFocusRestore == null
            isHorizontal = info.isHorizontalImages
            hasNext = item.hasNext
            if (immutableCurrentList != info.list) {
                submitList(info.list, Runnable { scheduleFocusRestore() })
            } else {
                scheduleFocusRestore()
            }
        }
        binding.homeChildMoreInfo.text = info.name
        scheduleFocusRestore()
    }

    override fun onUpdateContent(
        holder: ViewHolderState<Bundle>,
        item: HomeViewModel.ExpandableHomepageList,
        position: Int
    ) {
        (holder as? ParentItemHolder)?.let { bindParentContent(it, item) }
    }

    override fun onBindContent(
        holder: ViewHolderState<Bundle>,
        item: HomeViewModel.ExpandableHomepageList,
        position: Int
    ) {
        (holder as? ParentItemHolder)?.let { bindParentContent(it, item) }
    }

    override fun onCreateContent(parent: ViewGroup): ParentItemHolder {
        val layoutResId = when {
            isLayout(TV) -> R.layout.homepage_parent_tv
            isLayout(EMULATOR) -> R.layout.homepage_parent_emulator
            else -> R.layout.homepage_parent
        }

        val inflater = LayoutInflater.from(parent.context)
        val binding = try {
            HomepageParentBinding.bind(inflater.inflate(layoutResId, parent, false))
        } catch (t: Throwable) {
            logError(t)
            // just in case someone forgot we don't want to crash
            HomepageParentBinding.inflate(inflater)
        }

        val holder = ParentItemHolder(binding)
        val childRecyclerView = binding.homeChildRecyclerview
        childRecyclerView.setRecycledViewPool(HomeChildItemAdapter.sharedPool)
        childRecyclerView.setLinearListLayout(
            isHorizontal = true,
            nextLeft = R.id.nav_rail_view,
            nextRight = FOCUS_SELF,
        )
        childRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return

                val adapter = recyclerView.adapter as? HomeChildItemAdapter ?: return
                val categoryKey = holder.itemKey ?: return
                if (!recyclerView.isRecyclerScrollable() || !adapter.hasNext) {
                    return
                }
                if (holder.lastExpansionCategory == categoryKey &&
                    holder.lastExpansionPage == holder.item?.currentPage
                ) {
                    return
                }

                holder.lastExpansionCategory = categoryKey
                holder.lastExpansionPage = holder.item?.currentPage
                expandCallback?.invoke(categoryKey)
            }
        })
        if (isLayout(PHONE)) {
            binding.homeChildMoreInfo.setOnClickListener {
                holder.item?.let(moreInfoClickCallback)
            }
        }

        return holder
    }
}

@Suppress("DEPRECATION")
inline fun <reified T> Bundle.getSafeParcelable(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) getParcelable(key)
    else getParcelable(key, T::class.java)