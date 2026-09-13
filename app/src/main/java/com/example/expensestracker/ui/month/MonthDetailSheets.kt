package com.example.expensestracker.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.ui.components.CategoryDetailData
import com.example.expensestracker.ui.components.CategoryDetailSheet
import com.example.expensestracker.ui.components.ExpenseDetailSheet

/** Which detail sheet, if any, is open on top of a month view. */
@Stable
class MonthDetailState {
    var expense by mutableStateOf<Expense?>(null)
        private set
    var category by mutableStateOf<CategoryDetailData?>(null)
        private set

    fun openExpense(expense: Expense) {
        this.expense = expense
    }

    /** Uses the already-grouped [MonthUiState.categoryExpenses]: never filter by `categoryId` in UI (see CategoryResolver). */
    fun openCategory(category: CategorySpending, uiState: MonthUiState) {
        this.category = CategoryDetailData(
            name = category.name,
            icon = category.icon,
            colorHex = category.colorHex,
            expenses = uiState.categoryExpenses[category.categoryId].orEmpty()
        )
    }

    fun closeExpense() {
        expense = null
    }

    fun closeCategory() {
        category = null
    }
}

@Composable
fun rememberMonthDetailState(): MonthDetailState = remember { MonthDetailState() }

/**
 * Hosts the expense and category sheets for Home, History and Stats - all three offer the same
 * two drill-downs, so the wiring (including "tap an expense inside a category sheet") lives once
 * here instead of being repeated per screen.
 */
@Composable
fun MonthDetailSheets(
    state: MonthDetailState,
    uiState: MonthUiState,
    onEditExpense: (Expense) -> Unit,
    onDuplicateExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    state.expense?.let { expense ->
        ExpenseDetailSheet(
            expense = expense,
            myUid = uiState.myUid,
            partnerName = uiState.partnerName,
            onDismiss = { state.closeExpense() },
            onEdit = { onEditExpense(expense); state.closeExpense() },
            onDelete = { onDeleteExpense(expense.id); state.closeExpense() },
            onDuplicate = { onDuplicateExpense(expense); state.closeExpense() }
        )
    }

    state.category?.let { detail ->
        CategoryDetailSheet(
            detail = detail,
            myUid = uiState.myUid,
            partnerName = uiState.partnerName,
            onDismiss = { state.closeCategory() },
            onOpenExpense = { state.openExpense(it) }
        )
    }
}
