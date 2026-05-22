package com.catre.app.core.database

import androidx.room.TypeConverter
import com.catre.app.data.entity.FrequencyType
import java.time.Instant
import java.time.LocalDate

class CatReTypeConverters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun frequencyTypeToString(value: FrequencyType?): String? = value?.name

    @TypeConverter
    fun stringToFrequencyType(value: String?): FrequencyType? = value?.let(FrequencyType::valueOf)
}
