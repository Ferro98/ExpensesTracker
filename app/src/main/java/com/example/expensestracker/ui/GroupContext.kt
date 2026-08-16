package com.example.expensestracker.ui

import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.GroupRepository
import com.example.expensestracker.data.repository.SettlementRepository

/** Present only when the current device belongs to a group; null everywhere in solo mode. */
data class GroupContext(
    val groupId: String,
    val groupRepository: GroupRepository,
    val expenseRepository: ExpenseRepository,
    val settlementRepository: SettlementRepository
)
