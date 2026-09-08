package cz.euroklicmapa.data.mapper

import cz.euroklicmapa.data.local.FavoriteEntity
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.model.Feature
import cz.euroklicmapa.data.model.PickupPointProperties
import cz.euroklicmapa.data.model.WcProperties

// GeoJSON coordinates are [lon, lat] (WGS84). A malformed/missing pair becomes NaN; the
// repository drops non-finite rows before caching.
private fun Feature<*>.lon(): Double = geometry.coordinates.getOrElse(0) { Double.NaN }
private fun Feature<*>.lat(): Double = geometry.coordinates.getOrElse(1) { Double.NaN }

fun Feature<WcProperties>.toEntity(): WcLocationEntity = WcLocationEntity(
    id = properties.id,
    name = properties.name,
    source = properties.source,
    description = properties.description,
    note = properties.note,
    photoUrl = properties.photo_url,
    likes = properties.likes,
    dislikes = properties.dislikes,
    lastVerified = properties.last_verified,
    webUrl = properties.web_url,
    openingHours = properties.opening_hours,
    access = properties.access,
    wheelchair = properties.wheelchair,
    accessibilityNote = properties.accessibility_note,
    country = properties.country,
    floorPlanUrl = properties.floor_plan_url,
    longitude = lon(),
    latitude = lat(),
)

fun Feature<PickupPointProperties>.toEntity(): PickupPointEntity = PickupPointEntity(
    id = properties.id,
    kraj = properties.kraj,
    district = properties.district,
    orgName = properties.org_name,
    address = properties.address,
    phone = properties.phone,
    email = properties.email,
    hours = properties.hours,
    note = properties.note,
    precision = properties.precision,
    sourceUrl = properties.source_url,
    longitude = lon(),
    latitude = lat(),
)

// ---- Favourites: full detail snapshot round-trip (DB v8) -----------------------------------
// A favourited place must open with the complete DetailScreen body even when its live Room row
// is gone (feed scoped it out, removed server-side, never synced offline). `add(...)` freezes a
// snapshot here; `DetailViewModel` thaws it back into a WC / pickup entity when the live row is
// absent. Unknown numerics -> 0, unknown strings -> null; `source` is non-null on the WC entity.

fun WcLocationEntity.toFavoriteEntity(): FavoriteEntity = FavoriteEntity(
    placeId = id,
    isPickup = false,
    title = name,
    source = source,
    lastVerified = lastVerified,
    likes = likes,
    dislikes = dislikes,
    longitude = longitude,
    latitude = latitude,
    savedAt = System.currentTimeMillis(),
    description = description,
    note = note,
    photoUrl = photoUrl,
    webUrl = webUrl,
    openingHours = openingHours,
    access = access,
    wheelchair = wheelchair,
    accessibilityNote = accessibilityNote,
    country = country,
    floorPlanUrl = floorPlanUrl,
)

fun PickupPointEntity.toFavoriteEntity(): FavoriteEntity = FavoriteEntity(
    placeId = id,
    isPickup = true,
    title = orgName,
    source = null,
    lastVerified = null,
    likes = 0,
    dislikes = 0,
    longitude = longitude,
    latitude = latitude,
    savedAt = System.currentTimeMillis(),
    note = note,
    address = address,
    phone = phone,
    email = email,
    hours = hours,
    district = district,
    kraj = kraj,
    precision = precision,
    sourceUrl = sourceUrl,
)

fun FavoriteEntity.toWcLocationEntity(): WcLocationEntity = WcLocationEntity(
    id = placeId,
    name = title,
    source = source ?: "",
    description = description,
    note = note,
    photoUrl = photoUrl,
    likes = likes,
    dislikes = dislikes,
    lastVerified = lastVerified,
    webUrl = webUrl,
    openingHours = openingHours,
    access = access,
    wheelchair = wheelchair,
    accessibilityNote = accessibilityNote,
    country = country,
    floorPlanUrl = floorPlanUrl,
    longitude = longitude,
    latitude = latitude,
)

fun FavoriteEntity.toPickupPointEntity(): PickupPointEntity = PickupPointEntity(
    id = placeId,
    kraj = kraj,
    district = district,
    orgName = title,
    address = address,
    phone = phone,
    email = email,
    hours = hours,
    note = note,
    precision = precision,
    sourceUrl = sourceUrl,
    longitude = longitude,
    latitude = latitude,
)
