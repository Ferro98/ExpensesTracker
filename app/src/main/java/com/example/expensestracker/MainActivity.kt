package com.example.expensestracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.expensestracker.data.WriteOutcome
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.settings.ThemeMode
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.ui.addexpense.AddExpenseSheet
import com.example.expensestracker.ui.addexpense.AddExpenseViewModel
import com.example.expensestracker.ui.categories.CategoriesScreen
import com.example.expensestracker.ui.components.showUndoSnackbar
import com.example.expensestracker.ui.group.GroupScreen
import com.example.expensestracker.ui.history.HistoryScreen
import com.example.expensestracker.ui.home.HomeScreen
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.more.MoreScreen
import com.example.expensestracker.ui.navigation.BottomTab
import com.example.expensestracker.ui.navigation.Screen
import com.example.expensestracker.ui.recurring.RecurringScreen
import com.example.expensestracker.ui.settings.SettingsScreen
import com.example.expensestracker.ui.stats.StatsScreen
import com.example.expensestracker.ui.theme.ExpensesTrackerTheme
import com.example.expensestracker.ui.theme.semanticColors
import kotlinx.coroutines.launch

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

            // Reactive (not a one-shot uid capture) so linking/restoring a Google account from
            // Settings - which can switch the signed-in Firebase user entirely, see
            // AuthRepository.linkWithGoogle's collision-fallback path - is picked up here and
            // rebuilds the whole tree against the (possibly different, possibly restored) uid.
            val authState by remember { app.authRepository.observeAuthState() }.collectAsState(initial = null)
            LaunchedEffect(Unit) { app.authRepository.ensureSignedIn() }
            LaunchedEffect(authState?.uid) {
                authState?.uid?.let { app.personalDataRepositoryFor(it).seedDefaultsIfNeeded() }
            }
            val myUid = authState?.uid

            // Needed on Android 13+ for the recurring-expense reminders (see ReminderWorker) to
            // actually show up; requested once up front rather than gating it behind the
            // reminder toggle so a reminder set today isn't silently dropped tomorrow.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val groupId by app.settingsRepository.groupId.collectAsState(initial = null)
            // Defaults to true (no banner) until the first callback fires, rather than flashing
            // "offline" for a frame on every cold start.
            val isOnline by remember { app.connectivityObserver.isOnline }.collectAsState(initial = true)

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
                        ExpensesTrackerRoot(factory, vmKey = "$groupId:$uid", isOnline = isOnline)
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
fun ExpensesTrackerRoot(factory: AppViewModelFactory, vmKey: String, isOnline: Boolean = true) {
    val navController = rememberNavController()
    var showAddExpense by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var showAddRecurring by remember { mutableStateOf(false) }
    // Unlike the per-screen ViewModels (each scoped to its own NavBackStackEntry, which gets
    // recreated whenever the NavHost itself is rebuilt below), these ViewModels are requested
    // directly here - outside any nav route - so they resolve to the Activity's own, long-lived
    // ViewModelStore. Without an explicit key tied to group identity, they would keep returning
    // the instance built with whatever groupContext was active the first time this screen ever
    // ran, silently going stale after joining/leaving a group. The keys must also differ *from
    // each other*, since an explicit key replaces the per-class default one.
    val addExpenseViewModel: AddExpenseViewModel = viewModel(factory = factory, key = "addExpense:$vmKey")
    // Home, History and Stats are three views over the same month data: one shared instance means
    // one set of Firestore listeners instead of three.
    val monthViewModel: MonthViewModel = viewModel(factory = factory, key = "month:$vmKey")

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = Screen.fromRoute(backStackEntry?.destination?.route)
    val onEditExpense: (Expense) -> Unit = { expense ->
        addExpenseViewModel.startEdit(expense)
        showAddExpense = true
    }
    val onDuplicateExpense: (Expense) -> Unit = { expense ->
        addExpenseViewModel.startDuplicate(expense)
        showAddExpense = true
    }

    // One shared snackbar surface (the root Scaffold's own slot below) so a snackbar triggered
    // from any screen is positioned correctly against the same bottom-bar/FAB insets, instead of
    // each screen needing its own nested Scaffold just to host one.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    val expenseSavedMessage = stringResource(R.string.expense_saved)
    val expenseUpdatedMessage = stringResource(R.string.expense_updated)
    val expenseDeletedMessage = stringResource(R.string.expense_deleted)
    val expenseDeleteFailedMessage = stringResource(R.string.expense_delete_failed)

    // Home/History/Stats share monthViewModel, so their "delete, then offer to undo" wiring is
    // built once here rather than repeated in each screen. Waits for the actual outcome instead
    // of assuming success: a timeout still gets the success message (the delete already applied
    // to Firestore's local cache and will sync once online - see MonthViewModel.deleteExpense),
    // but a genuine failure says so instead of quietly leaving the expense right where it was.
    val onDeleteExpenseWithUndo: (Expense) -> Unit = { expense ->
        snackbarScope.launch {
            when (monthViewModel.deleteExpense(expense.id)) {
                WriteOutcome.FAILED -> snackbarHostState.showSnackbar(expenseDeleteFailedMessage)
                WriteOutcome.SUCCESS, WriteOutcome.TIMED_OUT -> {
                    val result = snackbarHostState.showSnackbar(
                        message = expenseDeletedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) monthViewModel.restoreExpense(expense)
                }
            }
        }
    }
    // createdId is null when the create timed out waiting for Firestore's ack (still saved -
    // it's already in the local cache - just without an id to offer "Undo" for).
    val onExpenseSaved: (String?) -> Unit = { createdId ->
        if (createdId != null) {
            snackbarScope.showUndoSnackbar(snackbarHostState, expenseSavedMessage, undoLabel) {
                monthViewModel.deleteExpense(createdId)
            }
        } else {
            snackbarScope.launch { snackbarHostState.showSnackbar(expenseSavedMessage) }
        }
    }
    // No undo action here - reversing an edit would need the pre-edit values, which are already
    // gone by the time this fires. Just confirms the save went through.
    val onExpenseUpdated: () -> Unit = {
        snackbarScope.launch { snackbarHostState.showSnackbar(expenseUpdatedMessage) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(currentScreen.titleRes),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    // Screens under "More" are pushed on top of it rather than being tabs, so they
                    // need a way back that isn't the system gesture.
                    if (!currentScreen.isTab) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            if (currentScreen.isTab) {
                // The "+" is drawn as part of the bar itself (docked, overlapping its top edge)
                // rather than through Scaffold's own floatingActionButton slot: that slot places a
                // FabPosition.Center FAB a fixed 16dp *above* the bottom bar, which read as a
                // separate floating circle hovering over the bar instead of a raised button
                // belonging to it.
                BottomBar(
                    navController = navController,
                    currentScreen = currentScreen,
                    showAddFab = currentScreen != Screen.More,
                    onAddExpense = { showAddExpense = true }
                )
            }
        },
        // Hosted here (rather than each screen owning its own Scaffold+FAB) so every screen's
        // FAB is positioned against the same, single set of bottom-bar insets - a FAB inside a
        // Scaffold nested in this one double-counted insets and could float over list content
        // instead of clearing it. Only the management screens' labelled, corner FAB goes through
        // this slot now; the tab screens' "+" is docked into BottomBar above.
        floatingActionButton = {
            when (currentScreen) {
                Screen.Categories -> ExtendedFloatingActionButton(
                    onClick = { showAddCategory = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_category)) }
                )
                Screen.Recurring -> ExtendedFloatingActionButton(
                    onClick = { showAddRecurring = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_recurring_expense)) }
                )
                else -> Unit
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Firestore itself never surfaces "offline" - it just queues writes locally and stays
            // quiet - so this is the app's own signal, not something derived from a failed call.
            AnimatedVisibility(visible = !isOnline) {
                OfflineBanner()
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.weight(1f)
            ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = monthViewModel,
                    onEditExpense = onEditExpense,
                    onDuplicateExpense = onDuplicateExpense,
                    onAddExpense = { showAddExpense = true },
                    onSeeAllCategories = { navController.navigateToTab(Screen.Stats) },
                    onSeeAllExpenses = { navController.navigateToTab(Screen.History) },
                    onOpenGroup = { navController.navigate(Screen.Group.route) },
                    onDeleteExpense = onDeleteExpenseWithUndo
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = monthViewModel,
                    onEditExpense = onEditExpense,
                    onDuplicateExpense = onDuplicateExpense,
                    onDeleteExpense = onDeleteExpenseWithUndo,
                    onAddExpense = { showAddExpense = true }
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen(
                    viewModel = monthViewModel,
                    onEditExpense = onEditExpense,
                    onDuplicateExpense = onDuplicateExpense,
                    onDeleteExpense = onDeleteExpenseWithUndo,
                    onAddExpense = { showAddExpense = true }
                )
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onOpenRecurring = { navController.navigate(Screen.Recurring.route) },
                    onOpenCategories = { navController.navigate(Screen.Categories.route) },
                    onOpenGroup = { navController.navigate(Screen.Group.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Recurring.route) {
                RecurringScreen(
                    factory,
                    showAddDialog = showAddRecurring,
                    onShowAddDialogChange = { showAddRecurring = it }
                )
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(
                    factory,
                    showAddDialog = showAddCategory,
                    onDismissAddDialog = { showAddCategory = false },
                    onShowAddDialog = { showAddCategory = true }
                )
            }
            composable(Screen.Group.route) {
                GroupScreen(
                    factory,
                    onEditExpense = onEditExpense,
                    onDuplicateExpense = onDuplicateExpense,
                    onAddExpense = { showAddExpense = true },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(Screen.Settings.route) { SettingsScreen(factory) }
            }
        }
    }

    if (showAddExpense) {
        AddExpenseSheet(
            viewModel = addExpenseViewModel,
            onDismiss = { showAddExpense = false },
            onExpenseSaved = onExpenseSaved,
            onExpenseUpdated = onExpenseUpdated
        )
    }
}

/** Persistent, not a snackbar - being offline can last a while, and a snackbar would only auto-dismiss and be forgotten. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.semanticColors.warningContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.semanticColors.onWarningContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.semanticColors.onWarningContainer
        )
    }
}

// Half the standard 56dp FAB - offsetting by this much straddles it across the bar's top edge
// instead of leaving daylight between the two, so the button reads as part of the bar.
private val FabCradleOffset = 28.dp

@Composable
private fun BottomBar(
    navController: NavHostController,
    currentScreen: Screen,
    showAddFab: Boolean,
    onAddExpense: () -> Unit
) {
    val tabs = Screen.bottomTabs
    Box(modifier = Modifier.fillMaxWidth()) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = NavigationBarDefaults.Elevation
        ) {
            NavBarTab(navController, currentScreen, tabs[0])
            NavBarTab(navController, currentScreen, tabs[1])
            // Reserved for the docked FAB above - kept even on "More" (where showAddFab is false)
            // so all four items stay in the same place instead of re-spacing across tabs.
            Spacer(modifier = Modifier.weight(1f))
            NavBarTab(navController, currentScreen, tabs[2])
            NavBarTab(navController, currentScreen, tabs[3])
        }

        if (showAddFab) {
            FloatingActionButton(
                onClick = onAddExpense,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -FabCradleOffset),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_expense),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavBarTab(navController: NavHostController, currentScreen: Screen, tab: BottomTab) {
    val label = stringResource(tab.labelRes)
    NavigationBarItem(
        selected = currentScreen == tab.screen,
        onClick = { navController.navigateToTab(tab.screen) },
        icon = { Icon(tab.icon, contentDescription = label) },
        label = {
            Text(
                label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** Switching tabs never stacks: it returns to the graph's start and restores that tab's own state. */
private fun NavHostController.navigateToTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
