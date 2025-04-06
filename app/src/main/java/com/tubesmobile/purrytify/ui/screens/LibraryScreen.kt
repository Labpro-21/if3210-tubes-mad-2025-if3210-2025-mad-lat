package com.tubesmobile.purrytify.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.components.SharedBottomNavigationBar
import com.tubesmobile.purrytify.ui.components.Screen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.tubesmobile.purrytify.ui.viewmodel.MusicViewModel

// SAMPLE DATA
val songs = listOf(
    Song("STARBOY", "The Weeknd, Daft Punk", R.drawable.ic_launcher_foreground),
    Song("Here Comes The Sun - Remaster...", "The Beatles", R.drawable.ic_launcher_foreground),
    Song("MIDNIGHT PRETENDERS", "Tomoko Aran", R.drawable.ic_launcher_foreground),
    Song("VIOLENT CRIMES", "Kanye West", R.drawable.ic_launcher_foreground),
    Song("DENIAL IS A RIVER", "Doechii", R.drawable.ic_launcher_foreground),
    Song("Doomsday", "MF DOOM, Pebbles The Invisible Girl", R.drawable.ic_launcher_foreground)
)

@Composable
fun MusicLibraryScreen(navController: NavHostController, musicViewModel: MusicViewModel) {
    val currentScreen = remember { mutableStateOf(Screen.LIBRARY) }
    var showPopup by remember { mutableStateOf(false) }
    var songsList by remember { mutableStateOf(songs) }


    Scaffold(
        bottomBar = {
            SharedBottomNavigationBar(
                currentScreen = currentScreen.value,
                onNavigate = { screen ->
                    currentScreen.value = screen
                    when (screen) {
                        Screen.HOME -> navController.navigate("home")
                        Screen.LIBRARY -> {}
                        Screen.PROFILE -> navController.navigate("profile")
                        Screen.MUSIC -> {}
                    }
                },
                transparent = false
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showPopup = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabButton(text = "All", isSelected = true)
                TabButton(text = "Liked", isSelected = false)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(songsList) { song ->
                    SongItem(
                        song = song,
                        onClick = { selectedSong ->
                            musicViewModel.playSong(selectedSong)
                            navController.navigate("music/${Screen.LIBRARY.name}")
                        })
                }
            }
        }
        if (showPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showPopup = false
                    }
            )

            SwipeableUpload(
                onDismiss = { showPopup = false },
                onAddSong = { newSong ->
                    songsList = songsList + newSong
                }
            )
        }
    }
}

@Composable
fun SwipeableUpload(onDismiss: () -> Unit, onAddSong: (Song) -> Unit) {

    fun shortenFilename(name: String, maxLength: Int = 20): String {
        return if (name.length <= maxLength) name
        else name.take(maxLength - 10) + "..." + name.takeLast(7)
    }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var selectedArtwork by remember { mutableStateOf<ImageBitmap?>(null) }
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val titleFocusRequester = remember { FocusRequester() }
    val artistFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current

    var pickedFileName by remember { mutableStateOf<String?>(null) }

    val launcherAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            audioUri = it
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst()) {
                    val fileName = c.getString(nameIndex)
                    pickedFileName = shortenFilename(fileName)
                }
            }
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, it)

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""

            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            duration = dur?.let { d -> "${d / 1000 / 60}:${(d / 1000 % 60).toString().padStart(2, '0')}" } ?: ""

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
            val stream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            selectedArtwork = bitmap?.asImageBitmap()
        }
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onDismiss()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(500.dp)
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val newOffset = offsetY.value + delta
                                if (newOffset >= 0f) {
                                    offsetY.snapTo(newOffset)
                                }
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
                        .padding(8.dp)

                    ,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimary,
                                RoundedCornerShape(2.dp)
                            )
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch {
                                        val newOffset = offsetY.value + delta
                                        if (newOffset >= 0f) {
                                            offsetY.snapTo(newOffset)
                                        }
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
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Upload Song",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        UploadBox(
                            label = "Upload Artwork",
                            image = selectedArtwork,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                launcherArtwork.launch("image/*")
                            }
                        )
                        UploadBox(
                            label = pickedFileName ?: "Upload File",
                            image = null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                launcherAudio.launch("audio/*")
                            }
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
                        keyboardActions = KeyboardActions(
                            onNext = {
                                artistFocusRequester.requestFocus()
                            }
                        )
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
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButtonUpload("Cancel", color = MaterialTheme.colorScheme.secondary) { onDismiss() }
                        ActionButtonUpload("Save", color = MaterialTheme.colorScheme.primary) {
                            if (title.isNotBlank() && artist.isNotBlank() && audioUri != null) {
                                val newSong = Song(
                                    title = title,
                                    artist = artist,
                                    imageRes = R.drawable.ic_launcher_foreground, // placeholder
//                                    uri = audioUri.toString(), // assuming you've added a uri field in your Song model
//                                    artworkUri = artworkUri?.toString(),
//                                    duration = duration
                                )
                                onAddSong(newSong)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun UploadBox(label: String, image: ImageBitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(120.dp)
            .width(120.dp)
            .border(1.dp, MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
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


@Composable
fun TabButton(text: String, isSelected: Boolean) {
    Button(
        onClick = { /* Handle tab click */ },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .height(36.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SongItem(song: Song, onClick: (Song) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = song.imageRes),
            contentDescription = song.title,
            modifier = Modifier
                .size(56.dp)
        )

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MusicLibraryScreenPreview() {
    val navController = rememberNavController()
    val previewViewModel: MusicViewModel = viewModel()

    PurrytifyTheme {
        MusicLibraryScreen(navController, previewViewModel)
    }
}