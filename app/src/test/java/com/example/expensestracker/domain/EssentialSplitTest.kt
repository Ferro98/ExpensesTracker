package com.example.expensestracker.domain

import com.example.expensestracker.R
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EssentialSplitTest {
    private val rent = Category(id = "rent", name = "Alloggio", essential = true)
    private val fun_ = Category(id = "fun", name = "Intrattenimento")
    private val other = Category(id = "other", name = "Altro")
    private val categories = listOf(rent, fun_, other)

    private fun expense(categoryId: String, name: String, amount: Double, shared: Boolean = false, paidBy: String = "me") =
        Expense(
            id = "$categoryId-$amount",
            categoryId = categoryId,
            categoryName = name,
            amountInBaseCurrency = amount,
            paidByUid = paidBy,
            isShared = shared,
            date = "2026-11-10"
        )

    @Test
    fun splitsByTheCategoryFlag() {
        val split = EssentialSplitCalculator.compute(
            listOf(expense("rent", "Alloggio", 600.0), expense("fun", "Intrattenimento", 200.0)),
            categories, emptyMap(), "me"
        )
        assertEquals(600.0, split.essential, 1e-9)
        assertEquals(200.0, split.extra, 1e-9)
        assertEquals(0.75, split.essentialShare!!, 1e-9)
    }

    @Test
    fun partnerExpenseFollowsTheViewersOwnCategoryByName() {
        // The partner's "Alloggio" has an id that exists only in their list; it resolves by name
        // to mine, which is essential.
        val split = EssentialSplitCalculator.compute(
            listOf(expense("theirs", "Alloggio", 100.0, shared = true, paidBy = "partner")),
            categories, emptyMap(), "me"
        )
        assertEquals(50.0, split.essential, 1e-9)
        assertEquals(0.0, split.extra, 1e-9)
    }

    @Test
    fun anAliasCanMoveAPartnerCategoryIntoAnEssentialOne() {
        val split = EssentialSplitCalculator.compute(
            listOf(expense("theirs", "Casa", 100.0, shared = true, paidBy = "partner")),
            categories, mapOf("theirs" to "rent"), "me"
        )
        assertEquals(50.0, split.essential, 1e-9)
    }

    @Test
    fun unresolvedAndOtherCountAsExtra() {
        val split = EssentialSplitCalculator.compute(
            listOf(expense("zzz", "Boh", 40.0)),
            categories, emptyMap(), "me"
        )
        assertEquals(0.0, split.essential, 1e-9)
        assertEquals(40.0, split.extra, 1e-9)
    }

    @Test
    fun nothingSpentHasNoShare() {
        assertNull(EssentialSplit().essentialShare)
    }

    @Test
    fun suggestedEssentialsAreTheNeedsNotFood() {
        assertTrue(DefaultUserData.isSuggestedEssential("Alloggio"))
        assertTrue(DefaultUserData.isSuggestedEssential("Bills & Subscriptions"))
        assertTrue(DefaultUserData.isSuggestedEssential("Salute"))
        assertTrue(DefaultUserData.isSuggestedEssential("Trasporti"))
        assertFalse(DefaultUserData.isSuggestedEssential("Cibo e Ristoranti"))
        assertFalse(DefaultUserData.isSuggestedEssential("Spesa"))
        assertEquals(R.string.default_category_health, DefaultUserData.knownDefaultNameVariants["Salute"])
    }
}
