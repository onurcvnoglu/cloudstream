package com.lagradost.cloudstream3.ui.search

internal enum class SearchFocusTarget {
    SUGGESTIONS,
    ADVANCED_RESULTS,
    STANDARD_RESULTS,
    HISTORY,
    NONE,
}

internal fun resolveSearchFocusTarget(
    hasSuggestions: Boolean,
    hasQuery: Boolean,
    isAdvancedSearch: Boolean,
    hasAdvancedResults: Boolean,
    hasStandardResults: Boolean,
    hasHistory: Boolean,
): SearchFocusTarget = when {
    hasSuggestions -> SearchFocusTarget.SUGGESTIONS
    !hasQuery && hasHistory -> SearchFocusTarget.HISTORY
    hasQuery && isAdvancedSearch && hasAdvancedResults -> SearchFocusTarget.ADVANCED_RESULTS
    hasQuery && !isAdvancedSearch && hasStandardResults -> SearchFocusTarget.STANDARD_RESULTS
    else -> SearchFocusTarget.NONE
}
