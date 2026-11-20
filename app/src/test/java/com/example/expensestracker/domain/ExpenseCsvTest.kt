package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCsvTest {
    private val header = listOf("date", "category", "note", "amount", "currency", "amount_base", "type", "paid_by", "my_share", "period_start", "period_end")

    private fun csv(vararg expenses: Expense, decimal: Char = '.') = ExpenseCsv.build(
        expenses.toList(), "me", "Pandino", decimal, header, "Personal", "Shared", "Me"
    )

    private fun lines(text: String) = text.removePrefix("﻿").trimEnd().split("\r\n")

    private val groceries = Expense(
        id = "1", categoryName = "Spesa", note = "Esselunga", amount = 12.5, currencyCode = "EUR",
        amountInBaseCurrency = 12.5, date = "2026-11-02", paidByUid = "me"
    )

    @Test
    fun writesAHeaderAndOneRowPerExpense() {
        val out = lines(csv(groceries))
        assertEquals(header.joinToString(","), out[0])
        assertEquals("2026-11-02,Spesa,Esselunga,12.50,EUR,12.50,Personal,Me,12.50,,", out[1])
    }

    @Test
    fun startsWithABomSoExcelReadsUtf8() {
        assertTrue(csv(groceries).startsWith("﻿"))
    }

    @Test
    fun aCommaDecimalSwitchesTheFieldSeparatorToASemicolon() {
        val out = lines(csv(groceries, decimal = ','))
        assertEquals("2026-11-02;Spesa;Esselunga;12,50;EUR;12,50;Personal;Me;12,50;;", out[1])
    }

    @Test
    fun quotesFieldsThatContainTheSeparatorQuotesOrNewlines() {
        val tricky = groceries.copy(note = "pane, latte \"bio\"\ne uova")
        val out = csv(tricky).removePrefix("﻿")
        assertTrue(out.contains("\"pane, latte \"\"bio\"\"\ne uova\""))
    }

    @Test
    fun aSharedExpenseShowsWhoPaidAndOnlyMyShare() {
        val shared = groceries.copy(isShared = true, paidByUid = "her", amountInBaseCurrency = 100.0, amount = 100.0, payerShare = 0.7)
        val row = lines(csv(shared))[1].split(",")
        assertEquals("Shared", row[6])
        assertEquals("Pandino", row[7])
        // She paid, took 70%: my part is the other 30%.
        assertEquals("30.00", row[8])
    }

    @Test
    fun rowsComeOutOldestFirstAndKeepTheSubscriptionPeriod() {
        val later = groceries.copy(id = "2", date = "2026-11-20", periodStart = "2026-11-20", periodEnd = "2026-12-19")
        val out = lines(csv(later, groceries))
        assertTrue(out[1].startsWith("2026-11-02"))
        assertTrue(out[2].endsWith("2026-11-20,2026-12-19"))
    }
}
