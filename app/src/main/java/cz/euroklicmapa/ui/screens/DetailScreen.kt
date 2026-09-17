package cz.euroklicmapa.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.R
import cz.euroklicmapa.data.auth.AuthState
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.model.WcComment
import cz.euroklicmapa.ui.map.MiniMap
import cz.euroklicmapa.ui.components.LoginDialog
import cz.euroklicmapa.ui.components.SourceBadge
import cz.euroklicmapa.ui.theme.originalSourceLabel
import cz.euroklicmapa.ui.components.SourceGlyph
import cz.euroklicmapa.ui.components.StatusBadge
import cz.euroklicmapa.ui.components.rememberImagePicker
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.DetailState
import cz.euroklicmapa.ui.viewmodel.DetailViewModel
import cz.euroklicmapa.ui.viewmodel.DetailViewModelFactory
import cz.euroklicmapa.util.OpenState
import cz.euroklicmapa.util.PlaceStatus
import cz.euroklicmapa.util.countryName
import cz.euroklicmapa.util.distanceBetween
import cz.euroklicmapa.util.isForeignCountry
import cz.euroklicmapa.util.isStaleVerification
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.formatWalkingTime
import cz.euroklicmapa.util.launchNavigation
import cz.euroklicmapa.util.openUrl
import cz.euroklicmapa.util.parseOpeningHours
import cz.euroklicmapa.util.sanitizeStationHours
import cz.euroklicmapa.util.placeStatus
import org.osmdroid.util.GeoPoint
import java.util.Calendar

private const val KEY_STATUS_URL = "https://euroklic.odjezdy.online/clanky/jak-vybavit-euroklic/"

/** OSM imports often dump `[Wheelchair: yes]` into `description`; make it readable. */
private fun prettifyDescription(raw: String): String {
    val m = Regex("""^\[?\s*wheelchair\s*[:=]\s*(\w+)\s*]?$""", RegexOption.IGNORE_CASE)
        .find(raw.trim()) ?: return raw
    return when (m.groupValues[1].lowercase()) {
        "yes" -> "Bezbariérový přístup: ano"
        "limited" -> "Bezbariérový přístup: částečně"
        "no" -> "Bezbariérový přístup: ne"
        else -> raw
    }
}

@Composable
fun DetailScreen(
    id: String,
    type: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            DetailViewModelFactory(
                repository, locationRepository, favoritesRepository, addPlaceRepository,
                id.toIntOrNull() ?: 0, type,
            )
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val voting by viewModel.voting.collectAsStateWithLifecycle()
    val myVote by viewModel.myVote.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val photoUploading by viewModel.photoUploading.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentPosting by viewModel.commentPosting.collectAsStateWithLifecycle()
    val reportSending by viewModel.reportSending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as EuroklicApplication
    val authState by app.authRepository.state.collectAsStateWithLifecycle()
    val loggedIn = authState is AuthState.LoggedIn
    var showLoginDialog by remember { mutableStateOf(false) }
    val imagePicker = rememberImagePicker { uri -> viewModel.uploadPhoto(uri) }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        // Also a Toast: the inline copy under the vote buttons is easy to miss (e.g. after a
        // comment submit the composer is scrolled into view and the vote row is off-screen).
        Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
        kotlinx.coroutines.delay(3500)
        viewModel.consumeMessage()
    }

    val navTarget: GeoPoint? = when (val s = state) {
        is DetailState.WcDetail -> GeoPoint(s.wc.latitude, s.wc.longitude)
        is DetailState.PickupDetail -> GeoPoint(s.pp.latitude, s.pp.longitude)
        else -> null
    }
    val navLabel = when (val s = state) {
        is DetailState.WcDetail -> s.wc.name
        is DetailState.PickupDetail -> s.pp.orgName
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (val s = state) {
                is DetailState.Loading -> Box(
                    Modifier.fillMaxSize().padding(top = 120.dp),
                    contentAlignment = Alignment.TopCenter,
                ) { CircularProgressIndicator() }

                is DetailState.Error -> {
                    Hero(photoUrl = null, isPickup = type != "WC", name = null)
                    Text(
                        "Toto místo se nepodařilo načíst.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DetailState.WcDetail -> WcBody(
                    wc = s.wc,
                    userLocation = userLocation,
                    voting = voting,
                    myVote = myVote,
                    message = message,
                    onVote = viewModel::vote,
                    photoUploading = photoUploading,
                    comments = comments,
                    onAddPhoto = {
                        if (loggedIn) imagePicker.request() else showLoginDialog = true
                    },
                    loggedIn = loggedIn,
                    commentPosting = commentPosting,
                    onSubmitComment = viewModel::postComment,
                    onRequestLogin = { showLoginDialog = true },
                    reportSending = reportSending,
                    onReportProblem = viewModel::reportProblem,
                )
                is DetailState.PickupDetail -> PickupBody(s.pp, userLocation)
            }
            Spacer(Modifier.height(96.dp)) // room for the sticky bar
        }

        // Back button floating over the hero
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
            }
        }

        if (state is DetailState.WcDetail || state is DetailState.PickupDetail) {
            // Share the recipient-usable web page (`/lokace/{id}-{slug}`), never the
            // `euroklicmapa://` deep link — that's useless to anyone without the app.
            val shareText = when (val s = state) {
                is DetailState.WcDetail ->
                    s.wc.webUrl?.takeIf { it.isNotBlank() }?.let { "${s.wc.name}\n$it" }
                        ?: "${s.wc.name}\nhttps://euroklic.odjezdy.online/"
                is DetailState.PickupDetail -> "${s.pp.orgName}\nhttps://euroklic.odjezdy.online/"
                else -> "https://euroklic.odjezdy.online/"
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = {
                        try {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, shareText)
                            context.startActivity(Intent.createChooser(send, null))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Nelze sdílet.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shadowElevation = 3.dp,
                    // 48dp touch target (an outer .size() clamps Surface's built-in
                    // minimumInteractiveComponentSize, so it has to be 48 here).
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Share, contentDescription = "Sdílet místo")
                    }
                }
                Surface(
                    onClick = viewModel::toggleFavorite,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isFavorite) "Odebrat z oblíbených" else "Přidat do oblíbených",
                        )
                    }
                }
            }
        }

        if (navTarget != null) {
            NavigateBar(
                onClick = { launchNavigation(context, navTarget.latitude, navTarget.longitude, navLabel) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onContinue = { openUrl(context, app.authRepository.buildLoginUri().toString()) },
        )
    }
}

@Composable
private fun Hero(photoUrl: String?, isPickup: Boolean, name: String?, onPhotoClick: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxWidth().height(if (photoUrl.isNullOrBlank()) 168.dp else 240.dp)) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name?.let { "Fotografie: $it" },
                placeholder = painterResource(R.drawable.placeholder),
                error = painterResource(R.drawable.placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { m ->
                        if (onPhotoClick == null) {
                            m
                        } else {
                            m.clickable(onClickLabel = "Otevřít na celou obrazovku", onClick = onPhotoClick)
                                .semantics { role = Role.Button }
                        }
                    },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPickup) Icons.Rounded.Directions else Icons.Rounded.Wc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        // top scrim so the white back button always has contrast
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent)),
                ),
        )
    }
}

/** Fullscreen photo viewer (pinch-zoom, double-tap) opened from [Hero]. Shows who submitted the
 *  photo when the backend sends it (`photo_author`) — absent on older/un-migrated rows. */
@Composable
private fun PhotoViewerDialog(url: String, author: String?, contentDescription: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(model = url, contentDescription = contentDescription)
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Zavřít")
                }
            }
            if (!author.isNullOrBlank()) {
                Text(
                    "Foto: $author",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Pinch-to-zoom (1x-5x) + pan; double-tap toggles between 1x and 2x. [model] is anything Coil
 *  accepts — a remote URL (photos) or a local file (floor plan SVG cache). */
@Composable
private fun ZoomableImage(model: Any?, contentDescription: String?) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + panChange
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        placeholder = painterResource(R.drawable.placeholder),
        error = painterResource(R.drawable.placeholder),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            },
    )
}

@Composable
private fun WcBody(
    wc: WcLocationEntity,
    userLocation: GeoPoint?,
    voting: Boolean,
    myVote: Boolean?,
    message: String?,
    onVote: (Boolean) -> Unit,
    photoUploading: Boolean,
    comments: List<WcComment>?,
    onAddPhoto: () -> Unit,
    loggedIn: Boolean,
    commentPosting: Boolean,
    onSubmitComment: (String) -> Unit,
    onRequestLogin: () -> Unit,
    reportSending: Boolean,
    onReportProblem: (cz.euroklicmapa.data.model.ReportReason, String?, (Boolean) -> Unit) -> Unit,
) {
    var showPhotoViewer by remember { mutableStateOf(false) }
    val hasPhoto = !wc.photoUrl.isNullOrBlank()
    Hero(
        photoUrl = wc.photoUrl,
        isPickup = false,
        name = wc.name,
        onPhotoClick = if (hasPhoto) ({ showPhotoViewer = true }) else null,
    )
    if (showPhotoViewer && hasPhoto) {
        PhotoViewerDialog(
            url = wc.photoUrl!!,
            author = wc.photoAuthor,
            contentDescription = "Fotografie: ${wc.name}",
            onDismiss = { showPhotoViewer = false },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(wc.name, style = MaterialTheme.typography.headlineMedium, color = EuroklicTheme.extended.textStrong)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceBadge(source = wc.source, isPickup = false)
            StatusBadge(likes = wc.likes, dislikes = wc.dislikes, isCd = wc.source == "cd")
            if (isForeignCountry(wc.country)) ForeignBadge(wc.country)
        }

        // A15 — moved up next to the status badge it summarizes: the old bottom-of-screen spot
        // (after photo/map/hours/description/comments) buried the one action this app is for.
        VoteRow(
            likes = wc.likes,
            dislikes = wc.dislikes,
            myVote = myVote,
            voting = voting,
            message = message,
            onVote = onVote,
        )
        // A11 — you're literally standing at a place that's flagged as broken: nudge a re-check.
        val reported = remember(wc.source, wc.likes, wc.dislikes, wc.lastVerified) {
            placeStatus(wc.source, wc.likes, wc.dislikes, wc.lastVerified) == PlaceStatus.REPORTED
        }
        val atThisPlace = userLocation?.let {
            distanceBetween(GeoPoint(wc.latitude, wc.longitude), it) <= 75.0
        } ?: false
        if (reported && atThisPlace) {
            VerifyPresenceCard(voting = voting, onVote = onVote)
        }

        AccessCard(requiresKey = wc.access == "eurokey", wheelchair = wc.wheelchair)
        AddPhotoRow(hasPhoto = hasPhoto, uploading = photoUploading, onClick = onAddPhoto)
        DistanceCard(GeoPoint(wc.latitude, wc.longitude), userLocation)
        MiniMap(
            lat = wc.latitude,
            lon = wc.longitude,
            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)),
        )
        OpeningHoursSection(hall = wc.openingHours, wc = wc.wcOpeningHours)
        wc.description?.takeIf { it.isNotBlank() }?.let {
            Section("Popis", prettifyDescription(it))
        }
        wc.note?.takeIf { it.isNotBlank() }?.let { Section("Poznámka", it) }
        wc.accessibilityNote?.takeIf { it.isNotBlank() }?.let { ExpandableSection("Přístupnost stanice", it) }
        wc.floorPlanUrl?.takeIf { it.isNotBlank() }?.let { FloorPlanSection(wc.id, it) }
        Section("Původní zdroj", originalSourceLabel(wc.source))
        wc.lastVerified?.let {
            Section("Naposledy ověřeno", it.substringBefore(" "))
            // STALE — the place once got confirmed but it's since aged out of "recently
            // verified"; say so explicitly instead of silently reading as never-checked.
            if (isStaleVerification(it)) {
                Text(
                    "Ověření je starší — informace mohou být neaktuální.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EuroklicTheme.extended.warning,
                )
            }
        }

        CommentsSection(
            comments = comments,
            loggedIn = loggedIn,
            posting = commentPosting,
            onSubmit = onSubmitComment,
            onRequestLogin = onRequestLogin,
        )

        ReportProblemRow(
            loggedIn = loggedIn,
            sending = reportSending,
            onSubmit = onReportProblem,
            onRequestLogin = onRequestLogin,
        )
        ReportLink(wc.webUrl)
    }
}

@Composable
private fun AddPhotoRow(hasPhoto: Boolean, uploading: Boolean, onClick: () -> Unit) {
    val label = if (hasPhoto) "Navrhnout jinou fotku" else "Přidat fotku"
    Surface(
        onClick = { if (!uploading) onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        // One focus stop announced as a button; the two Texts below carry the label.
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uploading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(20.dp)
                        .semantics { contentDescription = "Nahrávám fotku" },
                )
            } else {
                Icon(
                    Icons.Rounded.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Projde schválením moderátora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ForeignBadge(country: String?) {
    Text(
        countryName(country),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Merges the old separate KeyRequiredCard + WheelchairCard into one box — two stacked
 *  full-width cards for two one-line facts read as clutter; one card, two lines doesn't. */
@Composable
private fun AccessCard(requiresKey: Boolean, wheelchair: String?) {
    val wheelchairLine = when (wheelchair) {
        "yes" -> "Bezbariérový přístup do budovy: ano" to false
        "no" -> "Bezbariérový přístup do budovy: ne" to true
        else -> null
    }
    if (!requiresKey && wheelchairLine == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (requiresKey) {
                Text(
                    "Zamčeno – pro vstup je nutný Euroklíč.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            wheelchairLine?.let { (text, warn) ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (warn) MaterialTheme.colorScheme.error else EuroklicTheme.extended.success,
                )
            }
        }
    }
}

@Composable
private fun ExpandableSection(label: String, value: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(if (expanded) "Zobrazit méně" else "Zobrazit vše")
        }
    }
}

/**
 * Shows the station floor plan as a tappable (fullscreen, pinch-zoom) image, not just a link out.
 * `cd.cz/planek/{id}` itself is an HTML page with the plan buried as base64 SVG inside inline JS
 * — backend's `api_floorplan.php?id=<location id>` already does that scrape server-side and
 * serves clean `image/svg+xml` (7-day HTTP cache), so this is a plain AsyncImage, same as any
 * photo. [sourceUrl] (cd.cz) stays as a small secondary link for the original page.
 */
@Composable
private fun FloorPlanSection(locationId: Int, sourceUrl: String) {
    var showViewer by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val imageUrl = "https://euroklic.odjezdy.online/api_floorplan.php?id=$locationId"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "ORIENTAČNÍ PLÁNEK STANICE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AsyncImage(
            model = imageUrl,
            contentDescription = "Orientační plánek stanice — otevřít na celou obrazovku",
            placeholder = painterResource(R.drawable.placeholder),
            error = painterResource(R.drawable.placeholder),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White) // plans are white-background line art in both app themes
                .clickable(onClickLabel = "Otevřít na celou obrazovku", onClick = { showViewer = true })
                .semantics { role = Role.Button },
        )
        TextButton(
            onClick = { uriHandler.openUri(sourceUrl) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("Zobrazit na cd.cz", textDecoration = TextDecoration.Underline)
        }
    }

    if (showViewer) {
        PhotoViewerDialog(
            url = imageUrl,
            author = null,
            contentDescription = "Orientační plánek stanice",
            onDismiss = { showViewer = false },
        )
    }
}

@Composable
private fun ReportLink(webUrl: String?) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(webUrl ?: "https://euroklic.odjezdy.online/") },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        // "2.0" spec §37/29: split into two distinct actions — the structured in-app dialog
        // (ReportProblemRow, above) is now "Nahlásit problém"; this stays as the escape hatch
        // for edits the dialog doesn't cover (wrong name, merge duplicates, etc.).
        Text("Navrhnout úpravu na webu", textDecoration = TextDecoration.Underline)
    }
}

/**
 * "2.0" spec §23/29 — structured problem report, replacing the old "open the web" link for the
 * common case. Login-gated like comments/photos (the endpoint requires Bearer, unlike anonymous
 * voting) since a report opens a moderation-queue item, not a public vote.
 */
@Composable
private fun ReportProblemRow(
    loggedIn: Boolean,
    sending: Boolean,
    onSubmit: (cz.euroklicmapa.data.model.ReportReason, String?, (Boolean) -> Unit) -> Unit,
    onRequestLogin: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (loggedIn) showDialog = true else onRequestLogin() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Nahlásit problém",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showDialog) {
        ReportProblemDialog(
            sending = sending,
            onDismiss = { showDialog = false },
            onSubmit = { reason, note ->
                onSubmit(reason, note) { success -> if (success) showDialog = false }
            },
        )
    }
}

@Composable
private fun ReportProblemDialog(
    sending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (cz.euroklicmapa.data.model.ReportReason, String?) -> Unit,
) {
    var reason by remember { mutableStateOf<cz.euroklicmapa.data.model.ReportReason?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Co je špatně?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                cz.euroklicmapa.data.model.ReportReason.entries.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = reason == r,
                                role = Role.RadioButton,
                                enabled = !sending,
                                onClick = { reason = r },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null, enabled = !sending)
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 1000) note = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Poznámka (nepovinné)") },
                    minLines = 2,
                    enabled = !sending,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason != null && !sending,
                onClick = { reason?.let { onSubmit(it, note) } },
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp).semantics { contentDescription = "Odesílám hlášení" },
                    )
                } else {
                    Text("Odeslat")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) { Text("Zrušit") }
        },
    )
}

@Composable
private fun PickupBody(pp: PickupPointEntity, userLocation: GeoPoint?) {
    val uriHandler = LocalUriHandler.current
    Hero(photoUrl = null, isPickup = true, name = pp.orgName)
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(pp.orgName, style = MaterialTheme.typography.headlineMedium, color = EuroklicTheme.extended.textStrong)
        SourceBadge(source = null, isPickup = true)
        DistanceCard(GeoPoint(pp.latitude, pp.longitude), userLocation)
        MiniMap(
            lat = pp.latitude,
            lon = pp.longitude,
            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)),
        )

        if (pp.precision == "approx") {
            NoticeCard("Přesná adresa není známá – poloha je jen přibližná (obec). Ověřte si ji předem.")
        }

        Section("Adresa", pp.address)
        Section("Okres / kraj", listOfNotNull(pp.district, pp.kraj).joinToString(", ").ifBlank { null })
        Section("Telefon", pp.phone)
        Section("E-mail", pp.email)
        Section("Otevírací doba", pp.hours)

        NoticeCard(
            "NRZP ČR od července 2026 dočasně pozastavila výdej nových Euroklíčů kvůli " +
                "chybějícímu financování. Než se vydáte, ověřte si aktuální stav.",
        )
        TextButton(
            onClick = { uriHandler.openUri(KEY_STATUS_URL) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("Aktuální stav výdeje klíčů", textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
// Neutral, not primary-filled — "2.0" spec's point stands: primary is reserved for the one CTA
// (Navigovat), not spent on a metadata card. Distance still reads as the loudest number on the
// screen through type size/weight alone (headlineSmall/Bold), not through a colored box.
private fun DistanceCard(target: GeoPoint, userLocation: GeoPoint?) {
    val meters = userLocation?.let { distanceBetween(target, it) } ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                formatDistance(meters),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = EuroklicTheme.extended.textStrong,
            )
            Text(
                "${formatWalkingTime(meters)} pěšky\nvzdušnou čarou, přibližně",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Community notes — mirrors the website's list (web renders the same in the map popup). Read
 * is approved-only; the composer at the bottom posts through the moderation queue, so a fresh
 * comment won't show here immediately. Logged-out users get a "log in" button instead.
 */
@Composable
private fun CommentsSection(
    comments: List<WcComment>?,
    loggedIn: Boolean,
    posting: Boolean,
    onSubmit: (String) -> Unit,
    onRequestLogin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Komentáře",
            style = MaterialTheme.typography.titleMedium,
            color = EuroklicTheme.extended.textStrong,
        )
        val list = comments.orEmpty()
        if (list.isEmpty()) {
            Text(
                "Zatím žádné komentáře.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        list.forEach { c ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            c.author,
                            style = MaterialTheme.typography.labelLarge,
                            color = EuroklicTheme.extended.textStrong,
                        )
                        c.created_at?.let {
                            Text(
                                formatDate(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        c.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        CommentComposer(
            loggedIn = loggedIn,
            posting = posting,
            onSubmit = onSubmit,
            onRequestLogin = onRequestLogin,
        )
    }
}

@Composable
private fun CommentComposer(
    loggedIn: Boolean,
    posting: Boolean,
    onSubmit: (String) -> Unit,
    onRequestLogin: () -> Unit,
) {
    if (!loggedIn) {
        OutlinedButton(
            onClick = onRequestLogin,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Přihlásit se a přidat komentář")
        }
        return
    }
    var text by rememberSaveable { mutableStateOf("") }
    val trimmedLen = text.trim().length
    val lenError = text.isNotEmpty() && trimmedLen !in 3..2000
    val canSend = trimmedLen in 3..2000 && !posting
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("Přidat komentář") },
            isError = lenError,
            enabled = !posting,
            // Counter as supportingText → TalkBack reads it as part of the field, and
            // Material colours it with the error state automatically.
            supportingText = {
                Text("$trimmedLen/2000", modifier = Modifier.fillMaxWidth())
            },
        )
        Button(
            onClick = {
                onSubmit(text)
                text = ""
            },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (posting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(18.dp)
                        .semantics { contentDescription = "Odesílám komentář" },
                )
            } else {
                Text("Odeslat")
            }
        }
    }
}

/** `"2026-08-28 04:31:45"` → `"28. 8. 2026"` (same date-only display as the web). */
private fun formatDate(raw: String): String {
    val (y, m, d) = raw.substringBefore(" ").split('-')
    val yv = y.toIntOrNull() ?: return raw.substringBefore(" ")
    val mv = m.toIntOrNull() ?: return raw.substringBefore(" ")
    val dv = d.toIntOrNull() ?: return raw.substringBefore(" ")
    return "$dv. $mv. $yv"
}

@Composable
private fun Section(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * ČD stations only. [hall] is the station-hall opening hours (`opening_hours` — since 2026-09-09
 * primarily scraped per-day from Správa železnic, cd.cz as fallback); [wc] is WC-specific hours
 * (`wc_opening_hours`, still cd.cz-sourced, ~6 stations, usually narrower). We show the WC hours
 * when present, otherwise the hall hours, and a small "Otevřeno" / "Zavřeno" chip when
 * [parseOpeningHours] is confident. [sanitizeStationHours] strips the "UPOZORNĚNÍ: Mimořádná
 * změna…" tails a few cd.cz-sourced rows still carry.
 */
@Composable
private fun OpeningHoursSection(hall: String?, wc: String?) {
    val cleanWc = sanitizeStationHours(wc)
    val cleanHall = sanitizeStationHours(hall)
    val showWc = cleanWc != null
    val body = (cleanWc ?: cleanHall) ?: return
    val label = if (showWc) "OTEVÍRACÍ DOBA WC" else "OTEVÍRACÍ DOBA STANICE"

    val state = remember(body) {
        parseOpeningHours(body)?.statusAt(Calendar.getInstance()) ?: OpenState.UNKNOWN
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state != OpenState.UNKNOWN) {
                val open = state == OpenState.OPEN
                Text(
                    if (open) "Otevřeno" else "Zavřeno",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (open) EuroklicTheme.extended.onSuccess else MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (open) EuroklicTheme.extended.success else MaterialTheme.colorScheme.error,
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        // Bare "Otevřeno" has no context for TalkBack — spell out it's a status.
                        .semantics {
                            contentDescription = if (open) "Stav: otevřeno" else "Stav: zavřeno"
                        },
                )
            }
        }
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Zdroj: ČD",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A11 — shown only on the WC detail when the place is REPORTED *and* the user is within ~75 m.
 * Points at the same `onVote` the [VoteRow] below uses; the row stays too (this is a nudge).
 */
@Composable
private fun VerifyPresenceCard(voting: Boolean, onVote: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Jste na místě? Ověřte, jestli je WC funkční.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CompactVoteButton(
                    text = "Funguje",
                    tint = EuroklicTheme.extended.success,
                    enabled = !voting,
                    onClick = { onVote(true) },
                    contentDescription = "Ověřit, že WC funguje",
                    modifier = Modifier.weight(1f),
                )
                CompactVoteButton(
                    text = "Nefunguje",
                    tint = MaterialTheme.colorScheme.error,
                    enabled = !voting,
                    onClick = { onVote(false) },
                    contentDescription = "Nahlásit, že WC nefunguje",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactVoteButton(
    text: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        modifier = modifier
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun VoteRow(
    likes: Int,
    dislikes: Int,
    myVote: Boolean?,
    voting: Boolean,
    message: String?,
    onVote: (Boolean) -> Unit,
) {
    val ok = EuroklicTheme.extended.success
    val err = MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "OVĚŘENÍ KOMUNITOU",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VoteButton(
                text = "Funguje",
                count = likes,
                tint = ok,
                icon = Icons.Rounded.ThumbUpOffAlt,
                iconSelected = Icons.Rounded.ThumbUp,
                selected = myVote == true,
                enabled = !voting,
                onClick = { onVote(true) },
                modifier = Modifier.weight(1f),
            )
            VoteButton(
                text = "Nefunguje",
                count = dislikes,
                tint = err,
                icon = Icons.Rounded.ThumbDownOffAlt,
                iconSelected = Icons.Rounded.ThumbDown,
                selected = myVote == false,
                enabled = !voting,
                onClick = { onVote(false) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            message ?: "Hlasování je anonymní. Změní stav místa i pro ostatní.",
            style = MaterialTheme.typography.bodySmall,
            color = if (message != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoteButton(
    text: String,
    count: Int,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Neutral (outlined, no tint) until it means something — my vote, or someone already
    // voted. A permanently red-tinted "Nefunguje · 0" read as a standing warning on places
    // nobody has actually reported, which is what made this look unfinished/off.
    val active = selected || count > 0
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = when {
            selected -> tint
            active -> tint.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            selected -> Color.White
            active -> tint
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (selected) iconSelected else icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                "$text · $count",
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceGlyph(source = "user", isPickup = false, size = 10, modifier = Modifier.padding(top = 5.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun NavigateBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EuroklicTheme.extended.brandButton,
                    contentColor = EuroklicTheme.extended.onBrandButton,
                ),
            ) {
                Icon(Icons.Rounded.Directions, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Navigovat", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
