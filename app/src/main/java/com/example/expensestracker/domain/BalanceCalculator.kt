package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.Settlement

/** Positive [netAmount]: [owedByUid] owes [owedToUid]. Both uids null means settled up (within rounding). */
data class Balance(val netAmount: Double, val owedByUid: String?, val owedToUid: String?)

object BalanceCalculator {
    private const val EPSILON = 0.005

    fun compute(expenses: List<Expense>, settlements: List<Settlement>, memberUids: List<String>): Balance {
        if (memberUids.size < 2) return Balance(0.0, null, null)
        val sorted = memberUids.sorted()
        val m1 = sorted[0]
        val m2 = sorted[1]
        var net = 0.0 // amount m2 owes m1

        expenses.filter { it.isShared }.forEach { expense ->
            val nonPayerShare = expense.amountInBaseCurrency * (1 - expense.payerShare)
            when (expense.paidByUid) {
                m1 -> net += nonPayerShare
                m2 -> net -= nonPayerShare
            }
        }

        settlements.forEach { settlement ->
            when (settlement.fromUid) {
                m2 -> net -= settlement.amountInBaseCurrency
                m1 -> net += settlement.amountInBaseCurrency
            }
        }

        return when {
            net > EPSILON -> Balance(net, owedByUid = m2, owedToUid = m1)
            net < -EPSILON -> Balance(-net, owedByUid = m1, owedToUid = m2)
            else -> Balance(0.0, null, null)
        }
    }
}
