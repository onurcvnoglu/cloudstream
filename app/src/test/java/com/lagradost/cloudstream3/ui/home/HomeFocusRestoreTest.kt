package com.lagradost.cloudstream3.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFocusRestoreTest {
    @Test
    fun `category adapter position includes preview header`() {
        val categories = listOf(
            HomeFocusRestoreCategory("A", listOf("a")),
            HomeFocusRestoreCategory("B", listOf("b")),
        )

        val selection = HomeFocusRestorePlanner.select(
            categories,
            HomeFocusRestoreTarget("B", "b"),
        )

        assertEquals(1, selection?.categoryIndex)
        assertEquals(2, HomeFocusRestorePlanner.adapterPosition(selection!!.categoryIndex, headers = 1))
    }

    @Test
    fun `missing card falls back to first card in the same category`() {
        val selection = HomeFocusRestorePlanner.select(
            listOf(
                HomeFocusRestoreCategory("A", listOf("a1", "a2")),
                HomeFocusRestoreCategory("B", listOf("b1", "b2")),
            ),
            HomeFocusRestoreTarget("B", "removed"),
        )

        assertEquals(HomeFocusRestoreSelection(categoryIndex = 1, cardIndex = 0), selection)
    }

    @Test
    fun `missing category falls back to first non-empty category`() {
        val selection = HomeFocusRestorePlanner.select(
            listOf(
                HomeFocusRestoreCategory("A", emptyList()),
                HomeFocusRestoreCategory("B", listOf("b1")),
                HomeFocusRestoreCategory("C", listOf("c1")),
            ),
            HomeFocusRestoreTarget("removed", "removed"),
        )

        assertEquals(HomeFocusRestoreSelection(categoryIndex = 1, cardIndex = 0), selection)
    }

    @Test
    fun `no accessible category produces no restore selection`() {
        val selection = HomeFocusRestorePlanner.select(
            listOf(
                HomeFocusRestoreCategory("A", emptyList()),
                HomeFocusRestoreCategory("B", emptyList()),
            ),
            HomeFocusRestoreTarget("B", "removed"),
        )

        assertNull(selection)
    }
}
