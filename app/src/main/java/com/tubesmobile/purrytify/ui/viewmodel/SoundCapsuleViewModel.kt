// SoundCapsuleViewModel.kt
package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.SongDao
import com.tubesmobile.purrytify.data.model.ArtistData
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.data.model.SongData
import com.tubesmobile.purrytify.service.DataKeeper
import com.tubesmobile.purrytify.util.PdfGenerator
import com.tubesmobile.purrytify.ui.components.CapsuleShareView
import com.tubesmobile.purrytify.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DailyChartData(
    val day: Int,
    val minutesListened: Int,
    val label: String
)

class SoundCapsuleViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao: SongDao = AppDatabase.getDatabase(application).songDao()
    private var currentUserEmail = DataKeeper.email ?: ""

    private val _dailyChartData = MutableStateFlow<List<DailyChartData>>(emptyList())
    val dailyChartData: StateFlow<List<DailyChartData>> = _dailyChartData

    private val _monthlyCapsules = MutableStateFlow<List<MonthlySoundCapsuleData>>(emptyList())
    val monthlyCapsules: StateFlow<List<MonthlySoundCapsuleData>> = _monthlyCapsules

    private val _isSharingImage = MutableStateFlow(false)
    val isSharingImage: StateFlow<Boolean> = _isSharingImage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingDailyChart = MutableStateFlow(false)
    val isLoadingDailyChart: StateFlow<Boolean> = _isLoadingDailyChart

    init {
        if (DataKeeper.email != null && DataKeeper.email!!.isNotBlank()) {
            currentUserEmail = DataKeeper.email!!
            loadSoundCapsuleData()
        } else {
            Log.e("SoundCapsuleVM", "User email is blank at init, cannot load capsules.")
            _monthlyCapsules.value = createEmptyOrErrorCapsuleList("Error: User not logged in")
        }
    }

    fun updateUserEmailAndReload(newEmail: String?) {
        if (newEmail != null && newEmail.isNotBlank()) {
            currentUserEmail = newEmail
            loadSoundCapsuleData()
        } else {
            Log.e("SoundCapsuleVM", "User email is blank on update, cannot load capsules.")
            _monthlyCapsules.value = createEmptyOrErrorCapsuleList("Error: User not logged in")
            _isLoading.value = false
        }
    }

    fun shareCapsuleAsImage(
        contextForActivity: Context,
        capsuleData: MonthlySoundCapsuleData
    ) {
        if (_isSharingImage.value) {
            return
        }

        _isSharingImage.value = true
        viewModelScope.launch {
            try {
                val mainImageUrl = capsuleData.topSongImageUrl
                    ?: capsuleData.topArtistImageUrl
                    ?: capsuleData.dayStreakImage

                val displayMetrics = getApplication<Application>().resources.displayMetrics
                val fontScale = displayMetrics.scaledDensity / displayMetrics.density
                val density = Density(density = displayMetrics.density, fontScale = fontScale)


                val widthPx = density.run { 360.dp.toPx().toInt() }
                val heightPx = density.run { 640.dp.toPx().toInt() }

                Log.d("ShareCapsule", "Attempting to create bitmap: ${widthPx}x$heightPx with density: ${density.density}, fontScale: ${density.fontScale}")

                val bitmap = ImageUtils.createBitmapFromComposable(
                    context = getApplication(),
                    widthPx = widthPx,
                    heightPx = heightPx
                ) {
                    CapsuleShareView(
                        capsuleData = capsuleData,
                        mainImageUrl = mainImageUrl
                    )
                }

                if (bitmap != null) {
                    Log.d("ShareCapsule", "Bitmap created successfully.")
                    val imageFile = ImageUtils.saveBitmapToTempFile(getApplication(), bitmap)

                    if (imageFile != null) {
                        Log.d("ShareCapsule", "Bitmap saved to temp file: ${imageFile.absolutePath}")
                        val imageUri = ImageUtils.getUriForFile(getApplication(), imageFile)
                        Log.d("ShareCapsule", "Image URI: $imageUri")

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            putExtra(Intent.EXTRA_SUBJECT, "My ${capsuleData.monthYear} Sound Capsule")
                            putExtra(Intent.EXTRA_TEXT, "Check out my ${capsuleData.monthYear} Sound Capsule on Purrify! #PurrifyCapsule")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        val chooserIntent = Intent.createChooser(shareIntent, "Share Sound Capsule Via")
                        if (contextForActivity is android.app.Activity) {
                            contextForActivity.startActivity(chooserIntent)
                        } else {
                            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            getApplication<Application>().startActivity(chooserIntent)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(getApplication(), "Sharing via application context...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Log.e("ShareCapsule", "Failed to save shareable image to temp file.")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "Failed to save shareable image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("ShareCapsule", "Failed to create bitmap from Composable.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Failed to create shareable image.", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("ShareCapsule", "Error sharing capsule as image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main){
                    _isSharingImage.value = false
                }
            }
        }
    }

    fun loadDailyChartDataForCapsule(capsule: MonthlySoundCapsuleData?) {
        if (capsule == null || currentUserEmail.isBlank()) {
            _dailyChartData.value = emptyList()
            return
        }
        _isLoadingDailyChart.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance(TimeZone.getDefault())
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                try {
                    calendar.time = sdf.parse(capsule.monthYear) ?: Date()
                } catch (e: Exception) {
                    Log.e("SoundCapsuleVM", "Failed to parse monthYear for daily chart: ${capsule.monthYear}", e)
                }

                val (startTime, endTime) = getMonthStartAndEndTimestamps(calendar)

                val rawDailyData = songDao.getDailyPlayDurationsInMonth(currentUserEmail, startTime, endTime)

                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                val chartDataList = mutableListOf<DailyChartData>()

                val dataMap = rawDailyData.associateBy { it.dayOfMonth }

                for (day in 1..daysInMonth) {
                    val durationMillis = dataMap[day]?.totalDurationMillis ?: 0L
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis).toInt()
                    chartDataList.add(DailyChartData(day, minutes, day.toString()))
                }
                _dailyChartData.value = chartDataList
            } catch (e: Exception) {
                Log.e("SoundCapsuleVM", "Error loading daily chart data", e)
                _dailyChartData.value = emptyList()
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoadingDailyChart.value = false
                }
            }
        }
    }

    fun exportCapsuleToPdf(context: Context, capsuleData: MonthlySoundCapsuleData, username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = PdfGenerator.generateCapsulePdf(context, capsuleData, username)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "PDF saved to Downloads folder.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to generate PDF.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SoundCapsuleVM", "Error generating PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadSoundCapsuleData() {
        if (currentUserEmail.isBlank()) {
            Log.e("SoundCapsuleVM", "User email is blank in loadSoundCapsuleData, aborting.")
            _monthlyCapsules.value = createEmptyOrErrorCapsuleList("Error: User email not available")
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val capsules = mutableListOf<MonthlySoundCapsuleData>()
            val earliestTimestamp = songDao.getEarliestPlayLogTimestampForUser(currentUserEmail).firstOrNull()

            val currentMonthCalendar = Calendar.getInstance(TimeZone.getDefault())

            if (earliestTimestamp == null || earliestTimestamp == 0L) {
                Log.i("SoundCapsuleVM", "No valid play logs found for user $currentUserEmail. Displaying limited/no capsules.")
                if (_monthlyCapsules.value.isEmpty() || _monthlyCapsules.value.first().monthYear.startsWith("Error")) {
                    _monthlyCapsules.value = createEmptyOrErrorCapsuleList("No listening history found.")
                }
            } else {
                val firstLogCalendar = Calendar.getInstance(TimeZone.getDefault())
                firstLogCalendar.timeInMillis = earliestTimestamp
                firstLogCalendar.set(Calendar.DAY_OF_MONTH, 1)
                firstLogCalendar.set(Calendar.HOUR_OF_DAY, 0)
                firstLogCalendar.set(Calendar.MINUTE, 0)
                firstLogCalendar.set(Calendar.SECOND, 0)
                firstLogCalendar.set(Calendar.MILLISECOND, 0)

                val loopCalendar = firstLogCalendar.clone() as Calendar

                while (!loopCalendar.after(currentMonthCalendar) ||
                    (loopCalendar.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                            loopCalendar.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH))) {

                    val monthYearStr = String.format(
                        Locale.getDefault(),
                        "%tB %tY",
                        loopCalendar,
                        loopCalendar
                    )
                    val (startTime, endTime) = getMonthStartAndEndTimestamps(loopCalendar)
                    val data = generateCapsuleForMonth(currentUserEmail, monthYearStr, startTime, endTime)
                    capsules.add(data)

                    if (loopCalendar.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                        loopCalendar.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH)) {
                        break
                    }
                    loopCalendar.add(Calendar.MONTH, 1)
                    if (loopCalendar.after(currentMonthCalendar) &&
                        !(loopCalendar.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                                loopCalendar.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH))) {
                        break
                    }
                }
            }

            val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            _monthlyCapsules.value = capsules.sortedWith(compareByDescending {
                try {
                    sdf.parse(it.monthYear)
                } catch (e: Exception) {
                    Log.w("SoundCapsuleVM", "Failed to parse monthYear for sorting: ${it.monthYear}", e)
                    null
                }
            })
            _isLoading.value = false
        }
    }

    private fun createEmptyOrErrorCapsuleList(monthYearText: String): List<MonthlySoundCapsuleData> {
        // Menggunakan konstruktor default MonthlySoundCapsuleData yang menyediakan nilai default untuk field yang nullable
        return listOf(
            MonthlySoundCapsuleData(
                monthYear = monthYearText,
                timeListenedMinutes = null,
                dailyAverageMinutes = null,
                topArtistName = null,
                topArtistImageUrl = null,
                totalArtistsListenedThisMonth = null, // Sesuai model, default 0, tapi null lebih baik untuk "tidak ada data"
                topArtistsList = null,
                topSongName = null,
                topSongImageUrl = null,
                totalSongsPlayedThisMonth = null,
                topSongsList = null,
                dayStreakCount = null,
                dayStreakSongName = null,
                dayStreakSongArtist = null,
                dayStreakFullText = null,
                dayStreakDateRange = null,
                dayStreakImage = null
            )
        )
    }

    private suspend fun generateCapsuleForMonth(
        userEmail: String,
        monthYear: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): MonthlySoundCapsuleData = withContext(Dispatchers.IO) {

        val playLogsInMonth = songDao.getPlayLogsForUserInMonth(userEmail, startTimeMillis, endTimeMillis)
            .firstOrNull()
            ?.filter { it.playedAtTimestamp > 0 && it.playedAtTimestamp >= startTimeMillis && it.playedAtTimestamp < endTimeMillis }
            ?: emptyList()

        if (playLogsInMonth.isEmpty()) {
            Log.d("SoundCapsuleVM", "No play logs for $monthYear ($userEmail)")
            return@withContext MonthlySoundCapsuleData(monthYear = monthYear, timeListenedMinutes = null, dailyAverageMinutes = null, topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null, topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null, dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null)
        }
        Log.d("SoundCapsuleVM", "Found ${playLogsInMonth.size} logs for $monthYear ($userEmail)")

        // Time Listened
        val totalTimeListenedMillis = playLogsInMonth.sumOf { it.durationListenedMillis }
        val timeListenedMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeListenedMillis).toInt()
        val calendarForMonth = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = startTimeMillis }
        val daysInMonth = calendarForMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailyAverageMinutes = if (daysInMonth > 0 && timeListenedMinutes > 0) timeListenedMinutes / daysInMonth else 0

        // Top Artists
        val topArtistsStats = songDao.getTopArtistsInMonthByDuration(userEmail, startTimeMillis, endTimeMillis).firstOrNull() ?: emptyList()
        val topArtistEntity = topArtistsStats.firstOrNull()
        val topArtistName = topArtistEntity?.artist
        val topArtistImageUrlFromDb = topArtistName?.let { artistName ->
            songDao.getSongsByUser(userEmail).firstOrNull()
                ?.find { song -> song.artist == artistName }?.artworkUri
        }
        val topArtistsListUi: List<ArtistData>? = if (topArtistsStats.isNotEmpty()) {
            topArtistsStats.take(5).mapIndexed { index, stats ->
                val anArtistImage = songDao.getSongsByUser(userEmail).firstOrNull()
                    ?.find { s -> s.artist == stats.artist }?.artworkUri ?: ""
                ArtistData(rank = index + 1, name = stats.artist, imageUrl = anArtistImage)
            }
        } else null
        val totalArtistsListened = if (topArtistsStats.isNotEmpty()) topArtistsStats.size else null

        // Top Songs
        val topSongsStats = songDao.getTopSongsInMonthByDuration(userEmail, startTimeMillis, endTimeMillis).firstOrNull() ?: emptyList()
        val topSongStat = topSongsStats.firstOrNull()
        val topSongName = topSongStat?.title
        val topSongImageUrl = topSongStat?.artworkUri
        val topSongsListUi: List<SongData>? = if (topSongsStats.isNotEmpty()) {
            topSongsStats.take(5).mapIndexed { index, stats ->
                SongData(
                    rank = index + 1,
                    title = stats.title,
                    artists = stats.artist,
                    imageUrl = stats.artworkUri ?: "",
                    plays = TimeUnit.MILLISECONDS.toMinutes(stats.totalDuration).toInt()
                )
            }
        } else null
        val totalSongsPlayedThisMonth = if (topSongsStats.isNotEmpty()) topSongsStats.size else null

        // Day Streak
        var maxStreak = 0
        var streakSongId: Int? = null
        var streakSongDetails: com.tubesmobile.purrytify.data.model.SongPlayStats? = null // Model Anda
        var streakStartDate: Long? = null
        var streakEndDate: Long? = null

        val songsPlayedThisMonthIds = playLogsInMonth.map { it.songId }.distinct()

        for (sId in songsPlayedThisMonthIds) {
            val logsForThisSongOnDistinctDays = playLogsInMonth
                .filter { it.songId == sId }
                .map {
                    Calendar.getInstance(TimeZone.getDefault()).apply {
                        timeInMillis = it.playedAtTimestamp
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                .distinct()
                .sorted()

            if (logsForThisSongOnDistinctDays.size < 2) continue

            var currentStreak = 1
            var currentStreakStartDay = logsForThisSongOnDistinctDays.first()

            for (k in 1 until logsForThisSongOnDistinctDays.size) {
                val dayDiff = TimeUnit.MILLISECONDS.toDays(logsForThisSongOnDistinctDays[k] - logsForThisSongOnDistinctDays[k - 1])
                if (dayDiff == 1L) {
                    currentStreak++
                } else if (dayDiff > 1L) {
                    if (currentStreak > maxStreak) {
                        maxStreak = currentStreak
                        streakSongId = sId
                        streakStartDate = currentStreakStartDay
                        streakEndDate = logsForThisSongOnDistinctDays[k-1]
                    }
                    currentStreak = 1
                    currentStreakStartDay = logsForThisSongOnDistinctDays[k]
                }
            }
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
                streakSongId = sId
                streakStartDate = currentStreakStartDay
                streakEndDate = logsForThisSongOnDistinctDays.last()
            }
        }

        var dayStreakDateRangeStr: String? = null
        if (maxStreak >= 2 && streakSongId != null) { // Minimal 2 hari untuk dianggap streak
            streakSongDetails = topSongsStats.find { it.songId == streakSongId }
            if (streakStartDate != null && streakEndDate != null) {
                val sdfDate = SimpleDateFormat("MMM d", Locale.getDefault())
                val startDateStr = sdfDate.format(streakStartDate)
                val endDateStr = sdfDate.format(streakEndDate)
                dayStreakDateRangeStr = "$startDateStr - $endDateStr"
            }
        }

        val finalDayStreakCount = if (maxStreak >= 2) maxStreak else null
        val finalDayStreakSongName = if (finalDayStreakCount != null) streakSongDetails?.title else null
        val finalDayStreakSongArtist = if (finalDayStreakCount != null) streakSongDetails?.artist else null
        val finalDayStreakImage = if (finalDayStreakCount != null) streakSongDetails?.artworkUri else null
        val finalDayStreakFullText = if (finalDayStreakCount != null && finalDayStreakSongName != null && finalDayStreakSongArtist != null) {
            "You listened to $finalDayStreakSongName by $finalDayStreakSongArtist for $finalDayStreakCount consecutive days."
        } else null


        return@withContext MonthlySoundCapsuleData(
            monthYear = monthYear,
            timeListenedMinutes = if (timeListenedMinutes > 0) timeListenedMinutes else null,
            dailyAverageMinutes = if (dailyAverageMinutes > 0) dailyAverageMinutes else null,
            topArtistName = topArtistsStats.firstOrNull()?.artist,
            topArtistImageUrl = topArtistsStats.firstOrNull()?.artist?.let { artistName ->
                songDao.getSongsByUser(userEmail).firstOrNull()
                    ?.find { song -> song.artist == artistName }?.artworkUri
            },
            totalArtistsListenedThisMonth = if (topArtistsStats.isNotEmpty()) topArtistsStats.size else null,
            topArtistsList = if (topArtistsStats.isNotEmpty()) {
                topArtistsStats.take(5).mapIndexed { index, stats ->
                    val anArtistImage = songDao.getSongsByUser(userEmail).firstOrNull()
                        ?.find { s -> s.artist == stats.artist }?.artworkUri ?: ""
                    ArtistData(rank = index + 1, name = stats.artist, imageUrl = anArtistImage)
                }
            } else null,
            topSongName = topSongsStats.firstOrNull()?.title,
            topSongImageUrl = topSongsStats.firstOrNull()?.artworkUri,
            totalSongsPlayedThisMonth = if (topSongsStats.isNotEmpty()) topSongsStats.size else null,
            topSongsList = topSongsListUi,
            dayStreakCount = if (maxStreak >= 2) maxStreak else null,
            dayStreakSongName = if (maxStreak >= 2) (topSongsStats.find { it.songId == streakSongId })?.title else null,
            dayStreakSongArtist = if (maxStreak >= 2) (topSongsStats.find { it.songId == streakSongId })?.artist else null,
            dayStreakFullText = if (maxStreak >=2 && streakSongId != null) {
                val streakS = topSongsStats.find { it.songId == streakSongId }
                if (streakS != null) "You listened to ${streakS.title} by ${streakS.artist} for $maxStreak consecutive days." else null
            } else null,
            dayStreakDateRange = dayStreakDateRangeStr,
            dayStreakImage = if (maxStreak >=2) (topSongsStats.find { it.songId == streakSongId })?.artworkUri else null
        )
    }

    private fun getMonthStartAndEndTimestamps(calendarForMonth: Calendar): Pair<Long, Long> {
        val startCal = calendarForMonth.clone() as Calendar
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)
        val startTime = startCal.timeInMillis

        val endCal = calendarForMonth.clone() as Calendar
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)
        val endTime = endCal.timeInMillis
        return Pair(startTime, endTime)
    }
}