package com.mdmoney.platform

import kotlinx.coroutines.CoroutineDispatcher

/** Two-letter language code of the current device/OS (e.g. "pt", "en", "es"). */
expect fun systemLanguage(): String

/** The current calendar year in the device's local time zone. */
expect fun currentYear(): Int

/** The current calendar month (1..12) in the device's local time zone. */
expect fun currentMonth(): Int

/** The current day of the month (1..31) in the device's local time zone. */
expect fun currentDay(): Int

/** Dispatcher for blocking disk / SQLite work, kept off the UI thread. */
expect val ioDispatcher: CoroutineDispatcher
