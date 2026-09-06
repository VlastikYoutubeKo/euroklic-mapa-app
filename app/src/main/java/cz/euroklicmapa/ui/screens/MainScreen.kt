package cz.euroklicmapa.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import android.widget.Toast
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.data.auth.AuthEvent
import cz.euroklicmapa.ui.navigation.Destinations

@Composable
fun MainScreen() {
    val backStack = rememberNavBackStack(Destinations.Map)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val app = context.applicationContext as EuroklicApplication
        app.authRepository.events.collect { ev ->
            val msg = when (ev) {
                is AuthEvent.SignedIn ->
                    if (ev.username.isBlank()) "Přihlášeno" else "Přihlášen jako ${ev.username}"
                is AuthEvent.Error -> ev.message
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )

    fun go(target: Destinations) {
        if (backStack.lastOrNull() != target) backStack.add(target)
    }

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            item(
                selected = backStack.lastOrNull() is Destinations.Map,
                onClick = { go(Destinations.Map) },
                icon = { Icon(Icons.Rounded.Map, contentDescription = null) },
                label = { Text("Mapa") },
                colors = itemColors,
            )
            item(
                selected = backStack.lastOrNull() is Destinations.List,
                onClick = { go(Destinations.List) },
                icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null) },
                label = { Text("Seznam") },
                colors = itemColors,
            )
            item(
                selected = backStack.lastOrNull() is Destinations.Favorites,
                onClick = { go(Destinations.Favorites) },
                icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                label = { Text("Oblíbené") },
                colors = itemColors,
            )
            item(
                selected = backStack.lastOrNull() is Destinations.More,
                onClick = { go(Destinations.More) },
                icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
                label = { Text("Více") },
                colors = itemColors,
            )
        },
    ) {
        NavDisplay(
            backStack = backStack,
            // Default decorators don't scope ViewModels per entry; without this every Detail
            // entry would share one DetailViewModel (stale id).
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
        ) { key ->
            when (key) {
                is Destinations.Map -> NavEntry(key) {
                    MapScreen(
                        onMarkerClick = { id, type -> backStack.add(Destinations.Detail(id, type)) },
                        onAddPlace = { backStack.add(Destinations.AddPlace) },
                    )
                }

                is Destinations.List -> NavEntry(key) {
                    ListScreen(onItemClick = { id, type -> backStack.add(Destinations.Detail(id, type)) })
                }

                is Destinations.Favorites -> NavEntry(key) {
                    FavoritesScreen(onItemClick = { id, type -> backStack.add(Destinations.Detail(id, type)) })
                }

                is Destinations.More -> NavEntry(key) {
                    MoreScreen(
                        onAddPlace = { backStack.add(Destinations.AddPlace) },
                        onOpenAdmin = { backStack.add(Destinations.AdminQueue) },
                        onOpenAbout = { backStack.add(Destinations.About) },
                    )
                }

                is Destinations.About -> NavEntry(key) {
                    AboutScreen(
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    )
                }

                is Destinations.AddPlace -> NavEntry(key) {
                    AddPlaceScreen(
                        onDone = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    )
                }

                is Destinations.AdminQueue -> NavEntry(key) {
                    AdminQueueScreen(
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    )
                }

                is Destinations.Detail -> NavEntry(key) {
                    DetailScreen(
                        id = key.id,
                        type = key.type,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    )
                }

                else -> NavEntry(key) { Text("Neznámý cíl") }
            }
        }
    }
}
