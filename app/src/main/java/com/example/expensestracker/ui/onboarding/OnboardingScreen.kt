package com.example.expensestracker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.ui.AppViewModelFactory

private enum class Mode { LANDING, CREATE, JOIN }

/** Embedded in Settings' "Group" section (not a standalone screen - group membership is optional). */
@Composable
fun GroupSetupSection(factory: AppViewModelFactory) {
    val viewModel: OnboardingViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(Mode.LANDING) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val createdGroup = uiState.createdGroup
    when {
        createdGroup != null -> InviteCodeCard(
            code = createdGroup.id,
            onContinue = { viewModel.confirmGroupCreated(name) }
        )

        mode == Mode.LANDING -> LandingCard(
            onCreate = { mode = Mode.CREATE },
            onJoin = { mode = Mode.JOIN }
        )

        mode == Mode.CREATE -> NameAndActionCard(
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

        else -> NameAndActionCard(
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

@Composable
private fun LandingCard(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column {
        Text(
            "Share this trip's expenses with one other person - split costs, see a running balance, and settle up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create a group") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("Join a group") }
    }
}

@Composable
private fun NameAndActionCard(
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
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (code != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Invite code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAction,
            enabled = !isLoading && name.isNotBlank() && (code == null || code.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text(actionLabel)
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun InviteCodeCard(code: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column {
        Text("Group created!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Share this code with your partner so they can join.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Card {
            Text(
                code,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.weight(1f)
            ) { Text("Copy code") }
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) { Text("Continue") }
        }
    }
}
