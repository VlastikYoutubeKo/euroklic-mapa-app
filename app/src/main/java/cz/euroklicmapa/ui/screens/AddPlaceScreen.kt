package cz.euroklicmapa.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.ui.components.rememberImagePicker
import cz.euroklicmapa.ui.map.LocationPickerMap
import cz.euroklicmapa.ui.viewmodel.AddPlaceViewModel
import cz.euroklicmapa.ui.viewmodel.AddPlaceViewModelFactory
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceScreen(
    onDone: () -> Unit,
    viewModel: AddPlaceViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            AddPlaceViewModelFactory(addPlaceRepository, locationRepository)
        },
    ),
) {
    val context = LocalContext.current
    val imagePicker = rememberImagePicker { uri -> viewModel.photoUri = uri }
    // "2.0" spec's multi-step Review — this form is short enough (3 fields + map + photo) that
    // a full step-by-step wizard would just add taps without helping; one confirm-before-submit
    // dialog gets the same "catch a mistake before it's queued" value without the restructure.
    var showReview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.submitted.collect {
            android.widget.Toast
                .makeText(context, "Místo odesláno ke schválení.", android.widget.Toast.LENGTH_LONG)
                .show()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Přidat místo") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Přidáváte bezbariérovou toaletu s Euroklíčem. Než se objeví v mapě, projde " +
                    "schválením moderátora.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Název místa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("Popis (nepovinné)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Poloha", style = MaterialTheme.typography.titleSmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                LocationPickerMap(
                    initial = viewModel.initialCenter,
                    onCenterChange = viewModel::onCenterChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "Posuňte mapu tak, aby značka byla na místě. " +
                    String.format(Locale.US, "%.5f, %.5f", viewModel.location.latitude, viewModel.location.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Fotka (nepovinné)", style = MaterialTheme.typography.titleSmall)
            PhotoField(
                uri = viewModel.photoUri,
                onPick = { imagePicker.request() },
                onClear = { viewModel.photoUri = null },
            )

            viewModel.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { showReview = true },
                enabled = viewModel.canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (viewModel.submitting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Zkontrolovat a odeslat")
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }

    if (showReview) {
        ReviewDialog(
            name = viewModel.name,
            description = viewModel.description,
            latitude = viewModel.location.latitude,
            longitude = viewModel.location.longitude,
            photoUri = viewModel.photoUri,
            onDismiss = { showReview = false },
            onConfirm = {
                showReview = false
                viewModel.submit()
            },
        )
    }
}

/** "2.0" spec Review step, as one dialog instead of a separate screen — see the comment at
 *  [AddPlaceScreen]'s `showReview` for why a full wizard doesn't fit this form. */
@Composable
private fun ReviewDialog(
    name: String,
    description: String,
    latitude: Double,
    longitude: Double,
    photoUri: Uri?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zkontrolujte místo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Vybraná fotka",
                        modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    String.format(Locale.US, "%.5f, %.5f", latitude, longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Po odeslání projde místo schválením moderátora, než se objeví v mapě.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Odeslat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zpět a upravit") } },
    )
}

@Composable
private fun PhotoField(uri: Uri?, onPick: () -> Unit, onClear: () -> Unit) {
    if (uri == null) {
        Surface(
            onClick = onPick,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Přidat fotku",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))) {
            AsyncImage(
                model = uri,
                contentDescription = "Vybraná fotka",
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                onClick = onClear,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Odebrat fotku",
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

