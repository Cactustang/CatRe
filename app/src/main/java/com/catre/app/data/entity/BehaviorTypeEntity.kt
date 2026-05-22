package com.catre.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "behavior_types",
    indices = [
        Index(value = ["catId", "name"])
    ]
)
data class BehaviorTypeEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val isBuiltin: Boolean,
    val showOnHome: Boolean,
    val frequencyType: FrequencyType,
    val frequencyValue: Int?,
    val weeklyTarget: Int?,
    val reminderEnabled: Boolean,
    val reminderTime: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
