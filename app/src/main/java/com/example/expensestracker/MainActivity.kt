package com.example.expensestracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ExpensesTrackerApp
        setContent {
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            var myUid by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val uid = app.authRepository.ensureSignedIn()
                app.personalDataRepositoryFor(uid).seedDefaultsIfNeeded()
                myUid = uid
            }
            val groupId by app.settingsRepository.groupId.collectAsState(initial = null)

            ExpensesTrackerTheme(darkTheme = darkTheme) {
                val uid = myUid
                if (uid == null) {
                    SplashScreen()
                } else {
                    val factory = remember(groupId, uid) { AppViewModelFactory(app, groupId, uid) }
                    // Screen ViewModels are cached per nav back-stack entry and only pull from the
                    // factory the first time they're created, so joining/creating/leaving a group
                    // (which changes groupContext) wouldn't otherwise be picked up by an
                    // already-mounted screen. Keying on groupId forces the whole nav tree - and
                    // every ViewModel in it - to be recreated when group membership changes.
                    key(groupId, uid) {
                        ExpensesTrackerRoot(factory, vmKey = "$groupId:$uid")
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesTrackerRoot(factory: AppViewModelFactory, vmKey: String) {
    val navController = rememberNavController()
    var showAddExpense by remember { mutableStateOf(false) }
    // Unlike the per-screen ViewModels (each scoped to its own NavBackStackEntry, which gets
    // recreated whenever the NavHost itself is rebuilt below), this ViewModel is requested
    // directly here - outside any nav route - so it resolves to the Activity's own, long-lived
    // ViewModelStore. Without an explicit key tied to group identity, it would keep returning
    // the instance built with whatever groupContext was active the first time this screen ever
    // ran, silently going stale after joining/leaving a group.
    val addExpenseViewModel: AddExpenseViewModel = viewModel(factory = factory, key = vmKey)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Dashboard.route
    val currentTitleRes = when (currentRoute) {
        Screen.Recurring.route -> R.string.title_recurring
        Screen.Categories.route -> R.string.title_categories
        Screen.Settings.route -> R.string.title_settings
        else -> R.string.title_dashboard
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentTitleRes), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                Screen.bottomBarItems.forEach { screen ->
                    val label = stringResource(screen.labelRes)
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            // Categories and Recurring manage their own FAB for adding an item;
            // showing this one too would stack two FABs in the same corner.
            if (currentRoute == Screen.Dashboard.route) {
                FloatingActionButton(
                    onClick = { showAddExpense = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_expense))
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
