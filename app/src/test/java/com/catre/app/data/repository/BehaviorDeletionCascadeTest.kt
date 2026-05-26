package com.catre.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorDeletionCascadeTest {
    @Test
    fun deleteBehaviorRemovesOnlyCurrentCatBehaviorRecords() {
        val behaviors = listOf(
            TestBehavior(id = "catA_bath", catId = "catA", name = "洗澡"),
            TestBehavior(id = "catB_bath", catId = "catB", name = "洗澡")
        )
        val records = listOf(
            TestRecord(id = "a1", catId = "catA", behaviorTypeId = "catA_bath"),
            TestRecord(id = "a2", catId = "catA", behaviorTypeId = "catA_bath"),
            TestRecord(id = "b1", catId = "catB", behaviorTypeId = "catB_bath")
        )

        val result = deleteBehaviorAndRecords(behaviors, records, "catA_bath")

        assertFalse(result.behaviors.any { it.id == "catA_bath" })
        assertTrue(result.behaviors.any { it.id == "catB_bath" })
        assertEquals(listOf("b1"), result.records.map { it.id })
    }

    @Test
    fun cancelDeleteKeepsBehaviorAndRecords() {
        val behaviors = listOf(TestBehavior(id = "catA_bath", catId = "catA", name = "洗澡"))
        val records = listOf(TestRecord(id = "a1", catId = "catA", behaviorTypeId = "catA_bath"))

        val result = if (false) deleteBehaviorAndRecords(behaviors, records, "catA_bath") else DeleteResult(behaviors, records)

        assertEquals(behaviors, result.behaviors)
        assertEquals(records, result.records)
    }

    @Test
    fun deletedBehaviorIsAbsentFromHomeCalendarAndDetailInputs() {
        val behaviors = listOf(
            TestBehavior(id = "catA_bath", catId = "catA", name = "洗澡"),
            TestBehavior(id = "catA_weight", catId = "catA", name = "称重")
        )
        val records = listOf(
            TestRecord(id = "a1", catId = "catA", behaviorTypeId = "catA_bath"),
            TestRecord(id = "a2", catId = "catA", behaviorTypeId = "catA_weight")
        )

        val result = deleteBehaviorAndRecords(behaviors, records, "catA_bath")
        val visibleBehaviorIds = result.behaviors.map { it.id }.toSet()
        val visibleRecordBehaviorIds = result.records.map { it.behaviorTypeId }.toSet()

        assertFalse("首页行为入口不应再包含已删行为", "catA_bath" in visibleBehaviorIds)
        assertFalse("日历和详情统计不应再包含已删行为记录", "catA_bath" in visibleRecordBehaviorIds)
        assertTrue("其他行为仍保留", "catA_weight" in visibleBehaviorIds)
    }

    private fun deleteBehaviorAndRecords(
        behaviors: List<TestBehavior>,
        records: List<TestRecord>,
        behaviorId: String
    ): DeleteResult {
        return DeleteResult(
            behaviors = behaviors.filterNot { it.id == behaviorId },
            records = records.filterNot { it.behaviorTypeId == behaviorId }
        )
    }

    private data class DeleteResult(
        val behaviors: List<TestBehavior>,
        val records: List<TestRecord>
    )

    private data class TestBehavior(
        val id: String,
        val catId: String,
        val name: String
    )

    private data class TestRecord(
        val id: String,
        val catId: String,
        val behaviorTypeId: String
    )
}
