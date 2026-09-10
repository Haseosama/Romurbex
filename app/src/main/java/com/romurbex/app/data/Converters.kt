package com.romurbex.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCategory(value: LocationCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): LocationCategory =
        runCatching { LocationCategory.valueOf(value) }.getOrDefault(LocationCategory.AUTRE)

    @TypeConverter
    fun fromRisk(value: RiskLevel): String = value.name

    @TypeConverter
    fun toRisk(value: String): RiskLevel =
        runCatching { RiskLevel.valueOf(value) }.getOrDefault(RiskLevel.INCONNU)
}
