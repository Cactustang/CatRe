package com.catre.app

import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.catre.app.core.database.CatReDatabase
import com.catre.app.data.entity.BehaviorTypeEntity
import com.catre.app.data.entity.CatEntity
import com.catre.app.data.entity.CheckInRecordEntity
import com.catre.app.data.entity.FrequencyType
import com.catre.app.data.repository.CatReRepository
import com.catre.app.data.repository.HomeBehaviorSummary
import com.catre.app.data.repository.HomeSnapshot
import com.catre.app.ui.theme.CatReTheme
import java.time.YearMonth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = CatReDatabase.getInstance(applicationContext)
        val repository = CatReRepository(
            database = database,
            catDao = database.catDao(),
            behaviorTypeDao = database.behaviorTypeDao(),
            checkInRecordDao = database.checkInRecordDao(),
            preferences = getSharedPreferences("catre_preferences", MODE_PRIVATE)
        )

        lifecycleScope.launch {
            repository.ensureDefaultsForExistingCat()
        }

        setContent {
            val homeSnapshot by repository.homeSnapshot.collectAsStateWithLifecycle(
                initialValue = HomeSnapshot(cat = null, behaviorSummaries = emptyList(), recordCount = 0)
            )
            val cats by repository.cats.collectAsStateWithLifecycle(initialValue = emptyList())
            val currentCat by repository.currentCat.collectAsStateWithLifecycle(initialValue = null)
            val behaviorTypes by repository.behaviorTypes.collectAsStateWithLifecycle(initialValue = emptyList())
            val archivedBehaviorTypes by repository.archivedBehaviorTypes.collectAsStateWithLifecycle(initialValue = emptyList())
            val records by repository.currentCatRecords.collectAsStateWithLifecycle(initialValue = emptyList())
            CatReTheme {
                CatReAppScreen(
                    homeSnapshot = homeSnapshot,
                    cats = cats,
                    currentCat = currentCat,
                    behaviorTypes = behaviorTypes,
                    archivedBehaviorTypes = archivedBehaviorTypes,
                    records = records,
                    onCreateCat = repository::createCat,
                    onUpdateCat = repository::updateCat,
                    onDeleteCat = repository::deleteCat,
                    onSelectCat = repository::selectCat,
                    onCheckIn = { behaviorTypeId, value, unit -> repository.checkIn(behaviorTypeId, value, unit) },
                    onAddRecordOnDate = { behaviorTypeId, date, value, unit, note, imageUri ->
                        repository.addRecordOnDate(behaviorTypeId, date, value, unit, note, imageUri)
                    },
                    onCreateBehavior = repository::createBehavior,
                    onUpdateBehavior = repository::updateBehavior,
                    onDeleteBehavior = repository::deleteBehavior,
                    onArchiveBehavior = repository::archiveBehavior,
                    onRestoreBehavior = repository::restoreBehavior,
                    onMoveBehavior = repository::moveBehavior,
                    onUpdateRecord = repository::updateRecord,
                    onDeleteRecord = repository::deleteRecord
                )
            }
        }
    }
}

@Composable
fun CatReAppScreen(
    homeSnapshot: HomeSnapshot,
    cats: List<CatEntity>,
    currentCat: CatEntity?,
    behaviorTypes: List<BehaviorTypeEntity>,
    archivedBehaviorTypes: List<BehaviorTypeEntity>,
    records: List<CheckInRecordEntity>,
    onCreateCat: suspend (String, String?, String?, LocalDate?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateCat: suspend (CatEntity, String, String?, String?, LocalDate?, String?, String?) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteCat: suspend (CatEntity) -> Unit = {},
    onSelectCat: suspend (String) -> Unit = {},
    onCheckIn: suspend (String, Double?, String?) -> Unit = { _, _, _ -> },
    onAddRecordOnDate: suspend (String, LocalDate, Double?, String?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onCreateBehavior: suspend (String, String, String, Boolean, FrequencyType, Int?, Int?, Boolean, String?, String?) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onUpdateBehavior: suspend (BehaviorTypeEntity, String, String, String, Boolean, FrequencyType, Int?, Int?, Boolean, String?, String?) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onDeleteBehavior: suspend (BehaviorTypeEntity) -> Unit = {},
    onArchiveBehavior: suspend (BehaviorTypeEntity) -> Unit = {},
    onRestoreBehavior: suspend (BehaviorTypeEntity) -> Unit = {},
    onMoveBehavior: suspend (BehaviorTypeEntity, Int) -> Unit = { _, _ -> },
    onUpdateRecord: suspend (CheckInRecordEntity, Instant, String, Double?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteRecord: suspend (CheckInRecordEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var cachedTabs by rememberSaveable { mutableStateOf(listOf(AppTab.HOME)) }
    var detailBehaviorId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    val allBehaviorTypes = remember(behaviorTypes, archivedBehaviorTypes) {
        behaviorTypes + archivedBehaviorTypes
    }
    val allBehaviorTypesById = remember(allBehaviorTypes) {
        allBehaviorTypes.associateBy { it.id }
    }

    LaunchedEffect(detailBehaviorId, allBehaviorTypesById) {
        val behaviorId = detailBehaviorId
        if (behaviorId != null && allBehaviorTypesById[behaviorId] == null) {
            detailBehaviorId = null
            selectedTab = AppTab.BEHAVIORS
            cachedTabs = cachedTabs.withTab(AppTab.BEHAVIORS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            detailBehaviorId = null
                            detailRecordId = null
                            selectedTab = tab
                            cachedTabs = cachedTabs.withTab(tab)
                        },
                        label = { Text(tab.label) },
                        icon = {
                            Icon(
                                painter = painterResource(id = tab.iconRes),
                                contentDescription = tab.label
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            cachedTabs.forEach { tab ->
                val isActive = selectedTab == tab
                val tabModifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .zIndex(if (isActive) 1f else 0f)
                    .alpha(if (isActive) 1f else 0f)
                when (tab) {
                    AppTab.HOME -> CatReHomeScreen(
                        homeSnapshot = homeSnapshot,
                        onCreateCat = onCreateCat,
                        onCheckIn = onCheckIn,
                        onOpenBehaviorDetail = { detailBehaviorId = it },
                        modifier = tabModifier
                    )
                    AppTab.BEHAVIORS -> BehaviorManagementScreen(
                        behaviorTypes = behaviorTypes,
                        archivedBehaviorTypes = archivedBehaviorTypes,
                        onCreateBehavior = onCreateBehavior,
                        onUpdateBehavior = onUpdateBehavior,
                        onDeleteBehavior = onDeleteBehavior,
                        onArchiveBehavior = onArchiveBehavior,
                        onRestoreBehavior = onRestoreBehavior,
                        onMoveBehavior = onMoveBehavior,
                        onBehaviorDeleted = { deletedBehaviorId ->
                            if (detailBehaviorId == deletedBehaviorId) detailBehaviorId = null
                        },
                        modifier = tabModifier
                    )
                    AppTab.CALENDAR -> CalendarScreen(
                        hasCurrentCat = currentCat != null,
                        behaviorTypes = behaviorTypes,
                        recordBehaviorTypesById = allBehaviorTypesById,
                        records = records,
                        onAddRecordOnDate = onAddRecordOnDate,
                        onOpenBehaviorDetail = { detailBehaviorId = it },
                        onOpenRecordDetail = { detailRecordId = it },
                        onUpdateRecord = onUpdateRecord,
                        onDeleteRecord = onDeleteRecord,
                        modifier = tabModifier
                    )
                    AppTab.ME -> CatManagementScreen(
                        cats = cats,
                        currentCat = currentCat,
                        onCreateCat = onCreateCat,
                        onUpdateCat = onUpdateCat,
                        onDeleteCat = onDeleteCat,
                        onSelectCat = onSelectCat,
                        modifier = tabModifier
                    )
                }
            }
            detailBehaviorId?.let { behaviorId ->
                val behavior = allBehaviorTypesById[behaviorId]
                if (behavior != null) {
                    BehaviorDetailScreen(
                        behaviorType = behavior,
                        records = records.filter { it.behaviorTypeId == behaviorId },
                        onClose = { detailBehaviorId = null },
                        onOpenRecordDetail = { detailRecordId = it },
                        onUpdateRecord = onUpdateRecord,
                        onDeleteRecord = onDeleteRecord,
                        modifier = Modifier.zIndex(2f)
                    )
                }
            }
            detailRecordId?.let { recordId ->
                val record = records.firstOrNull { it.id == recordId }
                val behavior = record?.let { allBehaviorTypesById[it.behaviorTypeId] }
                if (record != null) {
                    RecordDetailScreen(
                        record = record,
                        behaviorType = behavior,
                        onClose = { detailRecordId = null },
                        onUpdateRecord = onUpdateRecord,
                        onDeleteRecord = onDeleteRecord,
                        modifier = Modifier.zIndex(3f)
                    )
                } else {
                    detailRecordId = null
                }
            }
        }
    }
}

private enum class AppTab(val label: String, val iconRes: Int) {
    HOME("首页", R.drawable.ic_nav_home),
    CALENDAR("日历", R.drawable.ic_nav_calendar),
    BEHAVIORS("行为", R.drawable.ic_nav_behavior),
    ME("我的", R.drawable.ic_nav_me)
}

private fun List<AppTab>.withTab(tab: AppTab): List<AppTab> {
    return if (tab in this) this else this + tab
}

@Composable
private fun CatReHomeScreen(
    homeSnapshot: HomeSnapshot,
    onCreateCat: suspend (String, String?, String?, LocalDate?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onCheckIn: suspend (String, Double?, String?) -> Unit = { _, _, _ -> },
    onOpenBehaviorDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            item {
                HomeHero(homeSnapshot)
            }
            item {
                if (homeSnapshot.cat == null) {
                    FirstCatCard(onCreateCat = onCreateCat)
                } else {
                    HomeSummaryCard(homeSnapshot)
                }
            }
            if (homeSnapshot.cat != null) {
                items(homeSnapshot.behaviorSummaries) { summary ->
                    BehaviorTypeRow(
                        summary = summary,
                        onCheckIn = onCheckIn,
                        onOpenDetail = { onOpenBehaviorDetail(summary.behaviorType.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHero(homeSnapshot: HomeSnapshot) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.catre_home_header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.catre_logo),
                contentDescription = null,
                modifier = Modifier
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            CatAvatar(
                cat = homeSnapshot.cat,
                size = 64,
                label = homeSnapshot.cat?.name?.take(1).orEmpty()
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CatRe",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = homeSnapshot.cat?.name ?: "猫咪行为记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "行为 ${homeSnapshot.behaviorSummaries.size} 个 · 记录 ${homeSnapshot.recordCount} 条",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun FirstCatCard(onCreateCat: suspend (String, String?, String?, LocalDate?, String?, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "创建第一只猫",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("猫咪名字") },
                singleLine = true,
                enabled = !isCreating,
                isError = errorMessage != null,
                supportingText = {
                    errorMessage?.let { Text(it) }
                }
            )
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorMessage = "请输入猫咪名字"
                    } else {
                        scope.launch {
                            isCreating = true
                            runCatching { onCreateCat(name, null, null, null, null, null) }
                                .onFailure { errorMessage = it.message ?: "创建失败，请重试" }
                            isCreating = false
                        }
                    }
                },
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isCreating) "创建中" else "开始记录")
            }
        }
    }
}

@Composable
private fun HomeSummaryCard(homeSnapshot: HomeSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = homeSnapshot.cat?.name ?: "暂无猫咪",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "默认行为 ${homeSnapshot.behaviorSummaries.size} 个，累计记录 ${homeSnapshot.recordCount} 条",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BehaviorTypeRow(
    summary: HomeBehaviorSummary,
    onCheckIn: suspend (String, Double?, String?) -> Unit,
    onOpenDetail: () -> Unit
) {
    var isCheckingIn by rememberSaveable(summary.behaviorType.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(summary.behaviorType.id) { mutableStateOf<String?>(null) }
    var weightText by rememberSaveable(summary.behaviorType.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val needsValue = summary.behaviorType.requiresValue()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${behaviorIconSymbol(summary.behaviorType.iconKey)} ${summary.behaviorType.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = behaviorFrequencyText(summary.behaviorType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = lastCheckInText(summary),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "近 30/90/180 天：${summary.count30Days}/${summary.count90Days}/${summary.count180Days} 次",
                    style = MaterialTheme.typography.bodySmall
                )
                if (summary.checkedToday) {
                    Text(
                        text = "今天已打卡",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "状态：${summary.statusText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (needsValue) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { value -> weightText = value.filter { it.isDigit() || it == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("${summary.behaviorType.valueLabelOrDefault()} ${summary.behaviorType.valueUnitOrDefault().orEmpty()}".trim()) },
                        singleLine = true,
                        enabled = !isCheckingIn
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val value = if (needsValue) weightText.toDoubleOrNull() else null
                        if (needsValue && (value == null || value <= 0.0)) {
                            errorMessage = "请输入有效数值"
                            return@Button
                        }
                        scope.launch {
                            isCheckingIn = true
                            errorMessage = null
                            runCatching {
                                onCheckIn(summary.behaviorType.id, value, if (needsValue) summary.behaviorType.valueUnitOrDefault() else null)
                            }.onFailure { errorMessage = it.message ?: "打卡失败，请重试" }
                            if (needsValue) weightText = ""
                            isCheckingIn = false
                        }
                    },
                    enabled = !isCheckingIn
                ) {
                    Text(if (isCheckingIn) "保存中" else "打卡")
                }
                Button(onClick = onOpenDetail) {
                    Text("详情")
                }
            }
        }
    }
}

@Composable
private fun BehaviorManagementScreen(
    behaviorTypes: List<BehaviorTypeEntity>,
    archivedBehaviorTypes: List<BehaviorTypeEntity>,
    onCreateBehavior: suspend (String, String, String, Boolean, FrequencyType, Int?, Int?, Boolean, String?, String?) -> Unit,
    onUpdateBehavior: suspend (BehaviorTypeEntity, String, String, String, Boolean, FrequencyType, Int?, Int?, Boolean, String?, String?) -> Unit,
    onDeleteBehavior: suspend (BehaviorTypeEntity) -> Unit,
    onArchiveBehavior: suspend (BehaviorTypeEntity) -> Unit,
    onRestoreBehavior: suspend (BehaviorTypeEntity) -> Unit,
    onMoveBehavior: suspend (BehaviorTypeEntity, Int) -> Unit,
    onBehaviorDeleted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var formBehaviorId by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var draggedBehaviorId by rememberSaveable { mutableStateOf<String?>(null) }
    var dragStartCenterY by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    val behaviorRowCenters = remember { mutableStateMapOf<String, Float>() }
    val editingId = formBehaviorId
    val editingBehavior = (behaviorTypes + archivedBehaviorTypes).firstOrNull { it.id == editingId }
    val showForm = isCreating || editingBehavior != null
    val visibleBehaviorTypes = if (showArchived) archivedBehaviorTypes else behaviorTypes
    val scope = rememberCoroutineScope()
    val visibleBehaviorIds = remember(visibleBehaviorTypes) { visibleBehaviorTypes.map { it.id } }
    val calculateDragTargetIndex: (Float) -> Int = { targetCenterY ->
        val measuredRows = visibleBehaviorTypes.mapIndexedNotNull { index, behaviorType ->
            behaviorRowCenters[behaviorType.id]?.let { centerY -> index to centerY }
        }
        when {
            visibleBehaviorTypes.isEmpty() -> 0
            measuredRows.isEmpty() -> visibleBehaviorTypes.indexOfFirst { it.id == draggedBehaviorId }.coerceAtLeast(0)
            targetCenterY <= measuredRows.first().second -> measuredRows.first().first
            targetCenterY >= measuredRows.last().second -> measuredRows.last().first
            else -> measuredRows.minBy { (_, centerY) ->
                kotlin.math.abs(centerY - targetCenterY)
            }.first
        }
    }

    LaunchedEffect(visibleBehaviorIds, showForm) {
        if (draggedBehaviorId != null && (showForm || draggedBehaviorId !in visibleBehaviorIds)) {
            draggedBehaviorId = null
            dragOffsetY = 0f
            dragTargetIndex = null
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "行为管理",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = {
                            isCreating = true
                            formBehaviorId = null
                        },
                        enabled = !showForm
                    ) {
                        Text("+")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showArchived,
                        onClick = { showArchived = false },
                        label = { Text("常用行为") }
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = true },
                        label = { Text("已归档 ${archivedBehaviorTypes.size}") }
                    )
                }
            }
            if (showForm) {
                item {
                    BehaviorForm(
                        editingBehavior = editingBehavior,
                        onCancelEdit = {
                            isCreating = false
                            formBehaviorId = null
                        },
                        onCreateBehavior = onCreateBehavior,
                        onUpdateBehavior = onUpdateBehavior,
                        onDeleteBehavior = onDeleteBehavior,
                        onArchiveBehavior = onArchiveBehavior,
                        onRestoreBehavior = onRestoreBehavior,
                        onDeleted = onBehaviorDeleted
                    )
                }
            } else {
                if (visibleBehaviorTypes.isEmpty()) {
                    item {
                        Text(
                            text = if (showArchived) "暂无已归档行为" else "暂无行为，点击右上角 + 新增",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visibleBehaviorTypes) { behaviorType ->
                    val rowIndex = visibleBehaviorTypes.indexOfFirst { it.id == behaviorType.id }
                    val isDragging = draggedBehaviorId == behaviorType.id
                    val targetIndex = dragTargetIndex ?: rowIndex
                    BehaviorManagementRow(
                        behaviorType = behaviorType,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            behaviorRowCenters[behaviorType.id] = coordinates.boundsInWindow().center.y
                        },
                        dragHandleModifier = Modifier.pointerInput(behaviorType.id, visibleBehaviorIds) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedBehaviorId = behaviorType.id
                                    dragStartCenterY = behaviorRowCenters[behaviorType.id] ?: 0f
                                    dragOffsetY = 0f
                                    dragTargetIndex = rowIndex
                                },
                                onDrag = { _, dragAmount ->
                                    dragOffsetY += dragAmount.y
                                    dragTargetIndex = calculateDragTargetIndex(dragStartCenterY + dragOffsetY)
                                },
                                onDragEnd = {
                                    val finalTargetIndex = dragTargetIndex
                                    draggedBehaviorId = null
                                    dragOffsetY = 0f
                                    dragTargetIndex = null
                                    if (finalTargetIndex != null && finalTargetIndex != rowIndex) {
                                        scope.launch { onMoveBehavior(behaviorType, finalTargetIndex) }
                                    }
                                },
                                onDragCancel = {
                                    draggedBehaviorId = null
                                    dragOffsetY = 0f
                                    dragTargetIndex = null
                                }
                            )
                        },
                        isDragging = isDragging,
                        dragHint = if (isDragging) "移动到第 ${targetIndex + 1} 位" else null,
                        onEdit = {
                            isCreating = false
                            formBehaviorId = behaviorType.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorForm(
    editingBehavior: BehaviorTypeEntity?,
    onCancelEdit: () -> Unit,
    onCreateBehavior: suspend (String, String, String, Boolean, FrequencyType, Int?, Int?, Boolean, String?, String?) -> Unit,
    onUpdateBehavior: suspend (BehaviorTypeEntity, String, String, String, Boolean, FrequencyType, Int?, Int?, Boolean, String?, String?) -> Unit,
    onDeleteBehavior: suspend (BehaviorTypeEntity) -> Unit,
    onArchiveBehavior: suspend (BehaviorTypeEntity) -> Unit,
    onRestoreBehavior: suspend (BehaviorTypeEntity) -> Unit,
    onDeleted: (String) -> Unit
) {
    var name by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.name.orEmpty()) }
    var iconKey by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.iconKey ?: "custom") }
    var colorHex by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.colorHex ?: "#F47C6B") }
    var showOnHome by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.showOnHome ?: true) }
    var frequencyType by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.frequencyType ?: FrequencyType.NONE) }
    var frequencyValueText by rememberSaveable(editingBehavior?.id) {
        mutableStateOf((editingBehavior?.frequencyValue ?: editingBehavior?.weeklyTarget ?: 7).toString())
    }
    var isSaving by rememberSaveable(editingBehavior?.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(editingBehavior?.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(editingBehavior?.id) { mutableStateOf(false) }
    var confirmArchive by rememberSaveable(editingBehavior?.id) { mutableStateOf(false) }
    var customColorText by rememberSaveable(editingBehavior?.id) { mutableStateOf(colorHex) }
    var valueEnabled by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.valueEnabled ?: false) }
    var valueLabel by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.valueLabel ?: "数值") }
    var valueUnit by rememberSaveable(editingBehavior?.id) { mutableStateOf(editingBehavior?.valueUnit ?: if (editingBehavior?.isWeightBehavior() == true) "kg" else "") }
    val scope = rememberCoroutineScope()
    val iconOptions = behaviorIconOptions()
    val colorOptions = behaviorColorOptions()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (editingBehavior == null) "新增行为" else "编辑行为",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("行为名称") },
                singleLine = true,
                enabled = !isSaving,
                isError = errorMessage != null,
                supportingText = { errorMessage?.let { Text(it) } }
            )
            Text(text = "图标", style = MaterialTheme.typography.labelLarge)
            iconOptions.chunked(4).forEach { rowIcons ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowIcons.forEach { option ->
                        FilterChip(
                            selected = iconKey == option.key,
                            onClick = { iconKey = option.key },
                            enabled = !isSaving,
                            label = { BehaviorIconLabel(option.key, option.label) }
                        )
                    }
                }
            }
            Text(text = "颜色", style = MaterialTheme.typography.labelLarge)
            colorOptions.chunked(3).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowColors.forEach { option ->
                        FilterChip(
                            selected = colorHex == option.hex,
                            onClick = { colorHex = option.hex },
                            enabled = !isSaving,
                            label = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(14.dp)
                                            .background(parseColor(option.hex), RoundedCornerShape(3.dp))
                                    )
                                    Text(option.label)
                                }
                            }
                        )
                    }
                }
            }
            Text(text = "自定义颜色", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(22.dp)
                        .background(parseColor(customColorText), RoundedCornerShape(4.dp))
                )
                OutlinedTextField(
                    value = customColorText,
                    onValueChange = {
                        customColorText = it
                        if (isValidColorHex(it)) colorHex = it
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("自定义颜色 #RRGGBB") },
                    singleLine = true,
                    enabled = !isSaving
                )
            }
            Text(text = "频率规则", style = MaterialTheme.typography.labelLarge)
            listOf(
                listOf(FrequencyType.NONE, FrequencyType.EVERY_N_DAYS),
                listOf(FrequencyType.TIMES_PER_WEEK, FrequencyType.TIMES_PER_MONTH)
            ).forEach { rowTypes ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowTypes.forEach { type ->
                        FilterChip(
                            selected = frequencyType == type,
                            onClick = { frequencyType = type },
                            label = { Text(frequencyTypeLabel(type)) }
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = showOnHome,
                    onCheckedChange = { showOnHome = it },
                    enabled = !isSaving
                )
                Text("在首页展示")
            }
            if (frequencyType != FrequencyType.NONE) {
                OutlinedTextField(
                    value = frequencyValueText,
                    onValueChange = { frequencyValueText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(frequencyValueLabel(frequencyType)) },
                    singleLine = true,
                    enabled = !isSaving
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = valueEnabled,
                    onCheckedChange = { valueEnabled = it },
                    enabled = !isSaving
                )
                Text("记录时填写数值")
            }
            if (valueEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueLabel,
                        onValueChange = { valueLabel = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("数值名称") },
                        singleLine = true,
                        enabled = !isSaving
                    )
                    OutlinedTextField(
                        value = valueUnit,
                        onValueChange = { valueUnit = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("单位") },
                        singleLine = true,
                        enabled = !isSaving
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val value = frequencyValueText.toIntOrNull()
                        if (name.trim().isEmpty()) {
                            errorMessage = "请输入行为名称"
                            return@Button
                        }
                        if (frequencyType != FrequencyType.NONE && (value == null || value <= 0)) {
                            errorMessage = "请输入有效频率"
                            return@Button
                        }
                        scope.launch {
                            isSaving = true
                            runCatching {
                                if (editingBehavior == null) {
                                    onCreateBehavior(
                                        name,
                                        iconKey,
                                        colorHex,
                                        showOnHome,
                                        frequencyType,
                                        if (frequencyType == FrequencyType.TIMES_PER_WEEK) null else value,
                                        if (frequencyType == FrequencyType.TIMES_PER_WEEK) value else null,
                                        valueEnabled,
                                        valueLabel,
                                        valueUnit
                                    )
                                    name = ""
                                    onCancelEdit()
                                } else {
                                    onUpdateBehavior(
                                        editingBehavior,
                                        name,
                                        iconKey,
                                        colorHex,
                                        showOnHome,
                                        frequencyType,
                                        if (frequencyType == FrequencyType.TIMES_PER_WEEK) null else value,
                                        if (frequencyType == FrequencyType.TIMES_PER_WEEK) value else null,
                                        valueEnabled,
                                        valueLabel,
                                        valueUnit
                                    )
                                    onCancelEdit()
                                }
                            }.onFailure {
                                errorMessage = it.message ?: "保存失败，请重试"
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "保存中" else "保存")
                }
                Button(onClick = onCancelEdit, enabled = !isSaving) {
                    Text("取消")
                }
            }
            if (editingBehavior != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            if (editingBehavior.isArchived) {
                                scope.launch {
                                    isSaving = true
                                    runCatching { onRestoreBehavior(editingBehavior) }
                                        .onSuccess { onCancelEdit() }
                                        .onFailure { errorMessage = it.message ?: "恢复失败，请重试" }
                                    isSaving = false
                                }
                            } else {
                                confirmArchive = true
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text(if (editingBehavior.isArchived) "恢复归档" else "归档行为")
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !isSaving
                    ) {
                        Text("删除行为")
                    }
                }
            }
        }
    }

    if (confirmArchive && editingBehavior != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("归档行为") },
            text = { Text("确认归档 ${editingBehavior.name}？归档后不再用于新增记录和补录选择，但历史记录会继续保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            runCatching { onArchiveBehavior(editingBehavior) }
                                .onSuccess {
                                    confirmArchive = false
                                    onCancelEdit()
                                }
                                .onFailure { errorMessage = it.message ?: "归档失败，请重试" }
                            isSaving = false
                        }
                    }
                ) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text("取消") }
            }
        )
    }

    if (confirmDelete && editingBehavior != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除行为") },
            text = { Text("确认删除 ${editingBehavior.name}？仅删除当前猫的该行为及其关联记录，不影响其他猫。删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            runCatching { onDeleteBehavior(editingBehavior) }
                                .onSuccess {
                                    confirmDelete = false
                                    onDeleted(editingBehavior.id)
                                    onCancelEdit()
                                }
                                .onFailure { errorMessage = it.message ?: "删除失败，请重试" }
                            isSaving = false
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BehaviorManagementRow(
    behaviorType: BehaviorTypeEntity,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean,
    dragHint: String?,
    onEdit: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${behaviorIconSymbol(behaviorType.iconKey)} ${behaviorType.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${if (behaviorType.isBuiltin) "内置" else "自定义"} · ${if (behaviorType.showOnHome) "首页展示" else "首页隐藏"} · ${if (behaviorType.isArchived) "已归档" else "使用中"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(12.dp)
                            .background(parseColor(behaviorType.colorHex), RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "${behaviorColorLabel(behaviorType.colorHex)} · ${behaviorIconLabel(behaviorType.iconKey)} · ${behaviorFrequencyText(behaviorType)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dragHint != null) {
                    Text(
                        text = dragHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onEdit) {
                    Text("编辑")
                }
                Box(
                    modifier = dragHandleModifier
                        .width(28.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDragging) 0.28f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↕",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(
    hasCurrentCat: Boolean,
    behaviorTypes: List<BehaviorTypeEntity>,
    recordBehaviorTypesById: Map<String, BehaviorTypeEntity>,
    records: List<CheckInRecordEntity>,
    onAddRecordOnDate: suspend (String, LocalDate, Double?, String?, String?, String?) -> Unit,
    onOpenBehaviorDetail: (String) -> Unit,
    onOpenRecordDetail: (String) -> Unit,
    onUpdateRecord: suspend (CheckInRecordEntity, Instant, String, Double?, String?, String?) -> Unit,
    onDeleteRecord: suspend (CheckInRecordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedBehaviorId by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var backfillWeightText by rememberSaveable { mutableStateOf("") }
    var backfillNoteText by rememberSaveable { mutableStateOf("") }
    var backfillImageUri by rememberSaveable { mutableStateOf("") }
    var backfillDateText by rememberSaveable { mutableStateOf(selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var showBackfillForm by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val recordsByDate = remember(records) {
        records.groupBy { it.checkedAt.localDate() }
    }
    val selectedRecords = remember(recordsByDate, selectedDate) {
        recordsByDate[selectedDate].orEmpty()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showBackfillForm) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "补录记录",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                showBackfillForm = false
                                errorMessage = null
                            },
                            enabled = !isSaving
                        ) {
                            Text("取消")
                        }
                    }
                }
                item {
                    AddRecordCard(
                        selectedDate = selectedDate,
                        dateText = backfillDateText,
                        hasCurrentCat = hasCurrentCat,
                        behaviorTypes = behaviorTypes,
                        selectedBehaviorId = selectedBehaviorId,
                        weightText = backfillWeightText,
                        noteText = backfillNoteText,
                        imageUri = backfillImageUri,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                        onDateChange = {
                            backfillDateText = it
                            errorMessage = null
                        },
                        onSelectBehavior = {
                            selectedBehaviorId = it
                            errorMessage = null
                        },
                        onWeightChange = { backfillWeightText = it.filter { char -> char.isDigit() || char == '.' } },
                        onNoteChange = { backfillNoteText = it },
                        onImageUriChange = { backfillImageUri = it },
                        onAdd = {
                            val behaviorId = selectedBehaviorId ?: behaviorTypes.firstOrNull()?.id
                            val parsedDate = runCatching { LocalDate.parse(backfillDateText.trim()) }.getOrNull()
                            if (!hasCurrentCat) {
                                errorMessage = "请先新增猫咪"
                                return@AddRecordCard
                            }
                            if (parsedDate == null) {
                                errorMessage = "请输入有效日期"
                                return@AddRecordCard
                            }
                            if (behaviorId == null) {
                                errorMessage = "暂无可补录的行为"
                                return@AddRecordCard
                            }
                            val behavior = behaviorTypes.firstOrNull { it.id == behaviorId }
                            val needsValue = behavior?.requiresValue() == true
                            val value = if (needsValue) backfillWeightText.toDoubleOrNull() else null
                            if (needsValue && (value == null || value <= 0.0)) {
                                errorMessage = "请输入有效数值"
                                return@AddRecordCard
                            }
                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                runCatching {
                                    onAddRecordOnDate(
                                        behaviorId,
                                        parsedDate,
                                        value,
                                        behavior?.valueUnitOrDefault(),
                                        backfillNoteText,
                                        backfillImageUri
                                    )
                                }
                                    .onSuccess {
                                        selectedDate = parsedDate
                                        visibleMonth = YearMonth.from(parsedDate)
                                        backfillDateText = parsedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        backfillWeightText = ""
                                        backfillNoteText = ""
                                        backfillImageUri = ""
                                        showBackfillForm = false
                                    }
                                    .onFailure { errorMessage = it.message ?: "补录失败，请重试" }
                                isSaving = false
                            }
                        }
                    )
                }
            }
            return@Surface
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "日历",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                CalendarMonthCard(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    recordsByDate = recordsByDate,
                    onPreviousMonth = {
                        val nextMonth = visibleMonth.minusMonths(1)
                        visibleMonth = nextMonth
                        selectedDate = nextMonth.atDay(1)
                    },
                    onNextMonth = {
                        val nextMonth = visibleMonth.plusMonths(1)
                        visibleMonth = nextMonth
                        selectedDate = nextMonth.atDay(1)
                    },
                    onSelectDate = { selectedDate = it }
                )
            }
            item {
                Button(
                    onClick = {
                        backfillDateText = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        errorMessage = null
                        showBackfillForm = true
                    },
                    enabled = hasCurrentCat
                ) {
                    Text("补录")
                }
                if (!hasCurrentCat) {
                    Text(
                        text = "请先新增猫咪后再补录记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Text(
                    text = "${selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))} 记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (selectedRecords.isEmpty()) {
                item {
                    Text(
                        text = "当天暂无记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(selectedRecords) { record ->
                    CalendarRecordRow(
                        record = record,
                        behaviorType = recordBehaviorTypesById[record.behaviorTypeId],
                        onOpenBehaviorDetail = { onOpenBehaviorDetail(record.behaviorTypeId) },
                        onOpenRecordDetail = { onOpenRecordDetail(record.id) },
                        onUpdateRecord = onUpdateRecord,
                        onDeleteRecord = onDeleteRecord
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthCard(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    recordsByDate: Map<LocalDate, List<CheckInRecordEntity>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onPreviousMonth) { Text("上月") }
                Text(
                    text = visibleMonth.format(DateTimeFormatter.ofPattern("yyyy 年 MM 月")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(onClick = onNextMonth) { Text("下月") }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            calendarWeeks(visibleMonth).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            visibleMonth = visibleMonth,
                            selected = date == selectedDate,
                            recordCount = recordsByDate[date]?.size ?: 0,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    visibleMonth: YearMonth,
    selected: Boolean,
    recordCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentMonth = date.month == visibleMonth.month
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        recordCount > 0 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.background
    }
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.86f),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background, RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (recordCount > 0) {
                Text(
                    text = recordCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AddRecordCard(
    selectedDate: LocalDate,
    dateText: String,
    hasCurrentCat: Boolean,
    behaviorTypes: List<BehaviorTypeEntity>,
    selectedBehaviorId: String?,
    weightText: String,
    noteText: String,
    imageUri: String,
    isSaving: Boolean,
    errorMessage: String?,
    onDateChange: (String) -> Unit,
    onSelectBehavior: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onImageUriChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    val selectedBehavior = behaviorTypes.firstOrNull { it.id == (selectedBehaviorId ?: behaviorTypes.firstOrNull()?.id) }
    val needsValue = selectedBehavior?.requiresValue() == true
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onImageUriChange(uri.toString())
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "补录 ${selectedDate.format(DateTimeFormatter.ofPattern("MM-dd"))}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = onDateChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("日期 yyyy-MM-dd") },
                singleLine = true,
                enabled = !isSaving
            )
            if (!hasCurrentCat) {
                Text("请先新增猫咪后再补录记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (behaviorTypes.isEmpty()) {
                Text("暂无行为可选", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                behaviorTypes.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { behavior ->
                            FilterChip(
                                selected = (selectedBehaviorId ?: behaviorTypes.first().id) == behavior.id,
                                onClick = { onSelectBehavior(behavior.id) },
                                label = { Text(behavior.name) }
                            )
                        }
                    }
                }
            }
            if (needsValue) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = onWeightChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${selectedBehavior?.valueLabelOrDefault() ?: "数值"} ${selectedBehavior?.valueUnitOrDefault().orEmpty()}".trim()) },
                    singleLine = true,
                    enabled = !isSaving
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !isSaving) {
                    Text(if (imageUri.isBlank()) "添加图片" else "更换图片")
                }
                if (imageUri.isNotBlank()) {
                    TextButton(onClick = { onImageUriChange("") }, enabled = !isSaving) {
                        Text("移除图片")
                    }
                }
            }
            if (imageUri.isNotBlank()) {
                Text("已选择图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                enabled = !isSaving
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onAdd, enabled = !isSaving && hasCurrentCat && behaviorTypes.isNotEmpty()) {
                Text(if (isSaving) "补录中" else "补录记录")
            }
        }
    }
}

@Composable
private fun CalendarRecordRow(
    record: CheckInRecordEntity,
    behaviorType: BehaviorTypeEntity?,
    onOpenBehaviorDetail: () -> Unit,
    onOpenRecordDetail: () -> Unit,
    onUpdateRecord: suspend (CheckInRecordEntity, Instant, String, Double?, String?, String?) -> Unit,
    onDeleteRecord: suspend (CheckInRecordEntity) -> Unit
) {
    EditableRecordRow(
        record = record,
        behaviorType = behaviorType,
        onUpdateRecord = onUpdateRecord,
        onRequestDelete = { },
        modifier = Modifier.fillMaxWidth(),
        onDeleteRecord = onDeleteRecord,
        title = behaviorType?.let { "${behaviorIconSymbol(it.iconKey)} ${it.name}" } ?: "未知行为",
        extraActionLabel = "记录详情",
        onExtraAction = onOpenRecordDetail
    )
}

@Composable
private fun BehaviorDetailScreen(
    behaviorType: BehaviorTypeEntity,
    records: List<CheckInRecordEntity>,
    onClose: () -> Unit,
    onOpenRecordDetail: (String) -> Unit,
    onUpdateRecord: suspend (CheckInRecordEntity, Instant, String, Double?, String?, String?) -> Unit,
    onDeleteRecord: suspend (CheckInRecordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteRecord = records.firstOrNull { it.id == pendingDelete }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${behaviorIconSymbol(behaviorType.iconKey)} ${behaviorType.name}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = behaviorFrequencyText(behaviorType),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(onClick = onClose) { Text("关闭") }
                }
            }
            item {
                BehaviorDetailSummary(behaviorType = behaviorType, records = records)
            }
            if (records.isEmpty()) {
                item {
                    Text(
                        text = "暂无历史记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(records.sortedByDescending { it.checkedAt }) { record ->
                    EditableRecordRow(
                        record = record,
                        behaviorType = behaviorType,
                        onUpdateRecord = onUpdateRecord,
                        onRequestDelete = { pendingDelete = record.id },
                        title = record.checkedAt.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        extraActionLabel = "记录详情",
                        onExtraAction = { onOpenRecordDetail(record.id) }
                    )
                }
            }
        }
    }

    if (pendingDeleteRecord != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("确认删除这条记录？删除后首页统计和日历会同步更新。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDeleteRecord(pendingDeleteRecord)
                            pendingDelete = null
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BehaviorDetailSummary(
    behaviorType: BehaviorTypeEntity,
    records: List<CheckInRecordEntity>
) {
    val now = Instant.now()
    val count30 = records.count { !it.checkedAt.isBefore(now.minus(30, java.time.temporal.ChronoUnit.DAYS)) }
    val count90 = records.count { !it.checkedAt.isBefore(now.minus(90, java.time.temporal.ChronoUnit.DAYS)) }
    val count180 = records.count { !it.checkedAt.isBefore(now.minus(180, java.time.temporal.ChronoUnit.DAYS)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("近 30/90/180 天：$count30/$count90/$count180 次")
            Text("累计记录：${records.size} 条")
            if (behaviorType.requiresValue()) {
                val latestValue = records
                    .filter { it.value != null }
                    .maxByOrNull { it.checkedAt }
                Text("最近${behaviorType.valueLabelOrDefault()}：${latestValue?.value?.let { "$it ${latestValue.valueUnit ?: behaviorType.valueUnitOrDefault().orEmpty()}".trim() } ?: "暂无"}")
            }
        }
    }
}

@Composable
private fun RecordDetailScreen(
    record: CheckInRecordEntity,
    behaviorType: BehaviorTypeEntity?,
    onClose: () -> Unit,
    onUpdateRecord: suspend (CheckInRecordEntity, Instant, String, Double?, String?, String?) -> Unit,
    onDeleteRecord: suspend (CheckInRecordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable(record.id) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "记录详情",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = behaviorType?.let { "${behaviorIconSymbol(it.iconKey)} ${it.name}" } ?: "未知行为",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(onClick = onClose) { Text("关闭") }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = record.checkedAt.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(recordSummaryText(record), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("创建：${record.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                        Text("更新：${record.updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                        if (record.imageUri != null) {
                            Text("图片已保存", color = MaterialTheme.colorScheme.secondary)
                            UriImagePreview(imageUri = record.imageUri, modifier = Modifier.fillMaxWidth().height(180.dp))
                        }
                    }
                }
            }
            item {
                EditableRecordRow(
                    record = record,
                    behaviorType = behaviorType,
                    onUpdateRecord = onUpdateRecord,
                    onRequestDelete = { confirmDelete = true },
                    onDeleteRecord = onDeleteRecord,
                    title = "编辑记录"
                )
            }
            item {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除记录")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录") },
            text = { Text("确认删除这条记录？删除后首页统计和日历会同步更新。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDeleteRecord(record)
                            confirmDelete = false
                            onClose()
                        }
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EditableRecordRow(
    record: CheckInRecordEntity,
    behaviorType: BehaviorTypeEntity?,
    onUpdateRecord: suspend (CheckInRecordEntity, Instant, String, Double?, String?, String?) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteRecord: (suspend (CheckInRecordEntity) -> Unit)? = null,
    title: String = record.checkedAt.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null
) {
    var note by rememberSaveable(record.id) { mutableStateOf(record.note.orEmpty()) }
    var dateText by rememberSaveable(record.id) {
        mutableStateOf(record.checkedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    }
    var timeText by rememberSaveable(record.id) {
        mutableStateOf(record.checkedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var valueText by rememberSaveable(record.id) { mutableStateOf(record.value?.toString().orEmpty()) }
    var imageUri by rememberSaveable(record.id) { mutableStateOf(record.imageUri.orEmpty()) }
    var isSaving by rememberSaveable(record.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(record.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(record.id) { mutableStateOf(false) }
    var isEditing by rememberSaveable(record.id) { mutableStateOf(false) }
    var menuExpanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val needsValue = behaviorType?.requiresValue() == true || record.value != null
    val valueLabel = behaviorType?.valueLabelOrDefault() ?: "数值"
    val valueUnit = behaviorType?.valueUnitOrDefault() ?: record.valueUnit
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imageUri = uri.toString()
        }
    }

    val cardModifier = if (extraActionLabel != null && onExtraAction != null && !isEditing) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onExtraAction)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = recordSummaryText(record),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (record.imageUri != null) {
                        Text(
                            text = "含图片",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (extraActionLabel != null && onExtraAction != null && !isEditing) {
                    TextButton(onClick = onExtraAction) {
                        Text(extraActionLabel)
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Text("⋮", style = MaterialTheme.typography.titleMedium)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                menuExpanded = false
                                isEditing = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                if (onDeleteRecord == null) onRequestDelete() else confirmDelete = true
                            }
                        )
                    }
                }
            }
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("日期") },
                        singleLine = true,
                        enabled = !isSaving
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("时间") },
                        singleLine = true,
                        enabled = !isSaving
                    )
                }
                if (needsValue) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it.filter { char -> char.isDigit() || char == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("$valueLabel ${valueUnit.orEmpty()}".trim()) },
                        singleLine = true,
                        enabled = !isSaving
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !isSaving) {
                        Text(if (imageUri.isBlank()) "添加图片" else "更换图片")
                    }
                    if (imageUri.isNotBlank()) {
                        TextButton(onClick = { imageUri = "" }, enabled = !isSaving) {
                            Text("移除图片")
                        }
                    }
                }
                if (imageUri.isNotBlank()) {
                    Text("已选择图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    enabled = !isSaving
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
                            val parsedTime = runCatching { LocalTime.parse(timeText) }.getOrNull()
                            if (parsedDate == null || parsedTime == null) {
                                errorMessage = "请输入有效日期和时间"
                                return@Button
                            }
                            val value = if (needsValue) valueText.toDoubleOrNull() else record.value
                            if (needsValue && (value == null || value <= 0.0)) {
                                errorMessage = "请输入有效数值"
                                return@Button
                            }
                            val checkedAt = ZonedDateTime.of(parsedDate, parsedTime, ZoneId.systemDefault()).toInstant()
                            scope.launch {
                                isSaving = true
                                runCatching {
                                    onUpdateRecord(record, checkedAt, note, value, if (needsValue) valueUnit else record.valueUnit, imageUri)
                                }
                                    .onSuccess { isEditing = false }
                                    .onFailure { errorMessage = it.message ?: "保存失败，请重试" }
                                isSaving = false
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "保存中" else "保存备注")
                    }
                    Button(onClick = { isEditing = false }, enabled = !isSaving) {
                        Text("取消")
                    }
                }
            }
        }
    }

    if (confirmDelete && onDeleteRecord != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录") },
            text = { Text("确认删除这条记录？删除后首页统计和日历会同步更新。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDeleteRecord(record)
                            confirmDelete = false
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CatManagementScreen(
    cats: List<CatEntity>,
    currentCat: CatEntity?,
    onCreateCat: suspend (String, String?, String?, LocalDate?, String?, String?) -> Unit,
    onUpdateCat: suspend (CatEntity, String, String?, String?, LocalDate?, String?, String?) -> Unit,
    onDeleteCat: suspend (CatEntity) -> Unit,
    onSelectCat: suspend (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var editingCatId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingCat = cats.firstOrNull { it.id == editingCatId }
    val pendingDeleteCat = cats.firstOrNull { it.id == pendingDeleteId }
    val showForm = isCreating || editingCat != null
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "猫咪资料",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentCat?.let { "当前：${it.name}" } ?: "暂无当前猫咪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            isCreating = true
                            editingCatId = null
                        },
                        enabled = !showForm
                    ) {
                        Text("+")
                    }
                }
            }
            if (showForm) {
                item {
                    CatProfileForm(
                        editingCat = editingCat,
                        onCancel = {
                            isCreating = false
                            editingCatId = null
                        },
                        onCreateCat = onCreateCat,
                        onUpdateCat = onUpdateCat
                    )
                }
            } else if (cats.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("还没有猫咪资料", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "点击右上角 + 新增猫咪后即可开始记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(cats) { cat ->
                    CatProfileRow(
                        cat = cat,
                        isCurrent = currentCat?.id == cat.id,
                        onSelect = {
                            scope.launch { onSelectCat(cat.id) }
                        },
                        onEdit = {
                            isCreating = false
                            editingCatId = cat.id
                        },
                        onDelete = {
                            pendingDeleteId = cat.id
                        }
                    )
                }
            }
        }
    }

    if (pendingDeleteCat != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除猫咪资料") },
            text = {
                Text("确认删除 ${pendingDeleteCat.name}？该猫咪关联的打卡记录也会一起删除，删除后不可恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDeleteCat(pendingDeleteCat)
                            pendingDeleteId = null
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CatProfileRow(
    cat: CatEntity,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatAvatar(cat = cat, size = 48, label = cat.name.take(1).ifEmpty { "猫" })
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrent) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = catProfileSummary(cat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onSelect, enabled = !isCurrent) {
                    Text(if (isCurrent) "已选" else "切换")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onEdit) { Text("编辑") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun CatAvatar(
    cat: CatEntity?,
    size: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avatarUri = cat?.avatarUri?.trim().orEmpty()
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, avatarUri, context) {
        if (avatarUri.isBlank()) {
            value = null
        } else {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(avatarUri))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
        }
    }
    val imageBitmap = remember(bitmap) {
        bitmap?.asImageBitmap()
    }
    val containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
    val contentDescription = cat?.name ?: "猫咪头像"
    val fallbackLabel = label.ifBlank { "猫" }
    Box(
        modifier = modifier
            .width(size.dp)
            .height(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallbackLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UriImagePreview(
    imageUri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, imageUri, context) {
        value = if (imageUri.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
        }
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "记录图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("图片暂不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CatProfileForm(
    editingCat: CatEntity?,
    onCancel: () -> Unit,
    onCreateCat: suspend (String, String?, String?, LocalDate?, String?, String?) -> Unit,
    onUpdateCat: suspend (CatEntity, String, String?, String?, LocalDate?, String?, String?) -> Unit
) {
    var name by rememberSaveable(editingCat?.id) { mutableStateOf(editingCat?.name.orEmpty()) }
    var gender by rememberSaveable(editingCat?.id) { mutableStateOf(editingCat?.gender.orEmpty()) }
    var avatarUri by rememberSaveable(editingCat?.id) { mutableStateOf(editingCat?.avatarUri.orEmpty()) }
    var birthdayText by rememberSaveable(editingCat?.id) {
        mutableStateOf(editingCat?.birthday?.format(DateTimeFormatter.ISO_LOCAL_DATE).orEmpty())
    }
    var breed by rememberSaveable(editingCat?.id) { mutableStateOf(editingCat?.breed.orEmpty()) }
    var note by rememberSaveable(editingCat?.id) { mutableStateOf(editingCat?.note.orEmpty()) }
    var isSaving by rememberSaveable(editingCat?.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(editingCat?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            avatarUri = uri.toString()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingCat == null) "新增猫咪" else "编辑猫咪",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CatAvatar(
                    cat = editingCat?.copy(avatarUri = avatarUri.trim().ifEmpty { null }),
                    size = 56,
                    label = name.take(1).ifEmpty { "猫" },
                    modifier = Modifier.clickable { avatarPicker.launch(arrayOf("image/*")) }
                )
                Text(
                    text = "点击头像选择图片；取消或加载失败时显示占位头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("昵称") },
                singleLine = true,
                enabled = !isSaving
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("男孩", "女孩", "未知").forEach { option ->
                    FilterChip(
                        selected = gender == option || (option == "未知" && gender.isBlank()),
                        onClick = { gender = if (option == "未知") "" else option },
                        enabled = !isSaving,
                        label = { Text(option) }
                    )
                }
            }
            OutlinedTextField(
                value = birthdayText,
                onValueChange = {
                    birthdayText = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("生日 yyyy-MM-dd") },
                singleLine = true,
                enabled = !isSaving
            )
            OutlinedTextField(
                value = breed,
                onValueChange = { breed = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("品种") },
                singleLine = true,
                enabled = !isSaving
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                enabled = !isSaving
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val birthday = if (birthdayText.isBlank()) {
                            null
                        } else {
                            runCatching { LocalDate.parse(birthdayText.trim()) }.getOrNull()
                        }
                        if (name.trim().isEmpty()) {
                            errorMessage = "请输入猫咪昵称"
                            return@Button
                        }
                        if (birthdayText.isNotBlank() && birthday == null) {
                            errorMessage = "生日格式应为 yyyy-MM-dd"
                            return@Button
                        }
                        scope.launch {
                            isSaving = true
                            runCatching {
                                if (editingCat == null) {
                                    onCreateCat(name, gender, avatarUri, birthday, breed, note)
                                } else {
                                    onUpdateCat(editingCat, name, gender, avatarUri, birthday, breed, note)
                                }
                                onCancel()
                            }.onFailure {
                                errorMessage = it.message ?: "保存失败，请重试"
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "保存中" else "保存")
                }
                Button(onClick = onCancel, enabled = !isSaving) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun frequencyTypeLabel(type: FrequencyType): String {
    return when (type) {
        FrequencyType.NONE -> "按需"
        FrequencyType.EVERY_N_DAYS -> "每 N 天"
        FrequencyType.TIMES_PER_WEEK -> "每周 N 次"
        FrequencyType.TIMES_PER_MONTH -> "每月 N 次"
    }
}

private fun frequencyValueLabel(type: FrequencyType): String {
    return when (type) {
        FrequencyType.NONE -> "频率"
        FrequencyType.EVERY_N_DAYS -> "天数"
        FrequencyType.TIMES_PER_WEEK -> "每周次数"
        FrequencyType.TIMES_PER_MONTH -> "每月次数"
    }
}

private fun calendarWeeks(month: YearMonth): List<List<LocalDate>> {
    val firstDay = month.atDay(1)
    val gridStart = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    return (0 until 6).map { week ->
        (0 until 7).map { day ->
            gridStart.plusDays((week * 7 + day).toLong())
        }
    }
}

private fun Instant.localDate(): LocalDate {
    return atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun recordSummaryText(record: CheckInRecordEntity): String {
    val time = record.checkedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    val valueText = record.value?.let { " · $it ${record.valueUnit ?: ""}".trimEnd() }.orEmpty()
    val noteText = record.note?.let { " · $it" }.orEmpty()
    return "$time$valueText$noteText"
}

private fun BehaviorTypeEntity.isWeightBehavior(): Boolean {
    return id == "weight" || name.contains("称重")
}

private fun BehaviorTypeEntity.requiresValue(): Boolean {
    return valueEnabled || isWeightBehavior()
}

private fun BehaviorTypeEntity.valueLabelOrDefault(): String {
    return valueLabel?.takeIf { it.isNotBlank() } ?: if (isWeightBehavior()) "体重" else "数值"
}

private fun BehaviorTypeEntity.valueUnitOrDefault(): String? {
    return valueUnit?.takeIf { it.isNotBlank() } ?: if (isWeightBehavior()) "kg" else null
}

private fun lastCheckInText(summary: HomeBehaviorSummary): String {
    val lastCheckedAt = summary.lastCheckedAt ?: return "暂无记录"
    val date = lastCheckedAt
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    return "上次 $date，距今天 ${summary.daysSinceLast ?: 0} 天"
}

private fun behaviorFrequencyText(behaviorType: BehaviorTypeEntity): String {
    return when (behaviorType.frequencyType) {
        FrequencyType.NONE -> "按需记录"
        FrequencyType.EVERY_N_DAYS -> "每 ${behaviorType.frequencyValue ?: 0} 天"
        FrequencyType.TIMES_PER_WEEK -> "每周 ${behaviorType.weeklyTarget ?: 0} 次"
        FrequencyType.TIMES_PER_MONTH -> "每月 ${behaviorType.frequencyValue ?: 0} 次"
    }
}

private fun catProfileSummary(cat: CatEntity): String {
    val parts = listOfNotNull(
        cat.gender?.takeIf { it.isNotBlank() },
        cat.birthday?.let { birthday ->
            val years = java.time.Period.between(birthday, LocalDate.now()).years
            if (years > 0) "${birthday.format(DateTimeFormatter.ISO_LOCAL_DATE)} · ${years} 岁" else birthday.format(DateTimeFormatter.ISO_LOCAL_DATE)
        },
        cat.breed?.takeIf { it.isNotBlank() },
        cat.note?.takeIf { it.isNotBlank() }
    )
    return parts.ifEmpty { listOf("资料待补充") }.joinToString(" · ")
}

private data class BehaviorIconOption(
    val key: String,
    val label: String,
    val symbol: String
)

private data class BehaviorColorOption(
    val hex: String,
    val label: String
)

private fun behaviorIconOptions(): List<BehaviorIconOption> {
    return listOf(
        BehaviorIconOption("bath", "洗澡", "♨"),
        BehaviorIconOption("scale", "称重", "⚖"),
        BehaviorIconOption("weight", "称重", "⚖"),
        BehaviorIconOption("scissors", "剪指甲", "✂"),
        BehaviorIconOption("nail", "剪指甲", "✂"),
        BehaviorIconOption("medicine", "驱虫", "●"),
        BehaviorIconOption("shield", "防护", "◆"),
        BehaviorIconOption("brush", "梳毛", "▥"),
        BehaviorIconOption("pill", "喂药", "●"),
        BehaviorIconOption("hospital", "就医", "+"),
        BehaviorIconOption("tooth", "刷牙", "◇"),
        BehaviorIconOption("vaccine", "疫苗", "✚"),
        BehaviorIconOption("custom", "自定义", "★")
    )
}

@Composable
private fun BehaviorIconLabel(iconKey: String, label: String = behaviorIconLabel(iconKey)) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(behaviorIconSymbol(iconKey), style = MaterialTheme.typography.bodyMedium)
        Text(label)
    }
}

private fun behaviorColorOptions(): List<BehaviorColorOption> {
    return listOf(
        BehaviorColorOption("#F47C6B", "珊瑚红"),
        BehaviorColorOption("#F6B44B", "暖橙"),
        BehaviorColorOption("#F2D15C", "麦黄色"),
        BehaviorColorOption("#6CBF84", "草木绿"),
        BehaviorColorOption("#58BFA3", "青绿色"),
        BehaviorColorOption("#4DA3D9", "湖蓝"),
        BehaviorColorOption("#3F6FD9", "海蓝"),
        BehaviorColorOption("#8D7BE8", "柔紫"),
        BehaviorColorOption("#E986B9", "樱花粉"),
        BehaviorColorOption("#9B6A4A", "栗棕色"),
        BehaviorColorOption("#7D8A99", "石灰灰"),
        BehaviorColorOption("#333A45", "深石色"),
        BehaviorColorOption("#F4EEE3", "浅米色")
    )
}

private fun behaviorIconLabel(iconKey: String): String {
    return behaviorIconOptions().firstOrNull { it.key == iconKey }?.label ?: "自定义"
}

private fun behaviorIconSymbol(iconKey: String): String {
    return behaviorIconOptions().firstOrNull { it.key == iconKey }?.symbol ?: "★"
}

private fun behaviorColorLabel(colorHex: String): String {
    return behaviorColorOptions().firstOrNull { it.hex.equals(colorHex, ignoreCase = true) }?.label ?: "自定义颜色"
}

private fun parseColor(colorHex: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color(0xFFF47C6B))
}

private fun isValidColorHex(colorHex: String): Boolean {
    return Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$").matches(colorHex.trim())
}

@Preview(showBackground = true)
@Composable
private fun CatReHomeScreenPreview() {
    val now = Instant.EPOCH
    CatReTheme {
        CatReHomeScreen(
            homeSnapshot = HomeSnapshot(
                cat = CatEntity(
                    id = "preview",
                    name = "第一只猫",
                    avatarUri = null,
                    gender = null,
                    birthday = null,
                    breed = null,
                    note = null,
                    createdAt = now,
                    updatedAt = now
                ),
                behaviorSummaries = listOf(
                    HomeBehaviorSummary(
                        behaviorType = BehaviorTypeEntity(
                            id = "bath",
                            catId = "preview",
                            name = "洗澡",
                            iconKey = "bath",
                            colorHex = "#F47C6B",
                            isBuiltin = true,
                            showOnHome = true,
                            frequencyType = FrequencyType.EVERY_N_DAYS,
                            frequencyValue = 30,
                            weeklyTarget = null,
                            reminderEnabled = false,
                            reminderTime = null,
                            isArchived = false,
                            archivedAt = null,
                            sortOrder = 0,
                            valueEnabled = false,
                            valueLabel = null,
                            valueUnit = null,
                            createdAt = now,
                            updatedAt = now
                        ),
                        lastCheckedAt = now,
                        daysSinceLast = 0,
                        count30Days = 1,
                        count90Days = 1,
                        count180Days = 1,
                        checkedToday = true,
                        statusText = "今天已完成"
                    )
                ),
                recordCount = 0
            )
        )
    }
}
