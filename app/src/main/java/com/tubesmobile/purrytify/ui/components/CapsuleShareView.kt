package com.tubesmobile.purrytify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tubesmobile.purrytify.R
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CapsuleShareView(
    capsuleData: MonthlySoundCapsuleData,
    mainImageUrl: String?
) {
    val context = LocalContext.current
    val imageWidth = 360.dp
    val imageHeight = 640.dp

    Box(
        modifier = Modifier
            .width(imageWidth)
            .height(imageHeight)
            .background(Color(0xFF121212))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(mainImageUrl ?: R.drawable.ic_default_album)
                .crossfade(true)
                .build(),
            contentDescription = "Capsule Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 0f,
                        endY = imageHeight.value * LocalContext.current.resources.displayMetrics.density
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Purrify Logo",
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Purrify",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date()),
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "My ${capsuleData.monthYear} Sound Capsule",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 32.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Top artists",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    capsuleData.topArtistsList?.take(5)?.forEach { artist ->
                        Text(
                            text = "${artist.rank}. ${artist.name}",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1
                        )
                    } ?: Text("N/A", fontSize = 13.sp, color = Color.LightGray)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Top songs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    capsuleData.topSongsList?.take(5)?.forEach { song ->
                        Text(
                            text = "${song.rank}. ${song.title}",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1
                        )
                    } ?: Text("N/A", fontSize = 13.sp, color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Time Listened
            Column {
                Text(
                    text = "Time listened",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${capsuleData.timeListenedMinutes ?: 0} minutes",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}