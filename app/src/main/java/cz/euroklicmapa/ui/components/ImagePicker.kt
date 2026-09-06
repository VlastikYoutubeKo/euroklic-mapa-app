package cz.euroklicmapa.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Handle returned by [rememberImagePicker]; call [request] to show the camera/gallery dialog. */
class ImagePickerHandle(val request: () -> Unit)

/**
 * Shared "take a photo or pick one from the gallery" flow. Calling this composable emits the
 * source-choice dialog (only visible once [ImagePickerHandle.request] is called) and wires both
 * launchers; the resulting [Uri] is delivered to [onPicked]. Camera capture writes to a
 * FileProvider Uri under `cache/captures/`, so no `CAMERA` permission is needed.
 *
 * Used by `AddPlaceScreen` (new place) and `DetailScreen` (photo for an existing place).
 */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): ImagePickerHandle {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingCameraUri?.let(onPicked)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Přidat fotku") },
            text = { Text("Vyfoťte místo, nebo vyberte fotku z galerie.") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    val uri = newCameraUri(context)
                    pendingCameraUri = uri
                    takePicture.launch(uri)
                }) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Vyfotit")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDialog = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Galerie")
                }
            },
        )
    }

    return remember { ImagePickerHandle(request = { showDialog = true }) }
}

private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
