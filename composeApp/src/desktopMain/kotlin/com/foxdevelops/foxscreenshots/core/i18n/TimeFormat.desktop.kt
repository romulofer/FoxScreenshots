package com.foxdevelops.foxscreenshots.core.i18n

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

actual fun formatClock(epochMillis: Long): String =
    clock.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
