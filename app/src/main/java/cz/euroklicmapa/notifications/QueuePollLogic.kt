package cz.euroklicmapa.notifications

/**
 * Pure, Android-free bits of [QueuePollWorker] — Czech pluralisation for the notification
 * text plus the admin "should we notify" predicate. Extracted so they're unit-testable
 * without a live [cz.euroklicmapa.EuroklicApplication]. No behaviour change: the worker
 * delegates here verbatim.
 */
internal object QueuePollLogic {

    /** Rough Czech plural for "místo". */
    internal fun places(n: Int) = when {
        n == 1 -> "1 místo"
        n in 2..4 -> "$n místa"
        else -> "$n míst"
    }

    internal fun photos(n: Int) = when {
        n == 1 -> "1 fotka"
        n in 2..4 -> "$n fotky"
        else -> "$n fotek"
    }

    /** "1 nové místo v okolí" / "3 nová místa v okolí" / "7 nových míst v okolí". */
    internal fun newPlacesNearby(n: Int) = when {
        n == 1 -> "1 nové místo v okolí"
        n in 2..4 -> "$n nová místa v okolí"
        else -> "$n nových míst v okolí"
    }

    /**
     * Notify about the admin queue only when there is something pending **and** either count
     * moved since the last poll. `lastPlaceCount` / `lastPhotoCount` are `-1` on the very first
     * poll ([cz.euroklicmapa.data.prefs.NotificationPrefs] sentinel), so real counts always
     * count as "changed".
     */
    internal fun adminShouldNotify(
        placeCount: Int,
        photoCount: Int,
        lastPlaceCount: Int,
        lastPhotoCount: Int,
    ): Boolean =
        (placeCount + photoCount) > 0 &&
            (placeCount != lastPlaceCount || photoCount != lastPhotoCount)
}
