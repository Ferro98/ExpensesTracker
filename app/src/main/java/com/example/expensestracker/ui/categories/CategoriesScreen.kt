package com.example.expensestracker.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.toColor

private val presetIcons = listOf("🍔", "🚌", "🏨", "🛍️", "🎉", "📱", "💊", "✈️", "📦", "☕", "🎮", "📚", "🐶", "🏋️", "🎵", "💡")
private val presetColors = listOf(
    "#FF7043", "#42A5F5", "#AB47BC", "#EC407A", "#FFA726", "#5C6BC0",
    "#26A69A", "#29B6F6", "#78909C", "#8D6E63", "#66BB6A", "#EF5350"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(factory: AppViewModelFactory) {
    val viewModel: CategoriesViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var budgetText by remember(uiState.monthlyBudget) {
        mutableStateOf(uiState.monthlyBudget?.let { String.format("%.2f", it) } ?: "")
    }
    var budgetCurrency by remember { mutableStateOf("EUR") }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_category)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.total_monthly_budget), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = budgetText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d{0,7}([.,]\\d{0,2})?$"))) {
                                        budgetText = input
                                    }
                                },
                                label = { Text(stringResource(R.string.label_amount)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                val value = budgetText.replace(',', '.').toDoubleOrNull()
                                viewModel.setMonthlyBudget(value, budgetCurrency)
                            }) { Text(stringResource(R.string.action_save)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.currencyRates.forEach { rate ->
                                FilterChip(
                                    selected = budgetCurrency == rate.code,
                                    onClick = { budgetCurrency = rate.code },
                                    label = { Text(rate.code) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(stringResource(R.string.categories_label), style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.categories, key = { it.id }) { category ->
                CategoryRow(
                    category = category,
                    budget = uiState.categoryBudgets[category.id],
                    onEdit = { editingCategory = category },
                    onDelete = { viewModel.deleteCategory(category) }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        CategoryEditDialog(
            initial = null,
            initialBudget = null,
            currencyRates = uiState.currencyRates,
            onDismiss = { showAddDialog = false },
            onSave = { name, icon, color, budget, budgetCcy ->
                viewModel.addCategory(name, icon, color, budget, budgetCcy)
                showAddDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        CategoryEditDialog(
            initial = category,
            initialBudget = uiState.categoryBudgets[category.id],
            currencyRates = uiState.currencyRates,
            onDismiss = { editingCategory = null },
            onSave = { name, icon, color, budget, budgetCcy ->
                viewModel.updateCategory(category, name, icon, color, budget, budgetCcy)
                editingCategory = null
            }
        )
    }
}

@Composable
private fun CategoryRow(category: Category, budget: Double?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(category.colorHex.toColor().copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.icon)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, fontWeight = FontWeight.Medium)
                Text(
                    text = budget?.let { stringResource(R.string.budget_prefix, formatMoney(it)) } ?: stringResource(R.string.no_budget),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    initial: Category?,
    initialBudget: Double?,
    currencyRates: List<CurrencyRate>,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String, budget: Double?, budgetCurrency: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: presetIcons.first()) }
    var color by remember { mutableStateOf(initial?.colorHex ?: presetColors.first()) }
    var budgetText by remember { mutableStateOf(initialBudget?.let { String.format("%.2f", it) } ?: "") }
    var budgetCurrency by remember { mutableStateOf("EUR") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_category_title else R.string.edit_category_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.icon_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetIcons.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (icon == candidate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (icon == candidate) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { icon = candidate },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = candidate)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.color_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetColors.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(candidate.toColor())
                                .border(
                                    width = if (color == candidate) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { color = candidate }
                        ) {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d{0,7}([.,]\\d{0,2})?$"))) {
                            budgetText = input
                        }
                    },
                    label = { Text(stringResource(R.string.monthly_budget_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    currencyRates.forEach { rate ->
                        FilterChip(
                            selected = budgetCurrency == rate.code,
                            onClick = { budgetCurrency = rate.code },
                            label = { Text(rate.code) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), icon, color, budgetText.replace(',', '.').toDoubleOrNull(), budgetCurrency)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
