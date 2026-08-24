package com.lagradost.cloudstream3.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFocusTargetTest {
    @Test
    fun `suggestions take priority over every other focus target`() {
        assertEquals(
            SearchFocusTarget.SUGGESTIONS,
            resolveSearchFocusTarget(
                hasSuggestions = true,
                hasQuery = true,
                isAdvancedSearch = true,
                hasAdvancedResults = true,
                hasStandardResults = true,
                hasHistory = true,
            ),
        )
    }

    @Test
    fun `empty query only targets committed history`() {
        assertEquals(
            SearchFocusTarget.HISTORY,
            resolveSearchFocusTarget(
                hasSuggestions = false,
                hasQuery = false,
                isAdvancedSearch = true,
                hasAdvancedResults = true,
                hasStandardResults = true,
                hasHistory = true,
            ),
        )
        assertEquals(
            SearchFocusTarget.NONE,
            resolveSearchFocusTarget(
                hasSuggestions = false,
                hasQuery = false,
                isAdvancedSearch = true,
                hasAdvancedResults = false,
                hasStandardResults = false,
                hasHistory = false,
            ),
        )
    }

    @Test
    fun `search mode chooses its visible result family`() {
        assertEquals(
            SearchFocusTarget.ADVANCED_RESULTS,
            resolveSearchFocusTarget(
                hasSuggestions = false,
                hasQuery = true,
                isAdvancedSearch = true,
                hasAdvancedResults = true,
                hasStandardResults = false,
                hasHistory = false,
            ),
        )
        assertEquals(
            SearchFocusTarget.STANDARD_RESULTS,
            resolveSearchFocusTarget(
                hasSuggestions = false,
                hasQuery = true,
                isAdvancedSearch = false,
                hasAdvancedResults = false,
                hasStandardResults = true,
                hasHistory = false,
            ),
        )
    }

    @Test
    fun `focus only moves while source and target are still valid`() {
        assertTrue(
            canMoveSearchFocus(
                sourceHasFocus = true,
                targetVisible = true,
                targetHasItems = true,
            ),
        )
        assertFalse(
            canMoveSearchFocus(
                sourceHasFocus = false,
                targetVisible = true,
                targetHasItems = true,
            ),
        )
        assertFalse(
            canMoveSearchFocus(
                sourceHasFocus = true,
                targetVisible = false,
                targetHasItems = true,
            ),
        )
        assertFalse(
            canMoveSearchFocus(
                sourceHasFocus = true,
                targetVisible = true,
                targetHasItems = false,
            ),
        )
    }
}
