package cz.euroklicmapa.util

/**
 * The feed intentionally spans beyond CZ/SK. `country` (ISO 3166-1 alpha-2, real
 * point-in-polygon) lets the UI *label* foreign places instead of hiding them.
 */

/** True when a place is neither CZ nor SK (and we actually know its country). */
fun isForeignCountry(iso: String?): Boolean =
    !iso.isNullOrBlank() && iso != "CZ" && iso != "SK"

private val NAMES = mapOf(
    "DE" to "Německo", "AT" to "Rakousko", "PL" to "Polsko", "CH" to "Švýcarsko",
    "SK" to "Slovensko", "CZ" to "Česko", "HU" to "Maďarsko", "FR" to "Francie",
    "IT" to "Itálie", "NL" to "Nizozemsko", "BE" to "Belgie", "SI" to "Slovinsko",
    "NO" to "Norsko", "SE" to "Švédsko", "DK" to "Dánsko", "GB" to "Spojené království",
    "ES" to "Španělsko", "LU" to "Lucembursko",
)

fun countryName(iso: String?): String =
    iso?.let { NAMES[it.uppercase()] ?: it.uppercase() } ?: "Zahraničí"
