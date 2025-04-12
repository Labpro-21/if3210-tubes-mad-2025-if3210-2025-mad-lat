package com.tubesmobile.purrytify.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tubesmobile.purrytify.ui.screens.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.toString


@Composable
fun SwipeableUpload(
    onDismiss: () -> Unit,
    existingSong: Song? = null,
    onAddSong: (Song, onExists: () -> Unit) -> Unit = { _, _ -> },
    onEditSong: (Song, String, String, Uri?, () -> Unit, (String) -> Unit) -> Unit
) {
    fun shortenFilename(name: String, maxLength: Int = 20): String {
        return if (name.length <= maxLength) name
        else name.take(maxLength - 10) + "..." + name.takeLast(7)
    }
    var isArtworkChanged by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var title by remember(existingSong?.title) { mutableStateOf(existingSong?.title ?: "") }
    var artist by remember(existingSong?.artist) { mutableStateOf(existingSong?.artist ?: "") }
    var duration by remember { mutableStateOf(existingSong?.duration ?: 0L) }
    var audioUri by remember { mutableStateOf<Uri?>(if (existingSong != null) Uri.parse(existingSong.uri) else null) }
    var artworkUri by remember { mutableStateOf<Uri?>(if (existingSong != null && existingSong.artworkUri.isNotEmpty()) Uri.parse(existingSong.artworkUri) else null) }
    var selectedArtwork by remember { mutableStateOf<ImageBitmap?>(null) }
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val titleFocusRequester = remember { FocusRequester() }
    val artistFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    var pickedFileName by remember { mutableStateOf<String?>(if (existingSong != null) shortenFilename(Uri.parse(existingSong.uri).lastPathSegment ?: "Audio File") else null) }

    LaunchedEffect(existingSong) {
        if (existingSong != null && existingSong.artworkUri.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val file = File(existingSong.artworkUri)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    withContext(Dispatchers.Main) {
                        selectedArtwork = bitmap?.asImageBitmap()
                    }
                }
            }
        }
    }

    val launcherAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            audioUri = it
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst()) {
                    pickedFileName = shortenFilename(c.getString(nameIndex))
                }
            }
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, it)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val art = retriever.embeddedPicture
            art?.let { byteArray ->
                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                selectedArtwork = bitmap.asImageBitmap()
            }
            retriever.release()
        }
    }

    val launcherArtwork = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            artworkUri = it
            val stream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            selectedArtwork = bitmap?.asImageBitmap()
            isArtworkChanged = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(500.dp)
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val newOffset = offsetY.value + delta
                                if (newOffset >= 0f) offsetY.snapTo(newOffset)
                            }
                        },
                        onDragStopped = {
                            scope.launch {
                                if (offsetY.value > screenHeightPx * 0.3f) {
                                    offsetY.animateTo(screenHeightPx, tween(300))
                                    onDismiss()
                                } else {
                                    offsetY.animateTo(0f, tween(300))
                                }
                            }
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (existingSong != null) "Edit Song" else "Upload Song",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Audio Upload Box (disabled in edit mode)
                        UploadBox(
                            label = pickedFileName ?: "Upload File",
                            image = null,
                            modifier = Modifier.weight(1f),
                            onClick = { launcherAudio.launch(arrayOf("audio/*")) },
                            enabled = existingSong == null
                        )

                        UploadBox(
                            label = "Change Artwork",
                            image = selectedArtwork,
                            modifier = Modifier.weight(1f),
                            onClick = { launcherArtwork.launch("image/*") },
                            enabled = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Title",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { artistFocusRequester.requestFocus() })
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Artist",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(artistFocusRequester),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButtonUpload("Cancel", color = MaterialTheme.colorScheme.secondary) { onDismiss() }
                        ActionButtonUpload("Save", color = MaterialTheme.colorScheme.primary) {
                            if (title.isNotBlank() && artist.isNotBlank()) {
                                if (existingSong != null) {
                                    // Edit mode
                                    onEditSong(
                                        existingSong,
                                        title,
                                        artist,
                                        if (isArtworkChanged) artworkUri else null,
                                        { onDismiss() },
                                        { message ->
                                            showErrorDialog = true
                                            errorMessage = message
                                        }
                                    )
                                } else if (audioUri != null) {
                                    // Upload mode
                                    val artworkSource = if (selectedArtwork != null && artworkUri == null) "Metadata comerciais = null" else artworkUri?.toString() ?: ""
                                    val newSong = Song(
                                        title = title,
                                        artist = artist,
                                        duration = duration,
                                        uri = audioUri.toString(),
                                        artworkUri = artworkSource
                                    )
                                    onAddSong(newSong) {
                                        showErrorDialog = true
                                        errorMessage = "Song with the same title and artist already exists."
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("OK") } },
            title = { Text(if (existingSong != null) "Edit Failed" else "Upload Failed") },
            text = { Text(errorMessage) }
        )
    }
}

@Composable
fun UploadBox(label: String, image: ImageBitmap?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(120.dp)
            .width(120.dp)
            .border(1.dp, MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { if (enabled) onClick() }
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = label,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(text = label)
        }
    }
}
@Composable
fun ActionButtonUpload(label: String, modifier: Modifier = Modifier, color: Color, onClick: () -> Unit){
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .width(150.dp)
        ,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

    }
}