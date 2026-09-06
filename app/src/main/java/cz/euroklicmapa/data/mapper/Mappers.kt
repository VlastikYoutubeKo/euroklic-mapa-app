package cz.euroklicmapa.data.mapper

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
