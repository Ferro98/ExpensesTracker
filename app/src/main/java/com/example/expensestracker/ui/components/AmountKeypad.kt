package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.util.decimalSeparator

/** Most an amount can be: 7 digits before the separator, 2 after. */
private const val MAX_INTEGER_DIGITS = 7
private const val MAX_DECIMALS = 2

/**
 * Appends [key] to [current] if the result is still a valid amount, otherwise returns [current]
 * unchanged - the keypad simply refuses the keystroke instead of showing an error.
 */
fun appendAmountKey(current: String, key: Char, separator: Char): String {
    if (key == separator) {
        if (current.contains(separator)) return current
        return if (current.isEmpty()) "0$separator" else current + separator
    }
    if (!key.isDigit()) return current
    val separatorIndex = current.indexOf(separator)
    if (separatorIndex >= 0) {
        if (current.length - separatorIndex - 1 >= MAX_DECIMALS) return current
    } else {
        if (current.length >= MAX_INTEGER_DIGITS) return current
        // Nobody types a leading zero on purpose; "0" then "5" means 5.
        if (current == "0") return key.toString()
    }
    return current + key
}

/**
 * A numeric pad drawn in the sheet instead of the system keyboard, which on a phone covers half
 * the screen - the whole point of the quick-add layout is that entering an expense needs no
 * scrolling, and that only holds if the amount field doesn't summon the IME.
 */
@Composable
fun AmountKeypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val separator = decimalSeparator()
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(separator, '0')
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    KeypadKey(modifier = Modifier.weight(1f), onClick = { onKey(key) }) {
                        Text(
                            key.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (row.size < 3) {
                    KeypadKey(modifier = Modifier.weight(1f), onClick = onBackspace) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.cd_backspace)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        content()
    }
}
