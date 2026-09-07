package io.seatlayer.android.compose

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerStringsTest {
    @Test
    fun `ships the locked thirty seven locale catalogue`() {
        assertEquals(37, SeatLayerPickerStrings.supportedLanguageTags.size)
        assertTrue("ar" in SeatLayerPickerStrings.supportedLanguageTags)
        assertTrue("zh-Hans" in SeatLayerPickerStrings.supportedLanguageTags)
        assertTrue("zh-Hant" in SeatLayerPickerStrings.supportedLanguageTags)
    }

    @Test
    fun `resolves exact script region base language and english fallback`() {
        assertEquals("zh-Hant", SeatLayerPickerStrings.localized("zh-TW").languageTag)
        assertEquals("zh-Hans", SeatLayerPickerStrings.localized("zh-CN").languageTag)
        assertEquals("pt", SeatLayerPickerStrings.localized("pt-BR").languageTag)
        assertEquals("en", SeatLayerPickerStrings.localized("xx-ZZ").languageTag)
    }

    @Test
    fun `plural placeholders and host overrides stay typed`() {
        val strings = SeatLayerPickerStrings.localized(
            "de",
            overrides = mapOf("ticketCount.other" to "Tickets: {count}"),
        )

        assertEquals("1 Ticket", strings.ticketCount(1))
        assertEquals("Tickets: 3", strings.ticketCount(3))
        assertTrue("3" in strings.findBestSeats(3))
    }

    @Test
    fun `the token table wins where no host named a locale`() {
        // Seven `en` rows of the locale table disagree with the token table
        // the chrome is designed against. Nothing the buyer sees may come
        // from them unless a host asked for that locale by name.
        val tokens = SeatLayerPickerStrings()
        val fromDevice = pickerDefaultStrings("en-GB")

        assertEquals(tokens.fitVenue, fromDevice.fitVenue)
        assertEquals(tokens.close, fromDevice.close)
        assertEquals(tokens.recentre, fromDevice.recentre)
        assertEquals(tokens.testMode, fromDevice.testMode)
        assertEquals(tokens.accessibility, fromDevice.accessibility)
        assertEquals(tokens.accessibilityTitle, fromDevice.accessibilityTitle)
        assertEquals(tokens.errorMessage, fromDevice.errorMessage)
        // A device in a language the token table has no words for still gets
        // its own: the rule is about English, not about ignoring the catalogue.
        assertEquals("de", pickerDefaultStrings("de-AT").languageTag)
        assertEquals(
            SeatLayerPickerStrings.localized("de-AT").fitVenue,
            pickerDefaultStrings("de-AT").fitVenue,
        )
    }

    @Test
    fun `a host that names en gets the locale files own wording`() {
        val named = SeatLayerPickerStrings.localized("en")

        assertEquals("Show whole venue", named.fitVenue)
        assertTrue(named.fitVenue != SeatLayerPickerStrings().fitVenue)
    }

    @Test
    fun `unknown currency never masquerades as the locale currency`() {
        val formatted = SeatLayerMoneyFormatter.localized(Locale.US)
            .format(12.0, "ZZZ")

        assertEquals("12 ZZZ", formatted)
    }
}
