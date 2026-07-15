package com.mdmoney.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun systemLanguage(): String = NSLocale.currentLocale.languageCode ?: "en"

actual fun currentYear(): Int {
    val components = NSCalendar.currentCalendar.components(NSCalendarUnitYear, NSDate())
    return components.year.toInt()
}

actual fun currentMonth(): Int {
    val components = NSCalendar.currentCalendar.components(NSCalendarUnitMonth, NSDate())
    return components.month.toInt()
}

actual fun currentDay(): Int {
    val components = NSCalendar.currentCalendar.components(NSCalendarUnitDay, NSDate())
    return components.day.toInt()
}

// Confined to Default rather than IO — Dispatchers.IO isn't guaranteed on Kotlin/Native; the single
// SQLite connection is additionally serialized by a Mutex in CacheDb.
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
