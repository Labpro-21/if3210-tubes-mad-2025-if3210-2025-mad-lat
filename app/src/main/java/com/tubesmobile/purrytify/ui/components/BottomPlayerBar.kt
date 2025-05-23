package com.tubesmobile.purrytify.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.service.MusicPlaybackService
import com.tubesmobile.purrytify.util.generateQRCode
import com.tubesmobile.purrytify.util.saveBitmapToCache
import com.tubesmobile.purrytify.viewmodel.MusicDbViewModel

@Composable
fun BottomPlayerBar(
    musicService: MusicPlaybackService,
    musicDbViewModel: MusicDbViewModel,
    navController: NavController,
    fromScreen: Screen,
    isFromApiSong: Boolean = false
) {
    val currentSong by musicService.currentSong.collectAsState()
    val isPlaying by musicService.isPlaying.collectAsState()
    val position by musicService.currentPosition.collectAsState()
    val duration by musicService.duration.collectAsState()
    val song = currentSong
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    val context = LocalContext.current
    val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    val imageBitmap = imageBitmapState.value
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(song?.artworkUri, isFromApiSong) {
        imageBitmapState.value = null
        if (song?.artworkUri?.isNotEmpty() == true && song.artworkUri != "Metadata") {
            if (isFromApiSong || song.artworkUri.startsWith("http")) {
                imageBitmapState.value = null
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                    if (fileBitmap != null) {
                        imageBitmapState.value = fileBitmap.asImageBitmap()
                    }
                } catch (e: Exception) {
                    imageBitmapState.value = null
                } finally {
                    retriever.release()
                }
            }
        } else if (song?.artworkUri == "Metadata" && !isFromApiSong) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(song.uri))
                val art = retriever.embeddedPicture
                if (art != null) {
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    imageBitmapState.value = bitmap.asImageBitmap()
                }
            } catch (e: Exception) {
                imageBitmapState.value = null
            } finally {
                retriever.release()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                navController.navigate("music/${fromScreen.name}/$isFromApiSong/-1")
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isFromApiSong || song?.artworkUri?.startsWith("http") == true -> {
                    AsyncImage(
                        model = song?.artworkUri,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                        error = painterResource(id = R.drawable.ic_launcher_foreground)
                    )
                }
                imageBitmap != null -> {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song?.title ?: "",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Text(
                    text = song?.artist ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFromApiSong) {
                    IconButton(
                        onClick = { showShareDialog = true }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = {
                        musicService.togglePlayPause(musicDbViewModel)
                    }
                ) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }

    if (showShareDialog) {
        ShareDialog(
            songId = song?.id,
            context = context,
            onDismiss = { showShareDialog = false }
        )
    }
}

@Composable
fun ShareDialog(
    songId: Int?,
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Song") },
        text = {
            Column {
                Text(
                    text = "Share as URL",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songId?.let { id ->
                                val shareUrl = "purrytify://song/$id"
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Song URL"))
                            }
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    fontSize = 16.sp
                )
                Text(
                    text = "Share as QR Code",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songId?.let { id ->
                                val shareUrl = "purrytify://song/$id"
                                val qrBitmap = generateQRCode(shareUrl, 512, 512)
                                if (qrBitmap != null) {
                                    val uri = saveBitmapToCache(context, qrBitmap, "song_qr_$id.png")
                                    if (uri != null) {
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            type = "image/png"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Song QR Code"))
                                    }
                                }
                                onDismiss()
                            }
                        }
                        .padding(vertical = 8.dp),
                    fontSize = 16.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}