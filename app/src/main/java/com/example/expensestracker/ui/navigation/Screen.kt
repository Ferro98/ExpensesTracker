package com.example.expensestracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Recurring : Screen("recurring", "Recurring", Icons.Default.Repeat)
    object Categories : Screen("categories", "Categories", Icons.Default.Category)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        // `by lazy` avoids a Kotlin sealed-class initialization-order trap: eagerly building
        // this list inside the companion's own <clinit> can run while `Screen` (the shared
        // superclass) is still initializing, leaving one of the sibling objects null.
        val bottomBarItems: List<Screen> by lazy { listOf(Dashboard, Recurring, Categories, Settings) }
    }
}
