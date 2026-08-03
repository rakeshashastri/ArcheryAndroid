package com.archery.tracker.data.local

import androidx.room.TypeConverter
import com.archery.tracker.core.Arrow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArrowListConverter {
    @TypeConverter
    fun fromArrowList(arrows: List<Arrow>): String = Json.encodeToString(arrows)

    @TypeConverter
    fun toArrowList(json: String): List<Arrow> =
        if (json.isBlank()) emptyList() else Json.decodeFromString(json)
}
