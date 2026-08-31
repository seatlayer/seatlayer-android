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
    fun `unknown currency never masquerades as the locale currency`() {
        val formatted = SeatLayerPickerMoneyFormatter.localized(Locale.US)
            .format(12.0, "ZZZ")

        assertEquals("12 ZZZ", formatted)
    }
}
