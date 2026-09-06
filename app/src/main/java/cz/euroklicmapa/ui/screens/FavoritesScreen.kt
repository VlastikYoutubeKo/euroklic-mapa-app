package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.ui.components.EmptyState
import cz.euroklicmapa.ui.components.PlaceCard
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.FavoritesViewModel
import cz.euroklicmapa.ui.viewmodel.FavoritesViewModelFactory
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.formatWalkingTime
import cz.euroklicmapa.util.placesCount

@Composable
fun FavoritesScreen(
    onItemClick: (String, String) -> Unit,
    viewModel: FavoritesViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            FavoritesViewModelFactory(favoritesRepository, locationRepository)
        },
    ),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Oblíbené",
                style = MaterialTheme.typography.displaySmall,
                color = EuroklicTheme.extended.textStrong,
            )
            Text(
                if (items.isEmpty()) "Uložená místa" else "Uloženo: ${placesCount(items.size)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.BookmarkBorder,
                title = "Zatím nic uloženého",
                subtitle = "V detailu místa klepněte na záložku a uloží se sem — funguje i offline.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { "${it.navType}_${it.id}" }) { item ->
                    PlaceCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        distance = item.distanceMeters?.let(::formatDistance),
                        walk = item.distanceMeters?.let(::formatWalkingTime),
                        source = item.source,
                        isPickup = item.isPickup,
                        status = item.status,
                        country = item.country,
                        likes = item.likes ?: 0,
                        dislikes = item.dislikes ?: 0,
                        onClick = { onItemClick(item.id.toString(), item.navType) },
                    )
                }
            }
        }
    }
}
