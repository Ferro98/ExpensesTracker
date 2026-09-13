package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.ui.theme.MoneyStyle
import com.example.expensestracker.util.formatMoney

/**
 * The recurring "EUR primary, original currency small underneath" amount block - previously
 * copy-pasted (identically) into every expense/recurring row and detail sheet. The secondary line
 * only renders when [originalCurrencyCode] differs from the base currency, so an amount already
 * entered in EUR doesn't get a redundant identical second line.
 */
@Composable
fun AmountText(
    amountInBase: Double,
    originalAmount: Double? = null,
    originalCurrencyCode: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyStyle.Medium,
    color: Color = LocalContentColor.current,
    secondaryStyle: TextStyle = MoneyStyle.Small,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalAlignment: Alignment.Horizontal = Alignment.End
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(formatMoney(amountInBase, DefaultUserData.BASE_CURRENCY), style = style, color = color)
        if (originalAmount != null && originalCurrencyCode != null && originalCurrencyCode != DefaultUserData.BASE_CURRENCY) {
            Text(formatMoney(originalAmount, originalCurrencyCode), style = secondaryStyle, color = secondaryColor)
        }
    }
}
