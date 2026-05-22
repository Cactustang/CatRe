package com.catre.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.catre.app.data.entity.BehaviorTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorTypeDao {
    @Query("SELECT * FROM behavior_types ORDER BY createdAt ASC")
    fun observeBehaviorTypes(): Flow<List<BehaviorTypeEntity>>

    @Query("SELECT * FROM behavior_types WHERE catId = :catId ORDER BY createdAt ASC")
    fun observeBehaviorTypesForCat(catId: String): Flow<List<BehaviorTypeEntity>>

    @Query("SELECT * FROM behavior_types WHERE showOnHome = 1 ORDER BY createdAt ASC")
    fun observeHomeBehaviorTypes(): Flow<List<BehaviorTypeEntity>>

    @Query("SELECT * FROM behavior_types WHERE catId = :catId AND showOnHome = 1 ORDER BY createdAt ASC")
    fun observeHomeBehaviorTypesForCat(catId: String): Flow<List<BehaviorTypeEntity>>

    @Query("SELECT COUNT(*) FROM behavior_types")
    suspend fun countBehaviorTypes(): Int

    @Query("SELECT COUNT(*) FROM behavior_types WHERE catId = :catId")
    suspend fun countBehaviorTypesForCat(catId: String): Int

    @Query("SELECT COUNT(*) FROM behavior_types WHERE catId = :catId AND name = :name")
    suspend fun countBehaviorNamesForCat(catId: String, name: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(behaviorTypes: List<BehaviorTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(behaviorType: BehaviorTypeEntity)

    @Update
    suspend fun update(behaviorType: BehaviorTypeEntity)

    @Query("DELETE FROM behavior_types WHERE id = :behaviorTypeId")
    suspend fun deleteById(behaviorTypeId: String)
}
