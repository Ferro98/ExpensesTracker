package com.example.expensestracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expensestracker.R

sealed class Screen(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", R.string.nav_home, Icons.Default.Home)
    object Recurring : Screen("recurring", R.string.nav_recurring, Icons.Default.Repeat)
    object Categories : Screen("categories", R.string.nav_categories, Icons.Default.Category)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)

    companion object {
        // `by lazy` avoids a Kotlin sealed-class initialization-order trap: eagerly building
        // this list inside the companion's own <clinit> can run while `Screen` (the shared
        // superclass) is still initializing, leaving one of the sibling objects null.
        val bottomBarItems: List<Screen> by lazy { listOf(Dashboard, Recurring, Categories, Settings) }
    }
}
