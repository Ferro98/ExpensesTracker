package com.example.expensestracker.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.SavingsEntry
import com.example.expensestracker.data.model.SavingsGoal
import com.example.expensestracker.data.model.Trip
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.repository.SavingsRepository
import com.example.expensestracker.data.repository.TripRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.SavingsMath
import com.example.expensestracker.domain.TripCalculator
import com.example.expensestracker.domain.TripPhase
import com.example.expensestracker.domain.TripStats
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** How long to wait for Firestore's server ack - a timeout only means "still offline"; the write is already in the local cache. */
private const val WRITE_TIMEOUT_MS = 8000L

/** The savings pot paying for a trip, as the trip screen shows it. */
data class LinkedGoal(val id: String, val name: String, val icon: String, val balance: Double, val target: Double)

/** A pot a trip could be linked to (same scope only), for the picker in the trip dialog. */
data class GoalChoice(val id: String, val name: String, val icon: String, val isGroup: Boolean, val target: Double)

/** A trip with the expenses that belong to it, what they add up to, and the pot funding it if any. */
data class TripItem(
    val trip: Trip,
    val isGroup: Boolean,
    val expenses: List<Expense>,
    val stats: TripStats,
    val linkedGoal: LinkedGoal? = null
)

data class TripsUiState(
    val loaded: Boolean = false,
    val trips: List<TripItem> = emptyList(),
    val goalChoices: List<GoalChoice> = emptyList(),
    val myUid: String = "",
    val partnerName: String = "Partner",
    val inGroup: Boolean = false,
    val currencyRates: List<CurrencyRate> = emptyList(),
    val defaultCurrency: String = "EUR"
)

private data class TripScope(val trips: List<Trip>, val expenses: List<Expense>)

private fun scopeFlow(trips: TripRepository?, expenses: ExpenseRepository?): Flow<TripScope> =
    if (trips == null || expenses == null) flowOf(TripScope(emptyList(), emptyList()))
    else combine(trips.observeTrips(), expenses.observeAllExpenses()) { t, e -> TripScope(t, e) }

private data class SavingsScope(val goals: List<SavingsGoal>, val entries: List<SavingsEntry>)

private fun savingsFlow(repository: SavingsRepository?): Flow<SavingsScope> =
    if (repository == null) flowOf(SavingsScope(emptyList(), emptyList()))
    else combine(repository.observeGoals(), repository.observeEntries()) { g, e -> SavingsScope(g, e) }

/**
 * Personal and (in a group) shared trips with what's been spent on each. A trip's spending is
 * whichever expenses of its own scope carry its id, so a personal trip never sees the partner's
 * private expenses and a shared one never mixes in anyone's personal ones.
 */
class TripsViewModel(
    private val personalTripRepository: TripRepository,
    personalExpenseRepository: ExpenseRepository,
    private val groupContext: GroupContext?,
    private val personalDataRepository: PersonalDataRepository,
    settingsRepository: SettingsRepository,
    private val myUid: String,
    personalSavingsRepository: SavingsRepository
) : ViewModel() {
    val uiState: StateFlow<TripsUiState> = combine(
        scopeFlow(personalTripRepository, personalExpenseRepository),
        scopeFlow(groupContext?.tripRepository, groupContext?.expenseRepository),
        combine(personalDataRepository.observeCategories(), settingsRepository.categoryAliases) { c, a -> c to a },
        combine(
            groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null),
            personalDataRepository.observeCurrencyRates(),
            settingsRepository.defaultCurrency
        ) { group, rates, currency -> Triple(group, rates, currency) },
        combine(savingsFlow(personalSavingsRepository), savingsFlow(groupContext?.savingsRepository)) { p, g -> p to g }
    ) { personal, shared, catsAndAliases, groupRatesCurrency, savings ->
        val (categories, aliases) = catsAndAliases
        val (group, rates, defaultCurrency) = groupRatesCurrency
        val (personalSavings, sharedSavings) = savings
        val today = LocalDate.now()
        val partnerUid = group?.otherMemberUid(myUid)

        fun items(scope: TripScope, isGroup: Boolean, savings: SavingsScope, categories: List<Category>) = scope.trips.map { trip ->
            val tripExpenses = scope.expenses.filter { it.tripId == trip.id }
            val linked = trip.goalId?.let { id -> savings.goals.firstOrNull { it.id == id } }?.let { goal ->
                LinkedGoal(goal.id, goal.name, goal.icon, SavingsMath.balance(savings.entries.filter { it.goalId == goal.id }), goal.targetAmount)
            }
            TripItem(trip, isGroup, tripExpenses, TripCalculator.compute(trip, tripExpenses, isGroup, myUid, today, categories, aliases), linked)
        }

        fun choices(scope: SavingsScope, isGroup: Boolean) =
            scope.goals.filter { !it.archived }.map { GoalChoice(it.id, it.name, it.icon, isGroup, it.targetAmount) }

        val all = items(personal, false, personalSavings, categories) + items(shared, true, sharedSavings, categories)
        // What's happening now first, then what's coming (soonest first), then what's over (latest first).
        val ordered = all.sortedWith(
            compareBy<TripItem> {
                when (it.stats.phase) {
                    TripPhase.ONGOING -> 0
                    TripPhase.UPCOMING -> 1
                    TripPhase.ENDED -> 2
                }
            }.thenBy { if (it.stats.phase == TripPhase.ENDED) -it.trip.localEnd.toEpochDay() else it.trip.localStart.toEpochDay() }
        )
        TripsUiState(
            loaded = true,
            trips = ordered,
            goalChoices = choices(personalSavings, false) + choices(sharedSavings, true),
            myUid = myUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner",
            inGroup = groupContext != null,
            currencyRates = rates,
            defaultCurrency = defaultCurrency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsUiState(inGroup = groupContext != null, myUid = myUid))

    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed

    fun clearWriteFailed() {
        _writeFailed.value = false
    }

    private fun repositoryFor(isGroup: Boolean): TripRepository =
        if (isGroup && groupContext != null) groupContext.tripRepository else personalTripRepository

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withTimeoutOrNull(WRITE_TIMEOUT_MS) { block() }
            } catch (e: Exception) {
                _writeFailed.value = true
            }
        }
    }

    fun addTrip(isGroup: Boolean, name: String, icon: String, start: LocalDate, end: LocalDate, budget: Double?, currencyCode: String, goalId: String?) {
        launchWrite {
            val budgetBase = budget?.let { personalDataRepository.convertToBase(it, currencyCode) } ?: 0.0
            repositoryFor(isGroup).addTrip(name, icon, start, end, budgetBase, goalId)
        }
    }

    fun updateTrip(item: TripItem, name: String, icon: String, start: LocalDate, end: LocalDate, budget: Double?, currencyCode: String, goalId: String?) {
        launchWrite {
            val budgetBase = budget?.let { personalDataRepository.convertToBase(it, currencyCode) } ?: 0.0
            repositoryFor(item.isGroup).updateTrip(item.trip.id, name, icon, start, end, budgetBase, goalId)
        }
    }

    fun deleteTrip(item: TripItem) {
        launchWrite { repositoryFor(item.isGroup).deleteTrip(item.trip.id) }
    }
}
