package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import java.util.Locale

/**
 * Expenses as a spreadsheet-ready CSV. The field separator follows the decimal separator the way
 * Excel and Sheets expect it for the user's locale (a comma as decimal mark means ";" between
 * fields, otherwise ","), and amounts use that same decimal mark - an Italian spreadsheet reads
 * "12,50" as a number, but "12.50" as text or a date. A leading BOM makes Excel read it as UTF-8.
 */
object ExpenseCsv {
    private const val BOM = "﻿"

    /**
     * @param header the column titles, in the order [build] writes the columns (11 of them).
     * @param typePersonal / [typeShared] the labels for the "type" column.
     * @param meLabel what to write in "paid by" when [myUid] paid.
     */
    fun build(
        expenses: List<Expense>,
        myUid: String,
        partnerName: String,
        decimalSeparator: Char,
        header: List<String>,
        typePersonal: String,
        typeShared: String,
        meLabel: String
    ): String {
        val separator = if (decimalSeparator == ',') ';' else ','

        fun number(value: Double) = String.format(Locale.ROOT, "%.2f", value).replace('.', decimalSeparator)

        fun field(text: String): String =
            if (text.any { it == separator || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

        val rows = expenses
            .sortedWith(compareBy<Expense> { it.date }.thenBy { it.createdAt?.seconds ?: 0 })
            .map { e ->
                listOf(
                    e.date,
                    e.categoryName,
                    e.note.orEmpty(),
                    number(e.amount),
                    e.currencyCode,
                    number(e.amountInBaseCurrency),
                    if (e.isShared) typeShared else typePersonal,
                    if (e.paidByUid == myUid) meLabel else partnerName,
                    number(e.shareFor(myUid)),
                    e.periodStart.orEmpty(),
                    e.periodEnd.orEmpty()
                )
            }

        return BOM + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(separator.toString()) { field(it) } } + "\r\n"
    }
}
