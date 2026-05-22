package com.catre.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.catre.app.data.entity.CatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatDao {
    @Query("SELECT * FROM cats ORDER BY createdAt ASC")
    fun observeCats(): Flow<List<CatEntity>>

    @Query("SELECT * FROM cats ORDER BY createdAt ASC")
    suspend fun getCats(): List<CatEntity>

    @Query("SELECT * FROM cats ORDER BY createdAt ASC LIMIT 1")
    fun observeFirstCat(): Flow<CatEntity?>

    @Query("SELECT * FROM cats ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstCat(): CatEntity?

    @Query("SELECT * FROM cats WHERE id = :catId LIMIT 1")
    suspend fun getCatById(catId: String): CatEntity?

    @Query("SELECT COUNT(*) FROM cats")
    suspend fun countCats(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(cat: CatEntity)

    @Update
    suspend fun update(cat: CatEntity)

    @Query("DELETE FROM cats WHERE id = :catId")
    suspend fun deleteById(catId: String)
}
