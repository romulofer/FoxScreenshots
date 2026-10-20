package com.foxdevelops.foxscreenshots.core

import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.core.i18n.StringsEn
import com.foxdevelops.foxscreenshots.core.i18n.StringsPt
import com.foxdevelops.foxscreenshots.core.i18n.captureFailureMessage
import com.foxdevelops.foxscreenshots.core.i18n.stringsFor
import com.foxdevelops.foxscreenshots.core.settings.LanguagePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StringsTest {
    @Test
    fun `explicit language wins over the system one`() {
        assertSame(StringsEn, stringsFor(LanguagePreference.English, "pt"))
        assertSame(StringsPt, stringsFor(LanguagePreference.Portuguese, "en"))
    }

    @Test
    fun `system language is followed when supported`() {
        assertSame(StringsEn, stringsFor(LanguagePreference.System, "en"))
        assertSame(StringsPt, stringsFor(LanguagePreference.System, "pt"))
    }

    @Test
    fun `unsupported system language falls back to pt-BR`() {
        assertSame(StringsPt, stringsFor(LanguagePreference.System, "de"))
    }

    @Test
    fun `the languages are not copies of each other`() {
        assertEquals(StringsPt.appTitle, StringsEn.appTitle)
        assertNotEquals(StringsPt.undo, StringsEn.undo)
        assertNotEquals(StringsPt.captureInstant, StringsEn.captureInstant)
    }

    @Test
    fun `every capture failure has a localized message`() {
        for (strings in listOf<Strings>(StringsPt, StringsEn)) {
            for (failure in CaptureFailure.entries) {
                assertTrue(strings.captureFailureMessage(failure).isNotBlank(), "$failure")
            }
        }
    }

    @Test
    fun `plurals follow the language`() {
        assertEquals("1 captura salva em /a", StringsPt.savedCountTo(1, "/a"))
        assertEquals("2 capturas salvas em /a", StringsPt.savedCountTo(2, "/a"))
        assertEquals("1 capture saved to /a", StringsEn.savedCountTo(1, "/a"))
        assertEquals("2 captures saved to /a", StringsEn.savedCountTo(2, "/a"))
    }
}
