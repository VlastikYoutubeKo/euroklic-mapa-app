package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import cz.euroklicmapa.util.PlaceStatus
import cz.euroklicmapa.util.countryName
import cz.euroklicmapa.util.distanceBetween
import cz.euroklicmapa.util.isForeignCountry
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.formatWalkingTime
import cz.euroklicmapa.util.launchNavigation
import cz.euroklicmapa.util.openUrl
import cz.euroklicmapa.util.placeStatus
import org.osmdroid.util.GeoPoint

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
    val context = LocalContext.current
    val app = context.applicationContext as EuroklicApplication
    val authState by app.authRepository.state.collectAsStateWithLifecycle()
    val loggedIn = authState is AuthState.LoggedIn
    var showLoginDialog by remember { mutableStateOf(false) }
    val imagePicker = rememberImagePicker { uri -> viewModel.uploadPhoto(uri) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3500)
            viewModel.consumeMessage()
        }
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
                .size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
            }
        }

        if (state is DetailState.WcDetail || state is DetailState.PickupDetail) {
            Surface(
                onClick = viewModel::toggleFavorite,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isFavorite) "Odebrat z oblíbených" else "Přidat do oblíbených",
                    )
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
private fun Hero(photoUrl: String?, isPickup: Boolean, name: String?) {
    Box(modifier = Modifier.fillMaxWidth().height(if (photoUrl.isNullOrBlank()) 168.dp else 240.dp)) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name?.let { "Fotografie: $it" },
                placeholder = painterResource(R.drawable.placeholder),
                error = painterResource(R.drawable.placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
) {
    Hero(photoUrl = wc.photoUrl, isPickup = false, name = wc.name)
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
        if (wc.access == "eurokey") {
            KeyRequiredCard()
        }
        WheelchairCard(wc.wheelchair)
        DistanceCard(GeoPoint(wc.latitude, wc.longitude), userLocation)
        MiniMap(
            lat = wc.latitude,
            lon = wc.longitude,
            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)),
        )
        OpeningHoursSection(wc.openingHours)
        wc.description?.takeIf { it.isNotBlank() }?.let {
            Section("Popis", prettifyDescription(it))
        }
        wc.note?.takeIf { it.isNotBlank() }?.let { Section("Poznámka", it) }
        wc.accessibilityNote?.takeIf { it.isNotBlank() }?.let { ExpandableSection("Přístupnost stanice", it) }
        wc.floorPlanUrl?.takeIf { it.isNotBlank() }?.let { FloorPlanLink(it) }
        Section("Původní zdroj", originalSourceLabel(wc.source))
        wc.lastVerified?.let { Section("Naposledy ověřeno", it.substringBefore(" ")) }

        CommentsSection(
            comments = comments,
            loggedIn = loggedIn,
            posting = commentPosting,
            onSubmit = onSubmitComment,
            onRequestLogin = onRequestLogin,
        )

        AddPhotoRow(
            hasPhoto = !wc.photoUrl.isNullOrBlank(),
            uploading = photoUploading,
            onClick = onAddPhoto,
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

        VoteRow(
            likes = wc.likes,
            dislikes = wc.dislikes,
            myVote = myVote,
            voting = voting,
            message = message,
            onVote = onVote,
        )
        ReportLink(wc.webUrl)
    }
}

@Composable
private fun AddPhotoRow(hasPhoto: Boolean, uploading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (!uploading) onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uploading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    Icons.Rounded.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (hasPhoto) "Navrhnout jinou fotku" else "Přidat fotku",
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

@Composable
private fun WheelchairCard(wheelchair: String?) {
    val (text, warn) = when (wheelchair) {
        "yes" -> "Bezbariérový přístup do budovy: ano" to false
        "no" -> "Bezbariérový přístup do budovy: ne" to true
        else -> return
    }
    Surface(
        color = if (warn) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else EuroklicTheme.extended.success.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (warn) MaterialTheme.colorScheme.error else EuroklicTheme.extended.success,
        )
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

@Composable
private fun KeyRequiredCard() {
    Surface(
        color = EuroklicTheme.extended.brandButton.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Zamčeno – pro vstup je nutný Euroklíč.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FloorPlanLink(url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(url) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text("Orientační plánek stanice", textDecoration = TextDecoration.Underline)
    }
}

@Composable
private fun ReportLink(webUrl: String?) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(webUrl ?: "https://euroklic.odjezdy.online/") },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text("Nahlásit problém nebo upravit na webu", textDecoration = TextDecoration.Underline)
    }
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
private fun DistanceCard(target: GeoPoint, userLocation: GeoPoint?) {
    val meters = userLocation?.let { distanceBetween(target, it) } ?: return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
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
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "${formatWalkingTime(meters)} pěšky\nvzdušnou čarou, přibližně",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
        )
        Text(
            "$trimmedLen/2000",
            style = MaterialTheme.typography.labelSmall,
            color = if (lenError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
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
                        .semantics { contentDescription = "Odesílání komentáře" },
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
 * ČD stations only. The backend's `opening_hours` is the **ticket-counter** schedule
 * ("Vnitrostátní pokladní přepážka"), which closes for 2–3 h gaps midday — it is NOT the
 * station-hall / WC availability. So we show the raw text under an honest label and a caveat,
 * and deliberately do NOT derive an "Otevřeno / Zavřeno" state from it (a wrong "Zavřeno"
 * during a counter gap would steer someone away from a usable toilet). `util/OpeningHours.kt`
 * stays in the tree for if/when the feed carries real hall hours — see BACKLOG A6.
 */
@Composable
private fun OpeningHoursSection(raw: String?) {
    if (raw.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "PROVOZNÍ DOBA POKLADNY (ČD)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(raw, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Doba pokladny, ne WC. Nádražní hala i WC bývají přístupné i mimo tyto hodiny.",
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
                selected = myVote == true,
                enabled = !voting,
                onClick = { onVote(true) },
                modifier = Modifier.weight(1f),
            )
            VoteButton(
                text = "Nefunguje",
                count = dislikes,
                tint = err,
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
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) tint else tint.copy(alpha = 0.12f),
        contentColor = if (selected) Color.White else tint,
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
