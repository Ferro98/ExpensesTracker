package com.example.expensestracker.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.localizedCategoryName
import com.example.expensestracker.util.toColor
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MONTH_PAGE_COUNT = 1201
private const val MONTH_INITIAL_PAGE = MONTH_PAGE_COUNT / 2

/** Snapshot taken at the moment a category (or the synthetic "Condivise" bucket) is tapped, so the detail sheet doesn't need to re-derive it reactively. */
private data class CategoryDetailData(val name: String, val icon: String, val colorHex: String, val expenses: List<Expense>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(factory: AppViewModelFactory, onEditExpense: (Expense) -> Unit) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    var showSettlementDialog by remember { mutableStateOf(false) }
    var expenseDetail by remember { mutableStateOf<Expense?>(null) }
    var categoryDetail by remember { mutableStateOf<CategoryDetailData?>(null) }

    val pagerState = rememberPagerState(initialPage = MONTH_INITIAL_PAGE) { MONTH_PAGE_COUNT }
    val coroutineScope = rememberCoroutineScope()

    // Drives the header label + the settlement dialog (both independent of which page is mid-swipe).
    val currentYearMonth = remember(pagerState.currentPage) {
        viewModel.currentMonth.plusMonths((pagerState.currentPage - MONTH_INITIAL_PAGE).toLong())
    }
    val currentUiState by remember(currentYearMonth) { viewModel.uiStateFor(currentYearMonth) }
        .collectAsState(initial = DashboardUiState())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.cd_previous_month))
            }
            Text(
                text = currentUiState.monthLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_next_month))
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val yearMonth = remember(page) { viewModel.currentMonth.plusMonths((page - MONTH_INITIAL_PAGE).toLong()) }
            val uiState by remember(yearMonth) { viewModel.uiStateFor(yearMonth) }.collectAsState(
                initial = DashboardUiState(monthStart = yearMonth.atDay(1), monthEnd = yearMonth.atEndOfMonth())
            )

            MonthContent(
                uiState = uiState,
                onOpenExpenseDetail = { expenseDetail = it },
                onOpenCategoryDetail = { category ->
                    val expenses = if (category.categoryId == DashboardViewModel.SHARED_BUCKET_ID) {
                        uiState.monthExpenses.filter { it.isShared && it.categoryId !in uiState.categoryIds }
                    } else {
                        uiState.monthExpenses.filter { it.categoryId == category.categoryId }
                    }
                    categoryDetail = CategoryDetailData(category.name, category.icon, category.colorHex, expenses)
                },
                onRecordSettlement = { showSettlementDialog = true }
            )
        }
    }

    if (showSettlementDialog) {
        val partnerUid = currentUiState.partnerUid
        if (partnerUid != null) {
            SettlementDialog(
                myUid = currentUiState.myUid,
                partnerUid = partnerUid,
                partnerName = currentUiState.partnerName,
                currencyRates = currentUiState.currencyRates.map { it.code },
                defaultCurrency = currentUiState.defaultCurrency,
                onDismiss = { showSettlementDialog = false },
                onSave = { fromUid, toUid, amount, currencyCode, note ->
                    viewModel.addSettlement(fromUid, toUid, amount, currencyCode, LocalDate.now(), note)
                    showSettlementDialog = false
                }
            )
        }
    }

    expenseDetail?.let { expense ->
        ExpenseDetailSheet(
            expense = expense,
            myUid = currentUiState.myUid,
            partnerName = currentUiState.partnerName,
            onDismiss = { expenseDetail = null },
            onEdit = { onEditExpense(expense); expenseDetail = null },
            onDelete = { viewModel.deleteExpense(expense.id); expenseDetail = null }
        )
    }

    categoryDetail?.let { detail ->
        CategoryDetailSheet(
            detail = detail,
            myUid = currentUiState.myUid,
            partnerName = currentUiState.partnerName,
            onDismiss = { categoryDetail = null },
            onOpenExpense = { expenseDetail = it }
        )
    }
}

@Composable
private fun MonthContent(
    uiState: DashboardUiState,
    onOpenExpenseDetail: (Expense) -> Unit,
    onOpenCategoryDetail: (CategorySpending) -> Unit,
    onRecordSettlement: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BudgetOverviewCard(uiState.totalSpent, uiState.monthlyBudget, uiState.categoryBudgetTotal) }

        if (uiState.categorySpending.any { it.spent > 0 || it.monthlyBudget != null }) {
            item {
                SectionHeader(stringResource(R.string.by_category), topPadding = 10.dp)
            }
            items(uiState.categorySpending.filter { it.spent > 0 || it.monthlyBudget != null }) { category ->
                CategorySpendingRow(category, onClick = { onOpenCategoryDetail(category) })
            }
        }

        if (uiState.inGroup) {
            item {
                BalanceCard(
                    balance = uiState.balance,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onRecordSettlement = onRecordSettlement
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.recent_expenses), topPadding = 10.dp)
        }

        if (uiState.monthExpenses.isEmpty()) {
            item { EmptyExpensesState() }
        } else {
            items(uiState.monthExpenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onClick = { onOpenExpenseDetail(expense) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(64.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, topPadding: Dp = 0.dp) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = topPadding, bottom = 2.dp)
    )
}

@Composable
private fun EmptyExpensesState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🧾", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.no_expenses_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BalanceCard(balance: Balance, myUid: String, partnerName: String, onRecordSettlement: () -> Unit) {
    val settled = balance.owedByUid == null
    val youOwe = balance.owedByUid == myUid
    val containerColor = when {
        settled -> MaterialTheme.colorScheme.surfaceVariant
        youOwe -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainerColor = when {
        settled -> MaterialTheme.colorScheme.onSurfaceVariant
        youOwe -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val emoji = if (settled) "🤝" else if (youOwe) "💸" else "🎉"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = onContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.balance_label).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainerColor.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(6.dp))
            val text = when {
                settled -> stringResource(R.string.balance_settled_up)
                youOwe -> stringResource(R.string.balance_you_owe, partnerName, formatMoney(balance.netAmount))
                else -> stringResource(R.string.balance_owes_you, partnerName, formatMoney(balance.netAmount))
            }
            Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = onContainerColor)
            if (!settled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    // This is a running lifetime total across every shared expense and settlement,
                    // not scoped to the month being viewed - unlike the numbers above it.
                    stringResource(R.string.balance_lifetime_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerColor.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRecordSettlement,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainerColor),
                border = BorderStroke(1.dp, onContainerColor.copy(alpha = 0.4f))
            ) {
                Text(stringResource(R.string.record_settlement))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettlementDialog(
    myUid: String,
    partnerUid: String,
    partnerName: String,
    currencyRates: List<String>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (fromUid: String, toUid: String, amount: Double, currencyCode: String, note: String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var iPaid by remember { mutableStateOf(true) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var note by remember { mutableStateOf("") }

    val amount = amountText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_settlement)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = iPaid, onClick = { iPaid = true }, label = { Text(stringResource(R.string.settlement_i_paid, partnerName)) })
                    FilterChip(selected = !iPaid, onClick = { iPaid = false }, label = { Text(stringResource(R.string.settlement_partner_paid_me, partnerName)) })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d{0,7}([.,]\\d{0,2})?$"))) amountText = input
                    },
                    label = { Text(stringResource(R.string.label_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencyRates.forEach { code ->
                        FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.label_note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amount != null && amount > 0) {
                        val fromUid = if (iPaid) myUid else partnerUid
                        val toUid = if (iPaid) partnerUid else myUid
                        onSave(fromUid, toUid, amount, currency, note)
                    }
                },
                enabled = amount != null && amount > 0
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun BudgetOverviewCard(totalSpent: Double, monthlyBudget: Double?, categoryBudgetTotal: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.spent_this_month).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatMoney(totalSpent),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (monthlyBudget != null && monthlyBudget > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                val progress = (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f)
                val overBudget = totalSpent > monthlyBudget
                val progressColor = when {
                    overBudget -> MaterialTheme.colorScheme.error
                    progress > 0.8f -> Color(0xFFF9A825)
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val remaining = monthlyBudget - totalSpent
                Text(
                    text = if (remaining >= 0)
                        stringResource(R.string.budget_left, formatMoney(monthlyBudget), formatMoney(remaining))
                    else
                        stringResource(R.string.budget_over_by, formatMoney(monthlyBudget), formatMoney(-remaining)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (categoryBudgetTotal > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.category_budgets_label), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    val allocationProgress = (categoryBudgetTotal / monthlyBudget).toFloat().coerceIn(0f, 1f)
                    val overAllocated = categoryBudgetTotal > monthlyBudget
                    LinearProgressIndicator(
                        progress = { allocationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (overAllocated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val unallocated = monthlyBudget - categoryBudgetTotal
                    Text(
                        text = if (unallocated >= 0)
                            stringResource(R.string.allocated_unallocated, formatMoney(categoryBudgetTotal), formatMoney(unallocated))
                        else
                            stringResource(R.string.allocated_over_by, formatMoney(categoryBudgetTotal), formatMoney(-unallocated)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overAllocated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_personal_budget),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategorySpendingRow(category: CategorySpending, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(category.colorHex.toColor().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.icon)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (category.monthlyBudget != null)
                        "${formatMoney(category.spent)} / ${formatMoney(category.monthlyBudget)}"
                    else
                        formatMoney(category.spent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (category.monthlyBudget != null && category.monthlyBudget > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = (category.spent / category.monthlyBudget).toFloat().coerceIn(0f, 1f)
                val overBudget = category.spent > category.monthlyBudget
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (overBudget) MaterialTheme.colorScheme.error else category.colorHex.toColor(),
                    trackColor = category.colorHex.toColor().copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, myUid: String, partnerName: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(expense.categoryColorHex.toColor().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(expense.categoryIcon)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // The note is what the user actually cares about at a glance - the category (already
                // shown as the icon) rides along as a small subtitle instead of pushing the note out.
                val categoryName = localizedCategoryName(expense.categoryName)
                val primaryText = expense.note?.takeIf { it.isNotBlank() } ?: categoryName
                Text(
                    primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val payerLabel = if (expense.paidByUid == myUid) stringResource(R.string.you) else partnerName
                val sharedLabel = if (expense.isShared) stringResource(R.string.paid_by_partner, payerLabel) else stringResource(R.string.personal_label)
                val subtitle = "${expense.categoryIcon} $categoryName · ${formatShortDate(expense.localDate)} · $sharedLabel"
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(expense.amountInBaseCurrency, DefaultUserData.BASE_CURRENCY),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // The original entered currency only shows up here when it differs from the base
                // currency, so an amount typed directly in EUR doesn't get a redundant second line.
                if (expense.currencyCode != DefaultUserData.BASE_CURRENCY) {
                    Text(
                        formatMoney(expense.amount, expense.currencyCode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailSheet(
    expense: Expense,
    myUid: String,
    partnerName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(expense.categoryColorHex.toColor().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(expense.categoryIcon, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(localizedCategoryName(expense.categoryName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatShortDate(expense.localDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))

            Text(
                formatMoney(expense.amountInBaseCurrency, DefaultUserData.BASE_CURRENCY),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            if (expense.currencyCode != DefaultUserData.BASE_CURRENCY) {
                Text(
                    formatMoney(expense.amount, expense.currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            expense.note?.takeIf { it.isNotBlank() }?.let {
                DetailRow(stringResource(R.string.label_note_optional), it)
            }
            val payerLabel = if (expense.paidByUid == myUid) stringResource(R.string.you) else partnerName
            val sharedLabel = if (expense.isShared) stringResource(R.string.paid_by_partner, payerLabel) else stringResource(R.string.personal_label)
            DetailRow(stringResource(R.string.label_status), sharedLabel)
            if (expense.isShared && expense.payerShare != 0.5) {
                val myPercent = (if (expense.paidByUid == myUid) expense.payerShare else 1 - expense.payerShare) * 100
                DetailRow(stringResource(R.string.custom_split_label), "${myPercent.toInt()}% / ${100 - myPercent.toInt()}%")
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cd_delete))
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cd_edit))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetailSheet(
    detail: CategoryDetailData,
    myUid: String,
    partnerName: String,
    onDismiss: () -> Unit,
    onOpenExpense: (Expense) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(detail.colorHex.toColor().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(detail.icon, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(detail.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatMoney(detail.expenses.sumOf { it.shareFor(myUid) }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (detail.expenses.isEmpty()) {
                Text(
                    stringResource(R.string.no_expenses_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.expenses.sortedByDescending { it.localDate }.forEach { expense ->
                        ExpenseRow(
                            expense = expense,
                            myUid = myUid,
                            partnerName = partnerName,
                            onClick = { onOpenExpense(expense) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
