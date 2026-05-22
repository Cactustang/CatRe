package com.catre.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.catre.app.data.entity.CheckInRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInRecordDao {
    @Query("SELECT * FROM check_in_records ORDER BY checkedAt DESC")
    fun observeAllRecords(): Flow<List<CheckInRecordEntity>>

    @Query("SELECT * FROM check_in_records WHERE catId = :catId ORDER BY checkedAt DESC")
    fun observeRecordsForCat(catId: String): Flow<List<CheckInRecordEntity>>

    @Query("SELECT COUNT(*) FROM check_in_records WHERE catId = :catId")
    fun observeRecordCountForCat(catId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CheckInRecordEntity)

    @Update
    suspend fun update(record: CheckInRecordEntity)

    @Delete
    suspend fun delete(record: CheckInRecordEntity)

    @Query("DELETE FROM check_in_records WHERE catId = :catId")
    suspend fun deleteRecordsForCat(catId: String)

    @Query("DELETE FROM check_in_records WHERE behaviorTypeId = :behaviorTypeId")
    suspend fun deleteRecordsForBehavior(behaviorTypeId: String)
}
