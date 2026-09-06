package cz.euroklicmapa.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.data.auth.AuthState
import cz.euroklicmapa.ui.components.DataFreshnessBanner
import cz.euroklicmapa.ui.components.LoginDialog
import cz.euroklicmapa.ui.components.PlaceCard
import cz.euroklicmapa.ui.components.SourceBadge
import cz.euroklicmapa.ui.components.StatusBadge
import cz.euroklicmapa.ui.map.EuroklicMap
import cz.euroklicmapa.ui.map.MapMarker
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.MapViewModel
import cz.euroklicmapa.ui.viewmodel.MapViewModelFactory
import cz.euroklicmapa.ui.viewmodel.NearestResult
import cz.euroklicmapa.ui.viewmodel.PlaceCategory
import cz.euroklicmapa.ui.viewmodel.PlaceListItem
import cz.euroklicmapa.util.distanceBetween
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.formatWalkingTime
import cz.euroklicmapa.util.launchNavigation
import cz.euroklicmapa.util.openUrl
import cz.euroklicmapa.util.placesCount
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onMarkerClick: (String, String) -> Unit,
    onAddPlace: () -> Unit = {},
    viewModel: MapViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            MapViewModelFactory(repository, locationRepository, geocodingRepository)
        },
    ),
) {
    val context = LocalContext.current
    val app = context.applicationContext as EuroklicApplication
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val authState by app.authRepository.state.collectAsStateWithLifecycle()
    val loggedIn = authState is AuthState.LoggedIn
    var showLoginDialog by remember { mutableStateOf(false) }

    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val nearby by viewModel.nearbyPlaces.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val recenterTarget by viewModel.recenterTarget.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val dataSync by viewModel.dataSync.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val shortcutPending by app.pendingNearestShortcut.collectAsStateWithLifecycle()
    val nearestResult by viewModel.nearestResult.collectAsStateWithLifecycle()

    var selectedMarker by remember { mutableStateOf<MapMarker?>(null) }
    var showRationale by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // Ask for fine location (better distance accuracy), but a coarse-only grant still counts —
    // the system may hand back either depending on what the user picks in its own dialog.
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )
    val hasLocationPermission = locationPermissions.permissions.any { it.status.isGranted }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) viewModel.locateUser()
    }

    // "Nejbližší WC" quick action result (from the map FAB or the launcher shortcut).
    // StateFlow-consume pattern (same as recenterTarget) so the result can never be dropped
    // mid-recomposition the way a replay-less SharedFlow emit could.
    LaunchedEffect(nearestResult) {
        when (val result = nearestResult) {
            is NearestResult.Found -> {
                selectedMarker = result.marker
                scope.launch { sheetState.partialExpand() }
                viewModel.onNearestResultHandled()
            }
            NearestResult.NoLocation -> {
                Toast.makeText(
                    context,
                    "Poloha není známá. Povolte polohu a zkuste to znovu.",
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.onNearestResultHandled()
            }
            NearestResult.NoPlaces -> {
                Toast.makeText(
                    context,
                    "Zatím nejsou stažená žádná místa.",
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.onNearestResultHandled()
            }
            null -> {}
        }
    }

    // Consumed-once flag set by the static launcher shortcut (res/xml/shortcuts.xml).
    LaunchedEffect(shortcutPending, hasLocationPermission) {
        if (shortcutPending) {
            app.consumeNearestShortcut()
            if (hasLocationPermission) viewModel.findNearest() else showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Přístup k poloze") },
            text = {
                Text(
                    "Polohu použijeme jen k seřazení míst podle vzdálenosti a k vycentrování " +
                        "mapy na vás. Nikam mimo appku ji neposíláme.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    locationPermissions.launchMultiplePermissionRequest()
                }) { Text("Povolit polohu") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Teď ne") } },
        )
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        // The selected-place card is taller than a couple of list rows — give it enough peek
        // that "Navigovat" and "Zobrazit podrobnosti" are both visible without dragging.
        sheetPeekHeight = if (selectedMarker != null) 340.dp else 232.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            val selected = selectedMarker
            if (selected != null) {
                SelectedPlaceCard(
                    marker = selected,
                    userLocation = userLocation,
                    context = context,
                    onOpenDetail = {
                        val type = if (selected.isPickup) "PICKUP" else "WC"
                        selectedMarker = null
                        onMarkerClick(selected.id.substringAfter("_"), type)
                    },
                    onBackToList = { selectedMarker = null },
                )
            } else {
                NearbySheetList(
                    places = nearby,
                    hasLocation = userLocation != null,
                    onOpen = { item -> onMarkerClick(item.id.toString(), item.navType) },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            EuroklicMap(
                modifier = Modifier.fillMaxSize(),
                markers = markers,
                userLocation = userLocation,
                recenterTarget = recenterTarget,
                selectedMarkerId = selectedMarker?.id,
                onRecenterHandled = viewModel::onRecenterHandled,
                onMarkerClick = {
                    selectedMarker = it
                    scope.launch { sheetState.partialExpand() }
                },
                onMapClick = { selectedMarker = null },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchField(
                    value = query,
                    onValueChange = viewModel::onSearchQueryChange,
                    onSearch = {
                        keyboard?.hide()
                        viewModel.submitSearch()
                    },
                    onClear = { viewModel.onSearchQueryChange("") },
                    loading = isSearching,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryChipRow(category = category, onSelect = viewModel::setCategory)
                searchError?.let { PillNotice(it) }
                DataFreshnessBanner(dataSync)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Secondary — same "Přidat místo" action as More, just reachable without
                // leaving the map. Tonal, so it doesn't compete with the primary FAB below.
                SmallFloatingActionButton(
                    onClick = { if (loggedIn) onAddPlace() else showLoginDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Přidat místo")
                }
                // The app's one question, one tap away — mirrors the website's
                // "Najít nejbližší WC (GPS)". Refreshes the fix, recentres on the nearest
                // place and selects it. The old separate "recenter on me" FAB is folded in:
                // selecting the nearest place always brings the camera back to your area,
                // and the you-are-here dot stays visible on the map anyway.
                FloatingActionButton(
                    onClick = {
                        if (hasLocationPermission) viewModel.findNearest() else showRationale = true
                    },
                    containerColor = EuroklicTheme.extended.brandButton,
                    contentColor = EuroklicTheme.extended.onBrandButton,
                ) {
                    Icon(Icons.Rounded.NearMe, contentDescription = "Najít nejbližší WC")
                }
            }
        }
    }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onContinue = { openUrl(context, app.authRepository.buildLoginUri().toString()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("Hledat adresu nebo místo") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    value.isNotEmpty() -> IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.Close, contentDescription = "Vymazat")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryChipRow(category: PlaceCategory, onSelect: (PlaceCategory) -> Unit) {
    // Deliberately NOT wrapped in its own pill Surface: sharing SearchField's exact shape,
    // color and elevation made the two read as one merged blob. Each chip now floats on its
    // own — a light border for the unselected ones keeps them legible over the map tiles,
    // and the selected chip uses the same brandButton blue as the FAB/Navigate CTA elsewhere,
    // instead of a pale primaryContainer that barely showed which filter was active.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MapFilterChip("Vše", category == PlaceCategory.ALL) { onSelect(PlaceCategory.ALL) }
        MapFilterChip("Toalety", category == PlaceCategory.TOILET) { onSelect(PlaceCategory.TOILET) }
        MapFilterChip("Výdejní místa", category == PlaceCategory.PICKUP) { onSelect(PlaceCategory.PICKUP) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(999.dp),
        elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = EuroklicTheme.extended.brandButton,
            selectedLabelColor = EuroklicTheme.extended.onBrandButton,
            selectedLeadingIconColor = EuroklicTheme.extended.onBrandButton,
        ),
    )
}

@Composable
private fun PillNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** Peek/expanded content when nothing is selected: the distance-sorted list of nearby places. */
@Composable
private fun NearbySheetList(
    places: List<PlaceListItem>,
    hasLocation: Boolean,
    onOpen: (PlaceListItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = placesCount(places.size) + if (hasLocation) " v okolí" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
        }
        items(places, key = { "${it.navType}_${it.id}" }) { item ->
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
                onClick = { onOpen(item) },
            )
        }
        if (places.isEmpty()) {
            item {
                Text(
                    "Zatím tu nic není. Zkuste to znovu, až budete online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

/** Peek content when a marker is tapped: the one place, its state, and the primary action. */
@Composable
private fun SelectedPlaceCard(
    marker: MapMarker,
    userLocation: GeoPoint?,
    context: Context,
    onOpenDetail: () -> Unit,
    onBackToList: () -> Unit,
) {
    val meters = userLocation?.let { distanceBetween(marker.position, it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 12.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                marker.title,
                style = MaterialTheme.typography.headlineSmall,
                color = EuroklicTheme.extended.textStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            )
            IconButton(onClick = onBackToList) {
                Icon(Icons.Rounded.Close, contentDescription = "Zpět na seznam")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceBadge(source = marker.source, isPickup = marker.isPickup)
            StatusBadge(likes = marker.likes, dislikes = marker.dislikes, isCd = marker.source == "cd")
        }
        if (meters != null) {
            Text(
                "${formatDistance(meters)} · ${formatWalkingTime(meters)} pěšky (vzdušnou čarou)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(2.dp))
        Button(
            onClick = {
                launchNavigation(context, marker.position.latitude, marker.position.longitude, marker.title)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EuroklicTheme.extended.brandButton,
                contentColor = EuroklicTheme.extended.onBrandButton,
            ),
        ) {
            Icon(Icons.Rounded.Directions, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Navigovat")
        }
        TextButton(
            onClick = onOpenDetail,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Zobrazit podrobnosti") }
    }
}
