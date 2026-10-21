package com.foxdevelops.foxscreenshots.core.i18n

/** Local wall-clock time of [epochMillis] as `HH:mm`, for the gallery's capture labels. */
expect fun formatClock(epochMillis: Long): String
