package com.josiel.organizeprocesso.data.local

import androidx.room.TypeConverter
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.TipoAnexo
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromStatusGeral(value: StatusGeralProcesso?): String? = value?.name

    @TypeConverter
    fun toStatusGeral(value: String?): StatusGeralProcesso? = value?.let(StatusGeralProcesso::valueOf)

    @TypeConverter
    fun fromTipoAnexo(value: TipoAnexo?): String? = value?.name

    @TypeConverter
    fun toTipoAnexo(value: String?): TipoAnexo? = value?.let(TipoAnexo::valueOf)
}
