package io.seatlayer.android.compose

import java.util.Locale

/**
 * The language the design source's own string table is written in.
 *
 * Every generated token string is authored here, and every other locale is a
 * translation OF it.
 */
internal const val PICKER_TOKEN_LANGUAGE: String = "en"

/**
 * The strings the picker draws when the host names no locale of its own.
 *
 * The design source carries the wording twice: once in the token table, which
 * is what the chrome is designed and reviewed against, and once as the `en`
 * row of the locale table, which is a translation table that happens to
 * include the language it was translated from. The two have drifted — the
 * token table says "Fit venue" where the `en` row says "Show whole venue", and
 * six more besides — and resolving the device's own English through the locale
 * table therefore drew words no one had approved.
 *
 * The rule the other SDKs settled on, and the one implemented here: **the
 * token table wins unless a host explicitly names a locale.** A host that asks
 * for `en` gets the locale table's `en`, exactly as a host asking for `de`
 * gets `de`; a device that merely happens to be English gets the design
 * source's own words. Every other language is unaffected — a German phone
 * still resolves German, because the token table has no German to prefer.
 *
 * The correction itself belongs upstream in the extractor that writes
 * `Design/locale_strings.json`; this is the resolution rule, not a patch over
 * the rows.
 */
internal fun pickerDefaultStrings(
    languageTag: String = Locale.getDefault().toLanguageTag(),
): SeatLayerPickerStrings {
    val resolved = SeatLayerPickerStrings.localized(languageTag)
    if (resolved.languageTag != PICKER_TOKEN_LANGUAGE) return resolved
    return SeatLayerPickerStrings(languageTag = resolved.languageTag)
}
