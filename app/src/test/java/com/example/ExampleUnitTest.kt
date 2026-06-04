package com.example

import com.example.data.DateHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying our safe calendar calculations and streak consecutive day math.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDateHelper_consecutiveDaysAreCorrect() {
        val day1 = "2026-06-03"
        val day2 = "2026-06-04"
        val day3 = "2026-06-05"
        val skipped = "2026-06-06"

        // Verify next day calculation
        val calculatedTomorrow = DateHelper.getTomorrowString(day1)
        assertEquals(day2, calculatedTomorrow)

        // Verify yesterday calculation
        val calculatedYesterday = DateHelper.getYesterdayString(day2)
        assertEquals(day1, calculatedYesterday)

        // Test consecutives
        assertTrue(DateHelper.isConsecutive(day1, day2))
        assertTrue(DateHelper.isConsecutive(day2, day3))
        assertFalse(DateHelper.isConsecutive(day1, day3)) // Gap present
        assertFalse(DateHelper.isConsecutive(day1, skipped)) // Large gap
    }

    @Test
    fun testDateHelper_sameOrLaterComparisonIsReflective() {
        val past = "2026-06-01"
        val present = "2026-06-03"
        val future = "2026-06-05"

        assertTrue(DateHelper.isSameOrLater(present, past))
        assertTrue(DateHelper.isSameOrLater(present, present))
        assertFalse(DateHelper.isSameOrLater(past, present))
        assertTrue(DateHelper.isSameOrLater(future, present))
    }
}
