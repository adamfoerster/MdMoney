package com.mdmoney.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.util.Locale

actual fun systemLanguage(): String = Locale.getDefault().language

actual fun currentYear(): Int = LocalDate.now().year

actual fun currentMonth(): Int = LocalDate.now().monthValue

actual fun currentDay(): Int = LocalDate.now().dayOfMonth

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
