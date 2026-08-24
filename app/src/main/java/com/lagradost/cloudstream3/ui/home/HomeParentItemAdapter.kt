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
            a.list.list == b.list.list
        })
) {
    companion object {
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 4) }
    }

    private var pendingFocusRestore: HomeFocusRestoreTarget? = null
    private var focusRestoreRecyclerView: RecyclerView? = null
    private var focusRestoreCompletion: ((HomeFocusRestoreTarget) -> Unit)? = null
    private var focusRestoreAttemptPosted = false

    data class ParentItemHolder(val binding: ViewBinding) : ViewHolderState<Bundle>(binding) {
        var itemKey: String? = null

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
        pendingFocusRestore = target
        focusRestoreRecyclerView = recyclerView
        focusRestoreCompletion = onComplete
        setChildAutomaticFocusRestore(recyclerView, enabled = false)
        scheduleFocusRestore()
    }

    private fun orderedItems(): List<HomeViewModel.ExpandableHomepageList> =
        immutableCurrentList.sortedBy { it.list.list.isEmpty() }

    private fun scheduleFocusRestore() {
        val recyclerView = focusRestoreRecyclerView ?: return
        if (pendingFocusRestore == null || !recyclerView.isAttachedToWindow) return
        if (focusRestoreAttemptPosted) return

        focusRestoreAttemptPosted = true
        recyclerView.post {
            focusRestoreAttemptPosted = false
            attemptFocusRestore()
        }
    }

    private fun attemptFocusRestore() {
        val target = pendingFocusRestore ?: return
        val recyclerView = focusRestoreRecyclerView ?: return
        val items = orderedItems()
        if (items.isEmpty()) return

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

    private fun childClickCallback(categoryKey: String): (SearchClickCallback) -> Unit = { callback ->
        if (callback.action != SEARCH_ACTION_FOCUSED) {
            focusTargetCallback?.invoke(
                HomeFocusRestoreTarget(categoryKey, homeFocusKey(callback.card))
            )
        }
        clickCallback(callback)
    }

    override fun submitList(
        list: Collection<HomeViewModel.ExpandableHomepageList>?,
        commitCallback: Runnable?
    ) {
        super.submitList(list?.sortedBy { it.list.list.isEmpty() }, Runnable {
            commitCallback?.run()
            scheduleFocusRestore()
        })
    }

    override fun onUpdateContent(
        holder: ViewHolderState<Bundle>,
        item: HomeViewModel.ExpandableHomepageList,
        position: Int
    ) {
        val binding = holder.view
        if (binding !is HomepageParentBinding) return
        (binding.homeChildRecyclerview.adapter as? HomeChildItemAdapter)?.apply {
            automaticFocusRestoreEnabled = pendingFocusRestore == null
            submitList(item.list.list, Runnable { scheduleFocusRestore() })
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Bundle>,
        item: HomeViewModel.ExpandableHomepageList,
        position: Int
    ) {
        val startFocus = R.id.nav_rail_view
        val endFocus = FOCUS_SELF
        val binding = holder.view
        if (binding !is HomepageParentBinding) return
        val info = item.list
        android.util.Log.d(
            "HomeFocusTrace",
            "parent-bind adapterPosition=$position category=${info.name} pending=${pendingFocusRestore != null}"
        )
        (holder as? ParentItemHolder)?.itemKey = info.name
        binding.apply {
            val currentAdapter = homeChildRecyclerview.adapter as? HomeChildItemAdapter
            if (currentAdapter == null) {
                homeChildRecyclerview.setRecycledViewPool(HomeChildItemAdapter.sharedPool)
                homeChildRecyclerview.adapter = HomeChildItemAdapter(
                    id = 31 * id + info.name.hashCode(),
                    clickCallback = childClickCallback(info.name),
                    nextFocusUp = homeChildRecyclerview.nextFocusUpId,
                    nextFocusDown = homeChildRecyclerview.nextFocusDownId,
                    primaryAction = primaryAction,
                ).apply {
                    automaticFocusRestoreEnabled = pendingFocusRestore == null
                    isHorizontal = info.isHorizontalImages
                    hasNext = item.hasNext
                    submitList(item.list.list, Runnable { scheduleFocusRestore() })
                }
            } else {
                currentAdapter.apply {
                    automaticFocusRestoreEnabled = pendingFocusRestore == null
                    isHorizontal = info.isHorizontalImages
                    hasNext = item.hasNext
                    this.clickCallback = childClickCallback(info.name)
                    primaryAction = this@ParentItemAdapter.primaryAction
                    nextFocusUp = homeChildRecyclerview.nextFocusUpId
                    nextFocusDown = homeChildRecyclerview.nextFocusDownId
                    submitIncomparableList(item.list.list, Runnable { scheduleFocusRestore() })
                }
            }

            homeChildRecyclerview.setLinearListLayout(
                isHorizontal = true,
                nextLeft = startFocus,
                nextRight = endFocus,
            )
            homeChildMoreInfo.text = info.name

            homeChildRecyclerview.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = item.list.name

                override fun onScrollStateChanged(
                    recyclerView: RecyclerView,
                    newState: Int
                ) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is HomeChildItemAdapter) return

                    val count = adapter.itemCount
                    val hasNext = adapter.hasNext
                    /*println(
                        "scolling ${recyclerView.isRecyclerScrollable()} ${
                            recyclerView.canScrollHorizontally(
                                1
                            )
                        }"
                    )*/
                    //!recyclerView.canScrollHorizontally(1)
                    if (!recyclerView.isRecyclerScrollable() && hasNext && expandCount != count) {
                        expandCount = count
                        expandCallback?.invoke(name)
                    }
                }
            })

            //(recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()
            if (isLayout(PHONE)) {
                homeChildMoreInfo.setOnClickListener {
                    moreInfoClickCallback.invoke(item)
                }
            }
            scheduleFocusRestore()
        }
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

        return ParentItemHolder(binding)
    }
}

@Suppress("DEPRECATION")
inline fun <reified T> Bundle.getSafeParcelable(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) getParcelable(key)
    else getParcelable(key, T::class.java)