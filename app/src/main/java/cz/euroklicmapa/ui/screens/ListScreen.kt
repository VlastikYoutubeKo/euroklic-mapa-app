package cz.euroklicmapa.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.ui.components.DataFreshnessBanner
import cz.euroklicmapa.ui.components.EmptyState
import cz.euroklicmapa.ui.components.PlaceCard
import cz.euroklicmapa.ui.components.PlaceListSkeleton
import cz.euroklicmapa.ui.components.brandChipColors
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.PlaceCategory
import cz.euroklicmapa.ui.viewmodel.PlaceFilters
import cz.euroklicmapa.ui.viewmodel.StatusFilter
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
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    var showFilterDialog by remember { mutableStateOf(false) }

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
                // "Filtry" sits outside the scroll area (Codex review, 2026-09-24) — it was the
                // trailing chip in a plain horizontalScroll Row with no scroll-position memory or
                // "there's more" affordance, so on first render it could sit at/past the screen
                // edge with no hint it was reachable. Only the category chips scroll now; the
                // filter action itself is always fully visible.
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip("Vše", category == PlaceCategory.ALL) { viewModel.setCategory(PlaceCategory.ALL) }
                    CategoryChip("Toalety", category == PlaceCategory.TOILET) { viewModel.setCategory(PlaceCategory.TOILET) }
                    CategoryChip("Výdejní místa", category == PlaceCategory.PICKUP) { viewModel.setCategory(PlaceCategory.PICKUP) }
                }
                FilterChip(
                    selected = filters.activeCount > 0,
                    onClick = { showFilterDialog = true },
                    label = { Text(if (filters.activeCount > 0) "Filtry · ${filters.activeCount}" else "Filtry") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    colors = brandChipColors(),
                )
            }
            if (!hasLocation && !hasLocationPermission && items.isNotEmpty()) {
                LocationHint(onEnable = { locationPermissions.launchMultiplePermissionRequest() })
            }
        }

        // Room's Flow emits an empty list immediately on subscribe, before the network refresh
        // resolves — so "items.isEmpty() && dataSync == null" alone can't tell "still loading"
        // from "loaded and failed, offline, no cache". A generous timeout (real device is
        // usually seconds; the documented worst case is ~40s on a slow link) breaks the
        // otherwise-permanent spinner into an actionable retry state.
        var loadTimedOut by remember { mutableStateOf(false) }
        LaunchedEffect(items.isEmpty(), dataSync) {
            loadTimedOut = false
            if (items.isEmpty() && dataSync == null) {
                delay(20_000)
                loadTimedOut = true
            }
        }

        when {
            items.isEmpty() && dataSync == null && !loadTimedOut ->
                PlaceListSkeleton()

            items.isEmpty() && dataSync == null && loadTimedOut ->
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Nepodařilo se načíst data",
                    subtitle = "Zkontrolujte připojení k internetu.",
                    action = { Button(onClick = viewModel::retry) { Text("Zkusit znovu") } },
                )

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
                    action = { Button(onClick = viewModel::retry) { Text("Zkusit znovu") } },
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

    if (showFilterDialog) {
        FilterDialog(
            initial = filters,
            onDismiss = { showFilterDialog = false },
            onApply = { viewModel.setFilters(it); showFilterDialog = false },
        )
    }
}

/**
 * "2.0" spec §24/28 — status + distance filters, using data already on [PlaceListItem]
 * (no backend needed). Temp state inside the dialog per spec: "Zrušit" discards, "Použít" commits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(initial: PlaceFilters, onDismiss: () -> Unit, onApply: (PlaceFilters) -> Unit) {
    var statuses by remember { mutableStateOf(initial.statuses) }
    var distance by remember { mutableStateOf(initial.maxDistanceMeters) }

    fun toggle(s: StatusFilter) {
        statuses = if (s in statuses) statuses - s else statuses + s
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "STAV",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = brandChipColors()
                    FilterChip(StatusFilter.VERIFIED in statuses, { toggle(StatusFilter.VERIFIED) }, label = { Text("Ověřená místa") }, colors = colors)
                    FilterChip(StatusFilter.UNVERIFIED in statuses, { toggle(StatusFilter.UNVERIFIED) }, label = { Text("Bez ověření") }, colors = colors)
                    FilterChip(StatusFilter.REPORTED in statuses, { toggle(StatusFilter.REPORTED) }, label = { Text("Nahlášený problém") }, colors = colors)
                }

                Text(
                    "VZDÁLENOST",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = brandChipColors()
                    FilterChip(distance == 500.0, { distance = 500.0 }, label = { Text("500 m") }, colors = colors)
                    FilterChip(distance == 1000.0, { distance = 1000.0 }, label = { Text("1 km") }, colors = colors)
                    FilterChip(distance == 5000.0, { distance = 5000.0 }, label = { Text("5 km") }, colors = colors)
                    FilterChip(distance == null, { distance = null }, label = { Text("Bez omezení") }, colors = colors)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(PlaceFilters(statuses, distance)) }) { Text("Použít") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        },
    )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = brandChipColors(),
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
