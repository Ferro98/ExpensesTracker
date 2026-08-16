package com.example.expensestracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.settings.ThemeMode
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.ui.onboarding.GroupSetupSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(factory: AppViewModelFactory) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val currencyRates by viewModel.currencyRates.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val group by viewModel.group.collectAsState()
    val clipboard = LocalClipboardManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingRate by remember { mutableStateOf<CurrencyRate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val isLeaving by viewModel.isLeaving.collectAsState()

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearStatus()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Group", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (viewModel.inGroup) {
                            val members = group?.memberNames?.values?.toList().orEmpty()
                            Text(
                                if (members.isEmpty()) "Loading…" else members.joinToString(" & "),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Invite code: ${group?.id ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { group?.id?.let { clipboard.setText(AnnotatedString(it)) } },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Copy invite code") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showLeaveConfirm = true },
                                enabled = !isLeaving,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Leave group") }
                        } else {
                            GroupSetupSection(factory)
                        }
                    }
                }
            }

            item {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Light") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Dark") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        leadingIcon = { Icon(Icons.Default.BrightnessAuto, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("System") }
                    )
                }
            }

            item {
                Text("Currencies", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Base currency: ${DefaultUserData.BASE_CURRENCY}. The rate is how many euros 1 unit of that currency is worth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(currencyRates, key = { it.code }) { rate ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rate.code, fontWeight = FontWeight.Medium)
                            Text(
                                "1 ${rate.code} = ${String.format("%.4f", rate.rateToBase)} EUR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { editingRate = rate }) { Text("Edit") }
                        if (rate.code != DefaultUserData.BASE_CURRENCY) {
                            IconButton(onClick = { viewModel.deleteCurrency(rate.code) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add currency")
                    }
                    Button(
                        onClick = { viewModel.refreshRates() },
                        enabled = !isRefreshing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh online")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    editingRate?.let { rate ->
        RateEditDialog(
            code = rate.code,
            initialRate = rate.rateToBase,
            onDismiss = { editingRate = null },
            onSave = { newRate ->
                viewModel.setRate(rate.code, newRate)
                editingRate = null
            }
        )
    }

    if (showAddDialog) {
        AddCurrencyDialog(
            existingCodes = currencyRates.map { it.code },
            onDismiss = { showAddDialog = false },
            onSave = { code, rate ->
                viewModel.addCurrency(code, rate)
                showAddDialog = false
            }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave this group?") },
            text = { Text("Your own expenses and budget stay on this device. You'll need the invite code to rejoin.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.leaveGroup {}
                }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RateEditDialog(code: String, initialRate: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var rateText by remember { mutableStateOf(String.format("%.4f", initialRate)) }
    val rate = rateText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $code rate") },
        text = {
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it },
                label = { Text("1 $code in EUR") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (rate != null && rate > 0) onSave(rate) }, enabled = rate != null && rate > 0) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddCurrencyDialog(
    existingCodes: List<String>,
    onDismiss: () -> Unit,
    onSave: (code: String, rate: Double) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    val rate = rateText.replace(',', '.').toDoubleOrNull()
    val validCode = code.trim().uppercase().length == 3 && code.trim().uppercase() !in existingCodes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New currency") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 3) code = it.uppercase() },
                    label = { Text("Code (e.g. USD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("1 unit in EUR") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (validCode && rate != null && rate > 0) onSave(code.trim().uppercase(), rate) },
                enabled = validCode && rate != null && rate > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
