package com.example.expensestracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryColorsTest {
    @Test
    fun newCategoryStartsOnTheFirstColorNobodyUses() {
        assertEquals("#42A5F5", nextUnusedCategoryColor(listOf("#FF7043")))
        assertEquals("#AB47BC", nextUnusedCategoryColor(listOf("#FF7043", "#42A5F5")))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals("#42A5F5", nextUnusedCategoryColor(listOf("#ff7043")))
    }

    @Test
    fun fallsBackToTheFirstPresetWhenEveryOneIsTaken() {
        assertEquals(categoryPresetColors.first(), nextUnusedCategoryColor(categoryPresetColors))
    }

    @Test
    fun customColorsDoNotUseUpAPreset() {
        assertEquals("#FF7043", nextUnusedCategoryColor(listOf("#123456")))
    }
}
