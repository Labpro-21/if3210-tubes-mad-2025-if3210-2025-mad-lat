package com.tubesmobile.purrytify.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.ui.theme.PurrytifyTheme
import com.tubesmobile.purrytify.ui.viewmodel.DailyChartData
import com.tubesmobile.purrytify.ui.viewmodel.SoundCapsuleViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeListenedScreen(navController: NavHostController, soundCapsuleViewModel: SoundCapsuleViewModel = viewModel()) {
    var capsuleData by remember { mutableStateOf<MonthlySoundCapsuleData?>(null) }
    val dailyChartData by soundCapsuleViewModel.dailyChartData.collectAsState()
    val isLoadingChart by soundCapsuleViewModel.isLoadingDailyChart.collectAsState()

    LaunchedEffect(Unit) {
        capsuleData = DataKeeper.currentSelectedCapsule
        capsuleData?.let {
            soundCapsuleViewModel.loadDailyChartDataForCapsule(it)
        }
    }

    LaunchedEffect(capsuleData) {
        capsuleData?.let {
            soundCapsuleViewModel.loadDailyChartDataForCapsule(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time listened") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        capsuleData?.let { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = data.monthYear,
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "You listened to music for",
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Text(
                    text = "${data.timeListenedMinutes ?: 0} minutes",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    // color = Color(0xFF1DB954),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "this month.",
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "Daily average: ${data.dailyAverageMinutes ?: 0} min",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    "Listening Activity This Month",
                    fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoadingChart) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                    }
                } else if (dailyChartData.isEmpty() && data.timeListenedMinutes != null && data.timeListenedMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No daily listening data found for this month.", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                } else if (dailyChartData.isNotEmpty()) {
                    DailyBarChart(
                        data = dailyChartData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                            .padding(top = 24.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No listening activity recorded this month.", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No data available for this month.", color = Color.White)
            }
        }
    }
}

@Composable
fun DailyBarChart(
    data: List<DailyChartData>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF1DB954),
    axisColor: Color = Color.Gray,
    labelTextColor: Color = Color.LightGray
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data to display in chart", color = labelTextColor)
        }
        return
    }

    val density = LocalDensity.current
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = labelTextColor.hashCode()
            textSize = density.run { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val maxMinutes = data.maxOfOrNull { it.minutesListened } ?: 1
    val yAxisLabelWidth = density.run { 30.dp.toPx() }
    val xAxisLabelHeight = density.run { 20.dp.toPx() }

    Canvas(modifier = modifier) {
        val chartAreaWidth = size.width - yAxisLabelWidth
        val chartAreaHeight = size.height - xAxisLabelHeight
        val barWidthRatio = 0.7f
        val totalBarSpaces = data.size
        val barSpacing = chartAreaWidth / totalBarSpaces
        val actualBarWidth = barSpacing * barWidthRatio
        val gapWidth = barSpacing * (1 - barWidthRatio) / 2

        val numYLabels = 5
        for (i in 0..numYLabels) {
            val value = maxMinutes * (i.toFloat() / numYLabels)
            val yPos = chartAreaHeight - (value / maxMinutes * chartAreaHeight)
            // Garis Grid
            drawLine(
                color = axisColor.copy(alpha = 0.3f),
                start = Offset(yAxisLabelWidth, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 1f
            )
            // Label Y
            drawContext.canvas.nativeCanvas.drawText(
                value.roundToInt().toString(),
                yAxisLabelWidth - density.run { 4.dp.toPx() },
                yPos + textPaint.textSize / 3,
                textPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
            )
        }


        data.forEachIndexed { index, itemData ->
            val barHeight = (itemData.minutesListened.toFloat() / maxMinutes) * chartAreaHeight
            val left = yAxisLabelWidth + index * barSpacing + gapWidth
            val top = chartAreaHeight - barHeight

            drawRect(
                color = barColor,
                topLeft = Offset(left, top.coerceAtLeast(0f)),
                size = Size(actualBarWidth, barHeight.coerceAtLeast(0f))
            )

            if (data.size <= 15 || index % (data.size / 10).coerceAtLeast(1) == 0 ) {
                drawContext.canvas.nativeCanvas.drawText(
                    itemData.label,
                    left + actualBarWidth / 2,
                    chartAreaHeight + xAxisLabelHeight - density.run { 4.dp.toPx() },
                    textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER }
                )
            }
        }

        drawLine(
            color = axisColor,
            start = Offset(yAxisLabelWidth, chartAreaHeight),
            end = Offset(size.width, chartAreaHeight),
            strokeWidth = 2f
        )

        drawLine(
            color = axisColor,
            start = Offset(yAxisLabelWidth, 0f),
            end = Offset(yAxisLabelWidth, chartAreaHeight),
            strokeWidth = 2f
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimeListenedScreenPreview() {
    PurrytifyTheme {
        DataKeeper.currentSelectedCapsule = MonthlySoundCapsuleData(
            monthYear = "April 2025",
            timeListenedMinutes = 862,
            dailyAverageMinutes = 33,
            topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
            topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
            dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
            hasData = true
        )
        TimeListenedScreen(navController = rememberNavController())
    }
}