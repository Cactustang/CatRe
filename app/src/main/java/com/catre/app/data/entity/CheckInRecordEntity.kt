package com.catre.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "check_in_records",
    indices = [
        Index(value = ["catId", "behaviorTypeId", "checkedAt"])
    ]
)
data class CheckInRecordEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val behaviorTypeId: String,
    val checkedAt: Instant,
    val note: String?,
    val value: Double?,
    val valueUnit: String?,
    val imageUri: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
