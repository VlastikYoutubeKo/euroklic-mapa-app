package cz.euroklicmapa.ui.screens

import cz.euroklicmapa.ui.theme.contrastBorder
import cz.euroklicmapa.ui.theme.EuroklicTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.data.model.AdminPlace
import cz.euroklicmapa.data.model.CommentSuggestion
import cz.euroklicmapa.data.model.LocationReport
import cz.euroklicmapa.data.model.PhotoSuggestion
import cz.euroklicmapa.data.model.ReportReason
import cz.euroklicmapa.data.repository.AdminListState
import cz.euroklicmapa.ui.components.EmptyState
import cz.euroklicmapa.ui.viewmodel.AdminQueueViewModel
import cz.euroklicmapa.ui.viewmodel.AdminQueueViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQueueScreen(
    onBack: () -> Unit,
    onOpenPlace: (Int) -> Unit,
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
                    TextButton(shape = EuroklicTheme.shapes.pill, onClick = viewModel::load) { Text("Obnovit") }
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
                    Button(shape = EuroklicTheme.shapes.pill, onClick = viewModel::load) { Text("Zkusit znovu") }
                }
            }

            is AdminListState.Loaded -> if (s.pending.isEmpty() && s.photos.isEmpty() && s.comments.isEmpty() && s.reports.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    title = "Fronta je prázdná",
                    subtitle = "Žádné nové návrhy ani nahlášené problémy.",
                )
            } else {
                val showHeaders = listOf(
                    s.pending.isNotEmpty(),
                    s.photos.isNotEmpty(),
                    s.comments.isNotEmpty(),
                    s.reports.isNotEmpty(),
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
                    if (s.reports.isNotEmpty() && showHeaders) item("h_reports") { SectionHeader("Nahlášené problémy") }
                    items(s.reports, key = { "r_${it.id}" }) { report ->
                        LocationReportCard(
                            report = report,
                            busy = viewModel.actingReportId == report.id,
                            enabled = viewModel.actingReportId == null,
                            onOpenPlace = { onOpenPlace(report.location_id) },
                            onResolve = { viewModel.reviewReport(report.id, resolve = true) },
                            onDismiss = { viewModel.reviewReport(report.id, resolve = false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationReportCard(
    report: LocationReport,
    busy: Boolean,
    enabled: Boolean,
    onOpenPlace: () -> Unit,
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        border = contrastBorder(),
        color = EuroklicTheme.extended.cardSurface,
        shape = EuroklicTheme.shapes.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val placeLabel = report.location_name?.takeIf { it.isNotBlank() }
                ?: "Místo #${report.location_id}"
            Text(
                placeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                ReportReason.entries.firstOrNull { it.wireValue == report.reason }?.label ?: report.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            report.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOfNotNull(
                    report.author_name?.takeIf { it.isNotBlank() } ?: "Neznámý autor",
                    report.created_at?.substringBefore(" ")?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                shape = EuroklicTheme.shapes.pill,
                onClick = onOpenPlace,
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "Otevřít místo $placeLabel" },
            ) { Text("Otevřít místo") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    shape = EuroklicTheme.shapes.pill,
                    onClick = onDismiss,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(shape = EuroklicTheme.shapes.pill, onClick = onResolve, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp).semantics { contentDescription = "Vyřizuji nahlášení" },
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Vyřešeno")
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
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .semantics { heading() },
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
        border = contrastBorder(),
        color = EuroklicTheme.extended.cardSurface,
        shape = EuroklicTheme.shapes.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val placeLabel = photo.location_name?.takeIf { it.isNotBlank() }
                ?: "Místo #${photo.location_id}"
            if (!photo.photo_url.isNullOrBlank()) {
                AsyncImage(
                    model = photo.photo_url,
                    // Photo under review is content, not decoration.
                    contentDescription = "Navrhovaná fotka pro $placeLabel",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(EuroklicTheme.shapes.small),
                )
            }
            Text(
                placeLabel,
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
                    shape = EuroklicTheme.shapes.pill,
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(shape = EuroklicTheme.shapes.pill, onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = "Schvaluji" },
                        )
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
        border = contrastBorder(),
        color = EuroklicTheme.extended.cardSurface,
        shape = EuroklicTheme.shapes.card,
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
                    shape = EuroklicTheme.shapes.pill,
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(shape = EuroklicTheme.shapes.pill, onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = "Schvaluji" },
                        )
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
        border = contrastBorder(),
        color = EuroklicTheme.extended.cardSurface,
        shape = EuroklicTheme.shapes.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!place.photo_url.isNullOrBlank()) {
                AsyncImage(
                    model = place.photo_url,
                    // Photo attached to a place under review is content, not decoration.
                    contentDescription = place.name.trim().takeIf { it.isNotEmpty() }
                        ?.let { "Fotka navrženého místa $it" }
                        ?: "Fotka navrženého místa",
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(EuroklicTheme.shapes.small),
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
                    shape = EuroklicTheme.shapes.pill,
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Zamítnout")
                }
                Button(
                    shape = EuroklicTheme.shapes.pill,
                    onClick = onApprove,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = "Schvaluji" },
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Schválit")
                    }
                }
            }
        }
    }
}
