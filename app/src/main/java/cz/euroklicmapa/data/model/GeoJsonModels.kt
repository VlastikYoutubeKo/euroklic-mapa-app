package cz.euroklicmapa.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeatureCollection<T>(
    val type: String,
    val features: List<Feature<T>>
)

@Serializable
data class Feature<T>(
    val type: String,
    val geometry: Geometry,
    val properties: T
)

@Serializable
data class Geometry(
    val type: String,
    val coordinates: List<Double> // [longitude, latitude]
)

// Nullability per the verified 2026-09-02 contract. Every field that the contract lists as
// nullable (or "chybí u N/M záznamů") is nullable here with a default, so one missing key
// can't fail the whole FeatureCollection parse.
@Serializable
data class WcProperties(
    val id: Int,
    val name: String = "",
    val source: String = "",
    val description: String? = null,
    val note: String? = null,
    val amenity: String? = null,
    val photo_url: String? = null,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val last_verified: String? = null,
    /** Canonical web page for this place (`/lokace/{id}-{slug}`). */
    val web_url: String? = null,
    /**
     * Free text station-hall opening hours; ČD stations only (~38 rows), `null` elsewhere.
     * Corrected 2026-09-08 — the backend now scrapes the "Prostory pro cestující" (hall)
     * block, not the ticket-counter one.
     */
    val opening_hours: String? = null,
    /**
     * WC-specific opening hours when cd.cz lists them separately from the hall (~6 rows,
     * usually narrower). Free text; `null` elsewhere. Added 2026-09-08.
     */
    val wc_opening_hours: String? = null,
    /** `"eurokey"` = locked, Euroklíč required. `"unknown"` = origin doesn't say. */
    val access: String? = null,
    /** `"yes" | "no" | "unknown"` — building accessibility, real data for ČD stations only. */
    val wheelchair: String? = null,
    /** Raw multi-line accessibility text; ČD stations only. */
    val accessibility_note: String? = null,
    /** ISO 3166-1 alpha-2 (`"CZ"`, `"DE"`…) or null. Real point-in-polygon. */
    val country: String? = null,
    /** Direct link to the ČD station orientation floor plan (`/planek/{planekId}`); ~60/109 ČD rows, null elsewhere. */
    val floor_plan_url: String? = null,
)

@Serializable
data class PickupPointProperties(
    val id: Int,
    val kraj: String? = null,
    val district: String? = null,
    val org_name: String = "",
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val hours: String? = null,
    val note: String? = null,
    val precision: String? = null,
    val source_url: String? = null,
)
