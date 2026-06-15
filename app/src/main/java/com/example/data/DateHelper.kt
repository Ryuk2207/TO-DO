package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateHelper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun getTodayString(): String {
        return dateFormat.format(Calendar.getInstance().time)
    }

    fun getFormattedDisplayDate(dateString: String): String {
        try {
            val date = dateFormat.parse(dateString) ?: return dateString
            val calendar = Calendar.getInstance()
            calendar.time = date
            
            val todayStr = getTodayString()
            val yesterdayStr = getYesterdayString(todayStr)
            
            if (dateString == todayStr) {
                return "Today"
            } else if (dateString == yesterdayStr) {
                return "Yesterday"
            }
            
            val displayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            return displayFormat.format(date)
        } catch (e: Exception) {
            return dateString
        }
    }

    fun getYesterdayString(dateString: String): String {
        return getOffsetDateString(dateString, -1)
    }

    fun getDayAndDateWithMonth(dateString: String): String {
        try {
            val date = dateFormat.parse(dateString) ?: return dateString
            val shortDisplayFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            return shortDisplayFormat.format(date)
        } catch (e: Exception) {
            return dateString
        }
    }

    fun getTomorrowString(dateString: String): String {
        return getOffsetDateString(dateString, 1)
    }

    fun getOffsetDateString(dateString: String, offsetDays: Int): String {
        try {
            val date = dateFormat.parse(dateString) ?: return getTodayString()
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.DAY_OF_YEAR, offsetDays)
            return dateFormat.format(calendar.time)
        } catch (e: Exception) {
            return getTodayString()
        }
    }

    /**
     * Checks if dateB is exactly the day after dateA.
     */
    fun isConsecutive(dateA: String, dateB: String): Boolean {
        if (dateA.isEmpty() || dateB.isEmpty()) return false
        val tomorrowOfA = getTomorrowString(dateA)
        return tomorrowOfA == dateB
    }

    /**
     * Checks if dateChecked is on or after dateBase (lexicographical comparison is safe for yyyy-MM-dd format).
     */
    fun isSameOrLater(dateChecked: String, dateBase: String): Boolean {
        return dateChecked >= dateBase
    }

    /**
     * Normalizes slot names to guarantee exact matches between database records and preferences,
     * overcoming legacy variations in double-hyphen spacing.
     */
    fun normalizeSlotName(slot: String): String {
        return slot.trim()
    }
}
