package cz.euroklicmapa.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.ui.components.DataFreshnessBanner
import cz.euroklicmapa.ui.components.EmptyState
import cz.euroklicmapa.ui.components.LoadingState
import cz.euroklicmapa.ui.components.PlaceCard
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.PlaceCategory
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.formatWalkingTime
import cz.euroklicmapa.util.placesCount

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ListScreen(
    onItemClick: (String, String) -> Unit,
    viewModel: cz.euroklicmapa.ui.viewmodel.ListViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            cz.euroklicmapa.ui.viewmodel.ListViewModelFactory(repository, locationRepository)
        },
    ),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val dataSync by viewModel.dataSync.collectAsStateWithLifecycle()
    val hasLocation by viewModel.hasLocation.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )
    val hasLocationPermission = locationPermissions.permissions.any { it.status.isGranted }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) viewModel.refreshLocation()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Nejbližší místa",
                style = MaterialTheme.typography.displaySmall,
                color = EuroklicTheme.extended.textStrong,
            )
            Text(
                text = placesCount(items.size) + if (hasLocation) " · seřazeno podle vzdálenosti"
                else " · řazeno abecedně",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DataFreshnessBanner(dataSync)
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip("Vše", category == PlaceCategory.ALL) { viewModel.setCategory(PlaceCategory.ALL) }
                CategoryChip("Toalety", category == PlaceCategory.TOILET) { viewModel.setCategory(PlaceCategory.TOILET) }
                CategoryChip("Výdejní místa", category == PlaceCategory.PICKUP) { viewModel.setCategory(PlaceCategory.PICKUP) }
            }
            if (!hasLocation && !hasLocationPermission && items.isNotEmpty()) {
                LocationHint(onEnable = { locationPermissions.launchMultiplePermissionRequest() })
            }
        }

        when {
            items.isEmpty() && dataSync == null ->
                LoadingState("Načítám místa…")

            items.isEmpty() && category != PlaceCategory.ALL ->
                EmptyState(
                    icon = Icons.Rounded.WrongLocation,
                    title = "V této kategorii nic není",
                    subtitle = "Zkuste přepnout filtr zpět na Vše.",
                )

            items.isEmpty() ->
                EmptyState(
                    icon = Icons.Rounded.WrongLocation,
                    title = "Zatím tu nic není",
                    subtitle = "Zkuste to znovu, až budete online.",
                )

            else -> LazyColumn(
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

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun LocationHint(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Zapněte polohu",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Uvidíte, které WC je opravdu nejblíž.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(
                onClick = onEnable,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) { Text("Povolit polohu") }
        }
    }
}
