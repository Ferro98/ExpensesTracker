package com.example.expensestracker.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.Expense

/** How an expense's sharing status matches the "Tutte / Personali / Condivise" chip. */
enum class ShareScope { ALL, PERSONAL, SHARED }

/** How an expense's payer matches the "Pagate da me / da partner" chip - only offered in a group. */
enum class PayerScope { ALL, ME, PARTNER }

/** Everything that narrows down the month's expense list, bundled so History can pass it around and check it in one place. */
data class HistoryFilterState(
    val searchText: String = "",
    val scope: ShareScope = ShareScope.ALL,
    val payer: PayerScope = PayerScope.ALL,
    val categoryIds: Set<String> = emptySet()
) {
    val isActive: Boolean
        get() = searchText.isNotBlank() || scope != ShareScope.ALL || payer != PayerScope.ALL || categoryIds.isNotEmpty()
}

/**
 * Search box + filter chips, pinned above the month pager (not inside a page) so typing a search
 * or picking a filter survives swiping between months instead of resetting per page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterBar(
    state: HistoryFilterState,
    onStateChange: (HistoryFilterState) -> Unit,
    categories: List<CategorySpending>,
    inGroup: Boolean,
    partnerName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = state.searchText,
            onValueChange = { onStateChange(state.copy(searchText = it)) },
            placeholder = { Text(stringResource(R.string.search_expenses_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchText.isNotEmpty()) {
                    IconButton(onClick = { onStateChange(state.copy(searchText = "")) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.scope == ShareScope.ALL,
                onClick = { onStateChange(state.copy(scope = ShareScope.ALL)) },
                label = { Text(stringResource(R.string.filter_scope_all)) }
            )
            FilterChip(
                selected = state.scope == ShareScope.PERSONAL,
                onClick = { onStateChange(state.copy(scope = ShareScope.PERSONAL)) },
                label = { Text(stringResource(R.string.personal_label)) }
            )
            FilterChip(
                selected = state.scope == ShareScope.SHARED,
                onClick = { onStateChange(state.copy(scope = ShareScope.SHARED)) },
                label = { Text(stringResource(R.string.dashboard_shared_category)) }
            )
            if (inGroup) {
                FilterChip(
                    selected = state.payer == PayerScope.ME,
                    onClick = { onStateChange(state.copy(payer = if (state.payer == PayerScope.ME) PayerScope.ALL else PayerScope.ME)) },
                    label = { Text(stringResource(R.string.filter_paid_by_me)) }
                )
                FilterChip(
                    selected = state.payer == PayerScope.PARTNER,
                    onClick = { onStateChange(state.copy(payer = if (state.payer == PayerScope.PARTNER) PayerScope.ALL else PayerScope.PARTNER)) },
                    label = { Text(stringResource(R.string.filter_paid_by_partner, partnerName)) }
                )
            }
        }

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val selected = category.categoryId in state.categoryIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newIds = if (selected) state.categoryIds - category.categoryId else state.categoryIds + category.categoryId
                            onStateChange(state.copy(categoryIds = newIds))
                        },
                        label = { Text("${category.icon} ${category.name}") }
                    )
                }
            }
        }
    }
}

/** Whether [expense] passes every active filter in [state]. [categoryIdOf] resolves an expense to the viewer's own category (see CategoryResolver via MonthUiState.categoryExpenses) - never raw `expense.categoryId`. */
fun matchesHistoryFilters(
    expense: Expense,
    state: HistoryFilterState,
    categoryIdOf: (String) -> String?,
    myUid: String,
    partnerUid: String?,
    localizedCategoryName: String
): Boolean {
    val matchesScope = when (state.scope) {
        ShareScope.ALL -> true
        ShareScope.PERSONAL -> !expense.isShared
        ShareScope.SHARED -> expense.isShared
    }
    val matchesPayer = when (state.payer) {
        PayerScope.ALL -> true
        PayerScope.ME -> expense.paidByUid == myUid
        PayerScope.PARTNER -> partnerUid != null && expense.paidByUid == partnerUid
    }
    val matchesCategory = state.categoryIds.isEmpty() || categoryIdOf(expense.id) in state.categoryIds
    val query = state.searchText.trim()
    val matchesSearch = query.isBlank() ||
        expense.note?.contains(query, ignoreCase = true) == true ||
        localizedCategoryName.contains(query, ignoreCase = true)
    return matchesScope && matchesPayer && matchesCategory && matchesSearch
}
