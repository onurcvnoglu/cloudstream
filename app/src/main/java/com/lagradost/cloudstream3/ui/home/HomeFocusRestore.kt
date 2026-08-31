package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.SearchResponse

data class HomeFocusRestoreTarget(
    val categoryKey: String,
    val itemKey: String,
)

data class HomeFocusRestoreCategory(
    val key: String,
    val cardKeys: List<String>,
)

data class HomeFocusRestoreSelection(
    val categoryIndex: Int,
    val cardIndex: Int,
)

object HomeFocusRestorePlanner {
    fun select(
        categories: List<HomeFocusRestoreCategory>,
        target: HomeFocusRestoreTarget,
    ): HomeFocusRestoreSelection? {
        val targetCategoryIndex = categories.indexOfFirst { it.key == target.categoryKey }
        if (targetCategoryIndex < 0 || categories[targetCategoryIndex].cardKeys.isEmpty()) {
            return null
        }
        val categoryIndex = targetCategoryIndex

        val category = categories[categoryIndex]
        val requestedCardIndex = if (categoryIndex == targetCategoryIndex) {
            category.cardKeys.indexOf(target.itemKey)
        } else {
            -1
        }

        return HomeFocusRestoreSelection(
            categoryIndex = categoryIndex,
            cardIndex = requestedCardIndex.takeIf { it >= 0 } ?: 0,
        )
    }

    fun adapterPosition(categoryIndex: Int, headers: Int): Int = categoryIndex + headers
}

internal fun homeFocusKey(item: SearchResponse): String =
    "${item.apiName}:${item.url}:${item.name}"
