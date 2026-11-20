package cz.euroklicmapa.util

/**
 * The feed intentionally spans beyond CZ/SK. `country` (ISO 3166-1 alpha-2, real
 * point-in-polygon) lets the UI *label* foreign places instead of hiding them.
 */

/** True when a place is neither CZ nor SK (and we actually know its country). */
fun isForeignCountry(iso: String?): Boolean =
    !iso.isNullOrBlank() && iso != "CZ" && iso != "SK"

private val NAMES = mapOf(
    "DE" to t("Německo"), "AT" to t("Rakousko"), "PL" to t("Polsko"), "CH" to t("Švýcarsko"),
    "SK" to t("Slovensko"), "CZ" to t("Česko"), "HU" to t("Maďarsko"), "FR" to t("Francie"),
    "IT" to t("Itálie"), "NL" to t("Nizozemsko"), "BE" to t("Belgie"), "SI" to t("Slovinsko"),
    "NO" to t("Norsko"), "SE" to t("Švédsko"), "DK" to t("Dánsko"), "GB" to t("Spojené království"),
    "ES" to t("Španělsko"), "LU" to t("Lucembursko"),
)

fun countryName(iso: String?): String =
    iso?.let { NAMES[it.uppercase()] ?: it.uppercase() } ?: t("Zahraničí")
