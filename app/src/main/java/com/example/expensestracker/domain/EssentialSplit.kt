package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.Expense

/** How much of a period's spend went on needs versus everything else, in the viewer's own share of each expense. */
data class EssentialSplit(val essential: Double = 0.0, val extra: Double = 0.0) {
    val total: Double get() = essential + extra

    /** 0..1, or null when there's nothing to take a share of. */
    val essentialShare: Double? get() = if (total > 0) essential / total else null
}

object EssentialSplitCalculator {
    /**
     * [expenses] must already be attributed to the period being summed (see
     * [attributedTo]) so a subscription only brings its own slice. Each expense is classified by the
     * viewer's category it resolves to - the same resolution Stats and budgets use - so a partner's
     * shared expense lands on the right side too. Anything that resolves to no category, or to one
     * not marked essential, is extra: the safe default is to count a need as a want only when told.
     */
    fun compute(expenses: List<Expense>, categories: List<Category>, aliases: Map<String, String>, myUid: String): EssentialSplit {
        var essential = 0.0
        var extra = 0.0
        expenses.forEach { expense ->
            val share = expense.shareFor(myUid)
            if (CategoryResolver.resolve(expense, categories, aliases)?.essential == true) essential += share else extra += share
        }
        return EssentialSplit(essential, extra)
    }
}
