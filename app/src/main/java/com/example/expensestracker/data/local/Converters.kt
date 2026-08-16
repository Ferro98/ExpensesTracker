package com.example.expensestracker.data.local

import androidx.room.TypeConverter
import com.example.expensestracker.data.local.entity.RecurrenceFrequency
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromFrequencyName(name: String?): RecurrenceFrequency? = name?.let { RecurrenceFrequency.valueOf(it) }

    @TypeConverter
    fun toFrequencyName(frequency: RecurrenceFrequency?): String? = frequency?.name
}
