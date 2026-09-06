package cz.euroklicmapa.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Destinations : NavKey {
    @Serializable
    data object Map : Destinations()

    @Serializable
    data object List : Destinations()

    @Serializable
    data object Favorites : Destinations()

    @Serializable
    data object More : Destinations()

    @Serializable
    data object AddPlace : Destinations()

    @Serializable
    data object AdminQueue : Destinations()

    @Serializable
    data object About : Destinations()

    /** [type] is `"WC"` or `"PICKUP"`. */
    @Serializable
    data class Detail(val id: String, val type: String) : Destinations()
}
