package com.example.expensestracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.expensestracker.data.settings.ThemeMode
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.ui.addexpense.AddExpenseSheet
import com.example.expensestracker.ui.addexpense.AddExpenseViewModel
import com.example.expensestracker.ui.categories.CategoriesScreen
import com.example.expensestracker.ui.dashboard.DashboardScreen
import com.example.expensestracker.ui.navigation.Screen
import com.example.expensestracker.ui.recurring.RecurringScreen
import com.example.expensestracker.ui.settings.SettingsScreen
import com.example.expensestracker.ui.theme.ExpensesTrackerTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ExpensesTrackerApp
        val factory = AppViewModelFactory(app)
        setContent {
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ExpensesTrackerTheme(darkTheme = darkTheme) {
                ExpensesTrackerRoot(factory)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesTrackerRoot(factory: AppViewModelFactory) {
    val navController = rememberNavController()
    var showAddExpense by remember { mutableStateOf(false) }
    val addExpenseViewModel: AddExpenseViewModel = viewModel(factory = factory)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Dashboard.route
    val currentTitle = Screen.bottomBarItems.firstOrNull { it.route == currentRoute }?.let {
        when (it) {
            Screen.Dashboard -> "My Expenses"
            Screen.Recurring -> "Recurring Expenses"
            Screen.Categories -> "Categories & Budget"
            Screen.Settings -> "Settings"
        }
    } ?: "My Expenses"

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(currentTitle) })
        },
        bottomBar = {
            NavigationBar {
                Screen.bottomBarItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            // Categories and Recurring manage their own FAB for adding an item;
            // showing this one too would stack two FABs in the same corner.
            if (currentRoute == Screen.Dashboard.route) {
                FloatingActionButton(onClick = { showAddExpense = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(factory) }
            composable(Screen.Recurring.route) { RecurringScreen(factory) }
            composable(Screen.Categories.route) { CategoriesScreen(factory) }
            composable(Screen.Settings.route) { SettingsScreen(factory) }
        }
    }

    if (showAddExpense) {
        AddExpenseSheet(viewModel = addExpenseViewModel, onDismiss = { showAddExpense = false })
    }
}
