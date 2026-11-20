package cz.euroklicmapa.util

import java.util.Locale

/** `true` when the UI language is Czech. Every other language gets English (the app is Czech + English). */
fun isCzechUi(): Boolean = Locale.getDefault().language == "cs"

/**
 * Translates a Czech source string. The Czech literal doubles as the key: Czech UI uses it as is,
 * everything else looks it up in [ENGLISH] / [ENGLISH_EXTRA] (a missing key falls back to the Czech text, never blank).
 * Interpolation is positional: `t("Uloženo: %s", count)` — a plain `$count` in the literal would be
 * resolved before the lookup and miss the table.
 *
 * Works anywhere (composables, view models, repositories) because it only reads the process locale.
 */
fun t(cs: String, vararg args: Any?): String {
    val pattern = if (isCzechUi()) cs else ENGLISH[cs] ?: ENGLISH_EXTRA[cs] ?: cs
    return if (args.isEmpty()) pattern else String.format(pattern, *args)
}
