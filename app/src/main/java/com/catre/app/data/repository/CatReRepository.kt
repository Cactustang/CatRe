package com.catre.app.data.repository

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.catre.app.core.database.CatReDatabase
import com.catre.app.data.dao.BehaviorTypeDao
import com.catre.app.data.dao.CatDao
import com.catre.app.data.dao.CheckInRecordDao
import com.catre.app.data.entity.BehaviorTypeEntity
import com.catre.app.data.entity.CatEntity
import com.catre.app.data.entity.CheckInRecordEntity
import com.catre.app.data.entity.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class HomeSnapshot(
    val cat: CatEntity?,
    val behaviorSummaries: List<HomeBehaviorSummary>,
    val recordCount: Int
)

data class HomeBehaviorSummary(
    val behaviorType: BehaviorTypeEntity,
    val lastCheckedAt: Instant?,
    val daysSinceLast: Long?,
    val count30Days: Int,
    val count90Days: Int,
    val count180Days: Int,
    val checkedToday: Boolean,
    val statusText: String
)

class CatReRepository(
    private val database: CatReDatabase,
    private val catDao: CatDao,
    private val behaviorTypeDao: BehaviorTypeDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val preferences: SharedPreferences
) {
    private val selectedCatId = MutableStateFlow(preferences.getString(KEY_CURRENT_CAT_ID, null))
    val cats: Flow<List<CatEntity>> = catDao.observeCats()
    val currentCat: Flow<CatEntity?> = combine(cats, selectedCatId) { cats, selectedId ->
        cats.firstOrNull { it.id == selectedId } ?: cats.firstOrNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val catAndBehaviors: Flow<Pair<CatEntity?, List<BehaviorTypeEntity>>> = currentCat.flatMapLatest { cat ->
        if (cat == null) {
            flowOf(null to emptyList())
        } else {
            behaviorTypeDao.observeHomeBehaviorTypesForCat(cat.id).combine(flowOf(cat)) { behaviorTypes, current ->
                current to behaviorTypes
            }
        }
    }

    val homeSnapshot: Flow<HomeSnapshot> = catAndBehaviors.combineCurrentCatRecords()
    @OptIn(ExperimentalCoroutinesApi::class)
    val behaviorTypes: Flow<List<BehaviorTypeEntity>> = currentCat.flatMapLatest { cat ->
        if (cat == null) flowOf(emptyList()) else behaviorTypeDao.observeBehaviorTypesForCat(cat.id)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val archivedBehaviorTypes: Flow<List<BehaviorTypeEntity>> = currentCat.flatMapLatest { cat ->
        if (cat == null) flowOf(emptyList()) else behaviorTypeDao.observeArchivedBehaviorTypesForCat(cat.id)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentCatRecords: Flow<List<CheckInRecordEntity>> = currentCat.flatMapLatest { cat ->
        if (cat == null) flowOf(emptyList()) else checkInRecordDao.observeRecordsForCat(cat.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<Pair<CatEntity?, List<BehaviorTypeEntity>>>.combineCurrentCatRecords(): Flow<HomeSnapshot> {
        return flatMapLatest { (cat, behaviorTypes) ->
            if (cat == null) {
                flowOf(HomeSnapshot(cat = null, behaviorSummaries = emptyList(), recordCount = 0))
            } else {
                checkInRecordDao.observeRecordsForCat(cat.id).map { records ->
                    HomeSnapshot(
                        cat = cat,
                        behaviorSummaries = buildHomeSummaries(behaviorTypes, records),
                        recordCount = records.size
                    )
                }
            }
        }
    }

    suspend fun ensureDefaultsForExistingCat() {
        val cats = catDao.getCats()
        cats.forEach { cat ->
            seedDefaultBehaviorTypesForCatIfNeeded(cat.id)
        }
        repairCurrentCatSelection()
    }

    suspend fun createFirstCat(name: String) {
        createCat(name = name, gender = null, avatarUri = null, birthday = null, breed = null, note = null)
    }

    suspend fun createCat(
        name: String,
        gender: String?,
        avatarUri: String?,
        birthday: LocalDate?,
        breed: String?,
        note: String?
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "请输入猫咪名字" }

        val now = Instant.now()
        val catId = UUID.randomUUID().toString()
        val wasEmpty = catDao.countCats() == 0
        catDao.insert(
            CatEntity(
                id = catId,
                name = cleanName,
                avatarUri = avatarUri?.trim()?.ifEmpty { null },
                gender = gender?.trim()?.ifEmpty { null },
                birthday = birthday,
                breed = breed?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now
            )
        )
        seedDefaultBehaviorTypesForCat(catId, now)
        if (wasEmpty) {
            setCurrentCat(catId)
        }
    }

    suspend fun updateCat(
        cat: CatEntity,
        name: String,
        gender: String?,
        avatarUri: String?,
        birthday: LocalDate?,
        breed: String?,
        note: String?
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "请输入猫咪名字" }
        catDao.update(
            cat.copy(
                name = cleanName,
                avatarUri = avatarUri?.trim()?.ifEmpty { null },
                gender = gender?.trim()?.ifEmpty { null },
                birthday = birthday,
                breed = breed?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun selectCat(catId: String) {
        require(catDao.getCatById(catId) != null) { "猫咪不存在" }
        setCurrentCat(catId)
    }

    suspend fun deleteCat(cat: CatEntity) {
        val selectedBeforeDelete = selectedCatId.value
        database.withTransaction {
            checkInRecordDao.deleteRecordsForCat(cat.id)
            catDao.deleteById(cat.id)
        }
        if (selectedBeforeDelete == cat.id || selectedBeforeDelete == null) {
            setCurrentCat(catDao.getFirstCat()?.id)
        }
    }

    suspend fun repairCurrentCatSelection() {
        val selectedId = selectedCatId.value
        if (selectedId == null || catDao.getCatById(selectedId) == null) {
            setCurrentCat(catDao.getFirstCat()?.id)
        }
    }

    suspend fun checkIn(behaviorTypeId: String, value: Double? = null, valueUnit: String? = null) {
        val cat = currentSelectedCatOrFallback() ?: error("请先创建猫咪")
        val now = Instant.now()
        checkInRecordDao.insert(
            CheckInRecordEntity(
                id = UUID.randomUUID().toString(),
                catId = cat.id,
                behaviorTypeId = behaviorTypeId,
                checkedAt = now,
                note = null,
                value = value,
                valueUnit = valueUnit,
                imageUri = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateRecord(
        record: CheckInRecordEntity,
        checkedAt: Instant,
        note: String,
        value: Double?,
        valueUnit: String?,
        imageUri: String?
    ) {
        checkInRecordDao.update(
            record.copy(
                checkedAt = checkedAt,
                note = note.trim().ifEmpty { null },
                value = value,
                valueUnit = valueUnit,
                imageUri = imageUri?.trim()?.ifEmpty { null },
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun deleteRecord(record: CheckInRecordEntity) {
        checkInRecordDao.delete(record)
    }

    suspend fun addRecordOnDate(
        behaviorTypeId: String,
        date: LocalDate,
        value: Double? = null,
        valueUnit: String? = null,
        note: String? = null,
        imageUri: String? = null
    ) {
        val cat = currentSelectedCatOrFallback() ?: error("请先创建猫咪")
        val localTime = LocalTime.now().withNano(0)
        val checkedAt = ZonedDateTime.of(date, localTime, ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        checkInRecordDao.insert(
            CheckInRecordEntity(
                id = UUID.randomUUID().toString(),
                catId = cat.id,
                behaviorTypeId = behaviorTypeId,
                checkedAt = checkedAt,
                note = note?.trim()?.ifEmpty { null } ?: "补录",
                value = value,
                valueUnit = valueUnit,
                imageUri = imageUri?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun createBehavior(
        name: String,
        iconKey: String,
        colorHex: String,
        showOnHome: Boolean,
        frequencyType: FrequencyType,
        frequencyValue: Int?,
        weeklyTarget: Int?,
        valueEnabled: Boolean,
        valueLabel: String?,
        valueUnit: String?
    ) {
        val cat = currentSelectedCatOrFallback() ?: error("请先新增猫咪")
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "请输入行为名称" }
        require(behaviorTypeDao.countBehaviorNamesForCat(cat.id, cleanName) == 0) { "当前猫咪已存在同名行为" }
        val now = Instant.now()
        val sortOrder = behaviorTypeDao.getBehaviorTypesForCat(cat.id).maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        behaviorTypeDao.insert(
            BehaviorTypeEntity(
                id = UUID.randomUUID().toString(),
                catId = cat.id,
                name = cleanName,
                iconKey = iconKey,
                colorHex = colorHex,
                isBuiltin = false,
                showOnHome = showOnHome,
                frequencyType = frequencyType,
                frequencyValue = frequencyValue,
                weeklyTarget = weeklyTarget,
                reminderEnabled = false,
                reminderTime = null,
                isArchived = false,
                archivedAt = null,
                sortOrder = sortOrder,
                valueEnabled = valueEnabled,
                valueLabel = valueLabel?.trim()?.ifEmpty { null },
                valueUnit = valueUnit?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateBehavior(
        behaviorType: BehaviorTypeEntity,
        name: String,
        iconKey: String,
        colorHex: String,
        showOnHome: Boolean,
        frequencyType: FrequencyType,
        frequencyValue: Int?,
        weeklyTarget: Int?,
        valueEnabled: Boolean,
        valueLabel: String?,
        valueUnit: String?
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "请输入行为名称" }
        val sameNameCount = behaviorTypeDao.countBehaviorNamesForCat(behaviorType.catId, cleanName)
        require(cleanName == behaviorType.name || sameNameCount == 0) { "当前猫咪已存在同名行为" }
        behaviorTypeDao.update(
            behaviorType.copy(
                name = cleanName,
                iconKey = iconKey,
                colorHex = colorHex,
                showOnHome = showOnHome,
                frequencyType = frequencyType,
                frequencyValue = frequencyValue,
                weeklyTarget = weeklyTarget,
                isArchived = behaviorType.isArchived,
                archivedAt = behaviorType.archivedAt,
                sortOrder = behaviorType.sortOrder,
                valueEnabled = valueEnabled,
                valueLabel = valueLabel?.trim()?.ifEmpty { null },
                valueUnit = valueUnit?.trim()?.ifEmpty { null },
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun deleteBehavior(behaviorType: BehaviorTypeEntity) {
        database.withTransaction {
            checkInRecordDao.deleteRecordsForBehavior(behaviorType.id)
            behaviorTypeDao.deleteById(behaviorType.id)
        }
    }

    suspend fun archiveBehavior(behaviorType: BehaviorTypeEntity) {
        val now = Instant.now()
        behaviorTypeDao.update(
            behaviorType.copy(
                isArchived = true,
                archivedAt = now,
                showOnHome = false,
                updatedAt = now
            )
        )
    }

    suspend fun restoreBehavior(behaviorType: BehaviorTypeEntity) {
        val now = Instant.now()
        behaviorTypeDao.update(
            behaviorType.copy(
                isArchived = false,
                archivedAt = null,
                updatedAt = now
            )
        )
    }

    suspend fun moveBehavior(behaviorType: BehaviorTypeEntity, targetIndex: Int) {
        val peers = behaviorTypeDao.getBehaviorTypesForCatByArchive(behaviorType.catId, behaviorType.isArchived)
        val index = peers.indexOfFirst { it.id == behaviorType.id }
        if (index < 0) return
        val boundedTargetIndex = targetIndex.coerceIn(0, peers.lastIndex)
        if (index == boundedTargetIndex) return
        val mutable = peers.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(boundedTargetIndex, item)
        database.withTransaction {
            val now = Instant.now()
            mutable.forEachIndexed { order, behavior ->
                behaviorTypeDao.update(behavior.copy(sortOrder = order, updatedAt = now))
            }
        }
    }

    private fun buildHomeSummaries(
        behaviorTypes: List<BehaviorTypeEntity>,
        records: List<CheckInRecordEntity>
    ): List<HomeBehaviorSummary> {
        val now = Instant.now()
        val today = LocalDate.now()
        val recordsByBehavior = records.groupBy { it.behaviorTypeId }
        return behaviorTypes.map { behaviorType ->
            val behaviorRecords = recordsByBehavior[behaviorType.id].orEmpty()
            val lastCheckedAt = behaviorRecords.maxByOrNull { it.checkedAt }?.checkedAt
            HomeBehaviorSummary(
                behaviorType = behaviorType,
                lastCheckedAt = lastCheckedAt,
                daysSinceLast = lastCheckedAt?.let {
                    ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), today)
                },
                count30Days = behaviorRecords.countSince(now.minus(30, ChronoUnit.DAYS)),
                count90Days = behaviorRecords.countSince(now.minus(90, ChronoUnit.DAYS)),
                count180Days = behaviorRecords.countSince(now.minus(180, ChronoUnit.DAYS)),
                checkedToday = behaviorRecords.any {
                    it.checkedAt.atZone(ZoneId.systemDefault()).toLocalDate() == today
                },
                statusText = behaviorStatusText(behaviorType, behaviorRecords, today, lastCheckedAt)
            )
        }
    }

    private fun behaviorStatusText(
        behaviorType: BehaviorTypeEntity,
        records: List<CheckInRecordEntity>,
        today: LocalDate,
        lastCheckedAt: Instant?
    ): String {
        val checkedToday = records.any {
            it.checkedAt.atZone(ZoneId.systemDefault()).toLocalDate() == today
        }
        if (checkedToday) return "今天已完成"

        return when (behaviorType.frequencyType) {
            FrequencyType.NONE -> "按需记录"
            FrequencyType.EVERY_N_DAYS -> {
                val targetDays = behaviorType.frequencyValue ?: return "未设置频率"
                val days = lastCheckedAt?.let {
                    ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), today)
                } ?: return "暂无记录"
                when {
                    days > targetDays -> "已逾期"
                    days == targetDays.toLong() -> "已到期"
                    days >= (targetDays - 3).coerceAtLeast(0) -> "即将到期"
                    else -> "正常"
                }
            }
            FrequencyType.TIMES_PER_WEEK -> {
                val target = behaviorType.weeklyTarget ?: return "未设置频率"
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val count = records.count {
                    !it.checkedAt.atZone(ZoneId.systemDefault()).toLocalDate().isBefore(weekStart)
                }
                "本周 $count/$target"
            }
            FrequencyType.TIMES_PER_MONTH -> {
                val target = behaviorType.frequencyValue ?: return "未设置频率"
                val monthStart = today.withDayOfMonth(1)
                val count = records.count {
                    !it.checkedAt.atZone(ZoneId.systemDefault()).toLocalDate().isBefore(monthStart)
                }
                "本月 $count/$target"
            }
        }
    }

    private fun List<CheckInRecordEntity>.countSince(start: Instant): Int {
        return count { !it.checkedAt.isBefore(start) }
    }

    private suspend fun seedDefaultBehaviorTypes(now: Instant = Instant.now()) {
        currentSelectedCatOrFallback()?.let { seedDefaultBehaviorTypesForCat(it.id, now) }
    }

    private suspend fun seedDefaultBehaviorTypesForCatIfNeeded(catId: String, now: Instant = Instant.now()) {
        if (behaviorTypeDao.countBehaviorTypesForCat(catId) == 0) {
            seedDefaultBehaviorTypesForCat(catId, now)
        }
    }

    private suspend fun seedDefaultBehaviorTypesForCat(catId: String, now: Instant = Instant.now()) {
        behaviorTypeDao.insertAll(defaultBehaviorTypes(catId, now))
    }

    private suspend fun currentSelectedCatOrFallback(): CatEntity? {
        val selectedId = selectedCatId.value
        return selectedId?.let { catDao.getCatById(it) } ?: catDao.getFirstCat()?.also { setCurrentCat(it.id) }
    }

    private fun setCurrentCat(catId: String?) {
        selectedCatId.value = catId
        preferences.edit().apply {
            if (catId == null) remove(KEY_CURRENT_CAT_ID) else putString(KEY_CURRENT_CAT_ID, catId)
        }.apply()
    }

    private fun defaultBehaviorTypes(catId: String, now: Instant): List<BehaviorTypeEntity> {
        return listOf(
            DefaultBehavior("bath", "洗澡", "bath", "#F47C6B", FrequencyType.EVERY_N_DAYS, 30),
            DefaultBehavior("deworm_internal", "体内驱虫", "medicine", "#58BFA3", FrequencyType.EVERY_N_DAYS, 90),
            DefaultBehavior("deworm_external", "体外驱虫", "shield", "#58BFA3", FrequencyType.EVERY_N_DAYS, 30),
            DefaultBehavior("grooming", "梳毛", "brush", "#F5B84B", FrequencyType.TIMES_PER_WEEK, null, 3),
            DefaultBehavior("nail_trim", "剪指甲", "scissors", "#F47C6B", FrequencyType.EVERY_N_DAYS, 14),
            DefaultBehavior("medicine", "喂药", "pill", "#D94A4A", FrequencyType.NONE, null),
            DefaultBehavior("weight", "称重", "scale", "#58BFA3", FrequencyType.EVERY_N_DAYS, 30, valueEnabled = true, valueUnit = "kg"),
            DefaultBehavior("vet", "就医", "hospital", "#D94A4A", FrequencyType.NONE, null),
            DefaultBehavior("teeth", "刷牙", "tooth", "#F5B84B", FrequencyType.TIMES_PER_WEEK, null, 3)
        ).mapIndexed { index, behavior ->
            BehaviorTypeEntity(
                id = "${catId}_${behavior.id}",
                catId = catId,
                name = behavior.name,
                iconKey = behavior.iconKey,
                colorHex = behavior.colorHex,
                isBuiltin = true,
                showOnHome = true,
                frequencyType = behavior.frequencyType,
                frequencyValue = behavior.frequencyValue,
                weeklyTarget = behavior.weeklyTarget,
                reminderEnabled = false,
                reminderTime = null,
                isArchived = false,
                archivedAt = null,
                sortOrder = index,
                valueEnabled = behavior.valueEnabled,
                valueLabel = if (behavior.valueEnabled) "数值" else null,
                valueUnit = if (behavior.valueEnabled) behavior.valueUnit else null,
                createdAt = now.plusMillis(index.toLong()),
                updatedAt = now.plusMillis(index.toLong())
            )
        }
    }

    private data class DefaultBehavior(
        val id: String,
        val name: String,
        val iconKey: String,
        val colorHex: String,
        val frequencyType: FrequencyType,
        val frequencyValue: Int?,
        val weeklyTarget: Int? = null,
        val valueEnabled: Boolean = false,
        val valueUnit: String? = null
    )

    private companion object {
        const val KEY_CURRENT_CAT_ID = "current_cat_id"
    }
}
