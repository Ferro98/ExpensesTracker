package com.example.expensestracker.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.expensestracker.data.model.DefaultUserData

/**
 * Expenses and recurring templates store a denormalized snapshot of their category's name at
 * creation time, in whichever locale the author's device was in. When a shared expense is viewed
 * by the other group member, that snapshot doesn't follow their own device's locale - this
 * translates it back for display whenever it matches one of the known default category names (in
 * any language this app has shipped), the same table [PersonalDataRepository] already uses to
 * migrate a category's own canonical name. A custom, user-typed category name is left as-is since
 * there's nothing to translate it against.
 */
@Composable
fun localizedCategoryName(storedName: String): String {
    val labelRes = DefaultUserData.knownDefaultNameVariants[storedName] ?: return storedName
    return stringResource(labelRes)
}
