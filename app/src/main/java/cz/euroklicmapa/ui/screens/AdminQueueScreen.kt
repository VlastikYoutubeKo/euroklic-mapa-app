package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.data.model.AdminPlace
import cz.euroklicmapa.data.model.CommentSuggestion
import cz.euroklicmapa.data.model.PhotoSuggestion
import cz.euroklicmapa.data.repository.AdminListState
import cz.euroklicmapa.ui.components.EmptyState
import cz.euroklicmapa.ui.viewmodel.AdminQueueViewModel
import cz.euroklicmapa.ui.viewmodel.AdminQueueViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQueueScreen(
    onBack: () -> Unit,
    viewModel: AdminQueueViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            AdminQueueViewModelFactory(adminRepository)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snack.collect { snackHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ke schválení") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::load) { Text("Obnovit") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackHost) },
    ) { innerPadding ->
        when (val s = state) {
            is AdminListState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is AdminListState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::load) { Text("Zkusit znovu") }
                }
            }

            is AdminListState.Loaded -> if (s.pending.isEmpty() && s.photos.isEmpty() && s.comments.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    title = "Fronta je prázdná",
                    subtitle = "Žádné nové návrhy ke schválení.",
                )
            } else {
                val showHeaders = listOf(
                    s.pending.isNotEmpty(),
                    s.photos.isNotEmpty(),
                    s.comments.isNotEmpty(),
                ).count { it } > 1
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (s.pending.isNotEmpty() && showHeaders) item("h_places") { SectionHeader("Nová místa") }
                    items(s.pending, key = { "p_${it.id}" }) { place ->
                        PendingCard(
                            place = place,
                            busy = viewModel.actingId == place.id,
                            enabled = viewModel.actingId == null,
                            onApprove = { viewModel.review(place.id, approve = true) },
                            onReject = { viewModel.review(place.id, approve = false) },
                        )
                    }
                    if (s.photos.isNotEmpty() && showHeaders) item("h_photos") { SectionHeader("Návrhy fotek") }
                    items(s.photos, key = { "f_${it.id}" }) { photo ->
                        PhotoSuggestionCard(
                            photo = photo,
                            busy = viewModel.actingPhotoId == photo.id,
                            enabled = viewModel.actingPhotoId == null,
                            onApprove = { viewModel.reviewPhoto(photo.id, approve = true) },
                            onReject = { viewModel.reviewPhoto(photo.id, approve = false) },
                        )
                    }
                    if (s.comments.isNotEmpty() && showHeaders) item("h_comments") { SectionHeader("Návrhy komentářů") }
                    items(s.comments, key = { "c_${it.id}" }) { c ->
                        CommentSuggestionCard(
                            comment = c,
                            busy = viewModel.actingCommentId == c.id,
                            enabled = viewModel.actingCommentId == null,
                            onApprove = { viewModel.reviewComment(c.id, approve = true) },
                            onReject = { viewModel.reviewComment(c.id, approve = false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun PhotoSuggestionCard(
    photo: PhotoSuggestion,
    busy: Boolean,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!photo.photo_url.isNullOrBlank()) {
                AsyncImage(
                    model = photo.photo_url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(
                photo.location_name?.takeIf { it.isNotBlank() } ?: "Místo #${photo.location_id}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(
                    photo.author_name?.takeIf { it.isNotBlank() },
                    String.format(java.util.Locale.US, "%.5f, %.5f", photo.location_lat, photo.location_lon),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Schválit")
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentSuggestionCard(
    comment: CommentSuggestion,
    busy: Boolean,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    comment.displayAuthor,
                    comment.location_name?.takeIf { it.isNotBlank() } ?: "Místo #${comment.location_id}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Schválit")
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCard(
    place: AdminPlace,
    busy: Boolean,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!place.photo_url.isNullOrBlank()) {
                AsyncImage(
                    model = place.photo_url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(
                place.name.ifBlank { "Bez názvu" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            place.text?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(
                    place.author_name?.takeIf { it.isNotBlank() },
                    place.created_at?.substringBefore(" ")?.takeIf { it.isNotBlank() },
                    String.format(java.util.Locale.US, "%.5f, %.5f", place.lat, place.lon),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(
                    onClick = onApprove,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Schválit")
                    }
                }
            }
        }
    }
}
