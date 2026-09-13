package com.example.expensestracker.domain

import com.example.expensestracker.R
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.model.Expense

/**
 * Categories are private per user, so a shared expense the other group member created carries a
 * categoryId that only exists in *their* list. Rather than showing it as a separate bucket, this
 * maps it onto the viewer's own categories by name: known default names match across every
 * language the app has shipped (so their "Food & Dining" lands in my "Cibo e Ristoranti"), custom
 * names match case-insensitively, and anything left over falls into the viewer's "Other".
 */
object CategoryResolver {
    private fun key(name: String): Any =
        DefaultUserData.knownDefaultNameVariants[name] ?: name.trim().lowercase()

    /** The viewer's category this expense counts toward, or null only if the viewer has no "Other" category either. */
    fun resolve(expense: Expense, categories: List<Category>): Category? {
        categories.firstOrNull { it.id == expense.categoryId }?.let { return it }
        val wanted = key(expense.categoryName)
        categories.firstOrNull { key(it.name) == wanted }?.let { return it }
        return categories.firstOrNull { key(it.name) == R.string.default_category_other }
    }
}
