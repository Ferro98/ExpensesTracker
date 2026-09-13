package com.example.expensestracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expensestracker.R

/**
 * [Home]/[History]/[Stats]/[More] are the four bottom tabs; the rest are pushed on top of "More"
 * and show a back arrow instead of being reachable directly. Management screens (recurring,
 * categories, settings) used to be tabs - they're destinations you visit occasionally, so they
 * moved behind More to free the bar for the three "look at my money" views.
 */
sealed class Screen(val route: String, @StringRes val titleRes: Int) {
    object Home : Screen("home", R.string.title_dashboard)
    object History : Screen("history", R.string.title_history)
    object Stats : Screen("stats", R.string.title_stats)
    object More : Screen("more", R.string.title_more)

    object Recurring : Screen("recurring", R.string.title_recurring)
    object Categories : Screen("categories", R.string.title_categories)
    object Settings : Screen("settings", R.string.title_settings)

    companion object {
        // `by lazy` avoids a Kotlin sealed-class initialization-order trap: eagerly building
        // these lists inside the companion's own <clinit> can run while `Screen` (the shared
        // superclass) is still initializing, leaving one of the sibling objects null.
        val bottomTabs: List<BottomTab> by lazy {
            listOf(
                BottomTab(Home, R.string.nav_home, Icons.Default.Home),
                BottomTab(History, R.string.nav_history, Icons.AutoMirrored.Filled.ReceiptLong),
                BottomTab(Stats, R.string.nav_stats, Icons.Default.PieChart),
                BottomTab(More, R.string.nav_more, Icons.Default.MoreHoriz)
            )
        }

        private val all: List<Screen> by lazy {
            listOf(Home, History, Stats, More, Recurring, Categories, Settings)
        }

        fun fromRoute(route: String?): Screen = all.firstOrNull { it.route == route } ?: Home
    }

    /** Screens outside the bar are entered from More, so they get a back arrow and no tab highlight. */
    val isTab: Boolean get() = bottomTabs.any { it.screen == this }
}

data class BottomTab(val screen: Screen, @StringRes val labelRes: Int, val icon: ImageVector)
