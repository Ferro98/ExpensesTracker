package com.example.expensestracker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.ui.AppViewModelFactory

private enum class Mode { LANDING, CREATE, JOIN }

@Composable
fun OnboardingScreen(factory: AppViewModelFactory) {
    val viewModel: OnboardingViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(Mode.LANDING) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            val createdGroup = uiState.createdGroup
            when {
                createdGroup != null -> InviteCodeStep(
                    code = createdGroup.id,
                    onContinue = { viewModel.confirmGroupCreated(name) }
                )

                mode == Mode.LANDING -> LandingStep(
                    onCreate = { mode = Mode.CREATE },
                    onJoin = { mode = Mode.JOIN }
                )

                mode == Mode.CREATE -> NameAndActionStep(
                    title = "Create a group",
                    description = "Pick the name your partner will see for you.",
                    name = name,
                    onNameChange = { name = it },
                    code = null,
                    onCodeChange = {},
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    actionLabel = "Create",
                    onAction = { viewModel.createGroup(name) },
                    onBack = { mode = Mode.LANDING; viewModel.clearError() }
                )

                else -> NameAndActionStep(
                    title = "Join a group",
                    description = "Enter the invite code your partner shared with you.",
                    name = name,
                    onNameChange = { name = it },
                    code = code,
                    onCodeChange = { if (it.length <= 6) code = it.uppercase() },
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    actionLabel = "Join",
                    onAction = { viewModel.joinGroup(code, name) },
                    onBack = { mode = Mode.LANDING; viewModel.clearError() }
                )
            }
        }
    }
}

@Composable
private fun LandingStep(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Expenses Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Track and split trip expenses with one other person.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create a group") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("Join a group") }
    }
}

@Composable
private fun NameAndActionStep(
    title: String,
    description: String,
    name: String,
    onNameChange: (String) -> Unit,
    code: String?,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (code != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Invite code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAction,
            enabled = !isLoading && name.isNotBlank() && (code == null || code.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(actionLabel)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun InviteCodeStep(code: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Group created!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Share this code with your partner so they can join.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                code,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(code)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy code") }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}
