package com.catre.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "cats")
data class CatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUri: String?,
    val gender: String?,
    val birthday: LocalDate?,
    val breed: String?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
