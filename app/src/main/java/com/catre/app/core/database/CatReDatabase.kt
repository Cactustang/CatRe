package com.catre.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.catre.app.data.dao.BehaviorTypeDao
import com.catre.app.data.dao.CatDao
import com.catre.app.data.dao.CheckInRecordDao
import com.catre.app.data.entity.BehaviorTypeEntity
import com.catre.app.data.entity.CatEntity
import com.catre.app.data.entity.CheckInRecordEntity

@Database(
    entities = [
        CatEntity::class,
        BehaviorTypeEntity::class,
        CheckInRecordEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(CatReTypeConverters::class)
abstract class CatReDatabase : RoomDatabase() {
    abstract fun catDao(): CatDao
    abstract fun behaviorTypeDao(): BehaviorTypeDao
    abstract fun checkInRecordDao(): CheckInRecordDao

    companion object {
        @Volatile
        private var instance: CatReDatabase? = null

        fun getInstance(context: Context): CatReDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatReDatabase::class.java,
                    "catre.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN catId TEXT NOT NULL DEFAULT ''")

                val cats = mutableListOf<String>()
                db.query("SELECT id FROM cats ORDER BY createdAt ASC").use { cursor ->
                    while (cursor.moveToNext()) {
                        cats.add(cursor.getString(0))
                    }
                }
                if (cats.isEmpty()) return

                val firstCatId = cats.first()
                db.execSQL("UPDATE behavior_types SET catId = '$firstCatId' WHERE catId = ''")

                val behaviors = mutableListOf<BehaviorMigrationRow>()
                db.query(
                    "SELECT id, name, iconKey, colorHex, isBuiltin, showOnHome, frequencyType, frequencyValue, weeklyTarget, reminderEnabled, reminderTime, createdAt, updatedAt FROM behavior_types WHERE catId = '$firstCatId'"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        behaviors.add(
                            BehaviorMigrationRow(
                                id = cursor.getString(0),
                                name = cursor.getString(1),
                                iconKey = cursor.getString(2),
                                colorHex = cursor.getString(3),
                                isBuiltin = cursor.getInt(4),
                                showOnHome = cursor.getInt(5),
                                frequencyType = cursor.getString(6),
                                frequencyValue = if (cursor.isNull(7)) null else cursor.getInt(7),
                                weeklyTarget = if (cursor.isNull(8)) null else cursor.getInt(8),
                                reminderEnabled = cursor.getInt(9),
                                reminderTime = if (cursor.isNull(10)) null else cursor.getString(10),
                                createdAt = cursor.getLong(11),
                                updatedAt = cursor.getLong(12)
                            )
                        )
                    }
                }

                cats.drop(1).forEach { catId ->
                    behaviors.forEach { behavior ->
                        val newBehaviorId = "${catId}_${behavior.id}"
                        db.execSQL(
                            "INSERT OR IGNORE INTO behavior_types (id, catId, name, iconKey, colorHex, isBuiltin, showOnHome, frequencyType, frequencyValue, weeklyTarget, reminderEnabled, reminderTime, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                newBehaviorId,
                                catId,
                                behavior.name,
                                behavior.iconKey,
                                behavior.colorHex,
                                behavior.isBuiltin,
                                behavior.showOnHome,
                                behavior.frequencyType,
                                behavior.frequencyValue,
                                behavior.weeklyTarget,
                                behavior.reminderEnabled,
                                behavior.reminderTime,
                                behavior.createdAt,
                                behavior.updatedAt
                            )
                        )
                        db.execSQL(
                            "UPDATE check_in_records SET behaviorTypeId = ? WHERE catId = ? AND behaviorTypeId = ?",
                            arrayOf(newBehaviorId, catId, behavior.id)
                        )
                    }
                }

                db.execSQL("CREATE INDEX IF NOT EXISTS index_behavior_types_catId_name ON behavior_types(catId, name)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")

                val behaviorIdsByCat = linkedMapOf<String, MutableList<String>>()
                db.query("SELECT id, catId FROM behavior_types ORDER BY catId ASC, createdAt ASC, id ASC").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val catId = cursor.getString(1)
                        behaviorIdsByCat.getOrPut(catId) { mutableListOf() }.add(id)
                    }
                }
                behaviorIdsByCat.values.forEach { ids ->
                    ids.forEachIndexed { index, id ->
                        db.execSQL("UPDATE behavior_types SET sortOrder = ? WHERE id = ?", arrayOf(index, id))
                    }
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN valueEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN valueLabel TEXT")
                db.execSQL("ALTER TABLE behavior_types ADD COLUMN valueUnit TEXT")
                db.execSQL(
                    "UPDATE behavior_types SET valueEnabled = 1, valueLabel = '数值', valueUnit = 'kg' WHERE name LIKE '%称重%' OR id LIKE '%weight%' OR iconKey IN ('weight', 'scale')"
                )
            }
        }

        private data class BehaviorMigrationRow(
            val id: String,
            val name: String,
            val iconKey: String,
            val colorHex: String,
            val isBuiltin: Int,
            val showOnHome: Int,
            val frequencyType: String,
            val frequencyValue: Int?,
            val weeklyTarget: Int?,
            val reminderEnabled: Int,
            val reminderTime: String?,
            val createdAt: Long,
            val updatedAt: Long
        )
    }
}
