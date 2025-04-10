package com.tubesmobile.purrytify.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.ui.viewmodel.MusicBehaviorViewModel

@Composable
fun BottomPlayerBar(
    musicBehaviorViewModel: MusicBehaviorViewModel,
    navController: NavController,
    fromScreen: Screen
) {
    val currentSong by musicBehaviorViewModel.currentSong.collectAsState()
    val isPlaying by musicBehaviorViewModel.isPlaying.collectAsState()
    val position by musicBehaviorViewModel.currentPosition.collectAsState()
    val duration by musicBehaviorViewModel.duration.collectAsState()

    val song = currentSong
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    val context = LocalContext.current
    val imageBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    val imageBitmap = imageBitmapState.value

    LaunchedEffect(song?.artworkUri) {
        imageBitmapState.value = null
        val retriever = MediaMetadataRetriever()
        try {
            if (song?.artworkUri == "Metadata") {
                retriever.setDataSource(context, Uri.parse(song.uri))
                val art = retriever.embeddedPicture
                if (art != null) {
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    imageBitmapState.value = bitmap.asImageBitmap()
                }
            } else if (!song?.artworkUri.isNullOrEmpty()) {
                val fileBitmap = BitmapFactory.decodeFile(song?.artworkUri)
                if (fileBitmap != null) {
                    imageBitmapState.value = fileBitmap.asImageBitmap()
                }
            }
        } catch (_: Exception) {
            imageBitmapState.value = null
        } finally {
            retriever.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                navController.navigate("music/${fromScreen.name}")
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground), // fallback
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song?.title ?: "Starboy",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "The Weeknd, Da...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    musicBehaviorViewModel.togglePlayPause()
                }
            ) {
                Icon(
                    painter = painterResource(id = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface
                )
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
}

