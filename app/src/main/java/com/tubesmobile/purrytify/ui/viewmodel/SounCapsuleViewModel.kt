package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.SongDao
import com.tubesmobile.purrytify.data.model.ArtistData
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import com.tubesmobile.purrytify.data.model.SongData
import com.tubesmobile.purrytify.service.DataKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SoundCapsuleViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao: SongDao = AppDatabase.getDatabase(application).songDao()
    private val currentUserEmail = DataKeeper.email ?: ""

    private val _monthlyCapsules = MutableStateFlow<List<MonthlySoundCapsuleData>>(emptyList())
    val monthlyCapsules: StateFlow<List<MonthlySoundCapsuleData>> = _monthlyCapsules

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        if (currentUserEmail.isNotBlank()) {
            loadSoundCapsuleData()
        } else {
            Log.e("SoundCapsuleVM", "User email is blank, cannot load capsules.")
            _monthlyCapsules.value = listOf(
                MonthlySoundCapsuleData(
                    monthYear = "Error", timeListenedMinutes = null, dailyAverageMinutes = null,
                    topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null,
                    topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null,
                    dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null,
                    hasData = false
                )
            )
        }
    }

    fun loadSoundCapsuleData() {
        viewModelScope.launch {
            _isLoading.value = true
            val capsules = mutableListOf<MonthlySoundCapsuleData>()
            val calendar = Calendar.getInstance(TimeZone.getDefault())

            for (i in 0..5) { // generate for current month and previous 5 months
                val monthCalendar = Calendar.getInstance(TimeZone.getDefault())
                monthCalendar.timeInMillis = calendar.timeInMillis
                monthCalendar.add(Calendar.MONTH, -i)

                val monthYearStr = String.format(
                    "%tB %tY", // locale-specific month name and year
                    monthCalendar,
                    monthCalendar
                )
                val (startTime, endTime) = getMonthStartAndEndTimestamps(monthCalendar)

                val data = generateCapsuleForMonth(currentUserEmail, monthYearStr, startTime, endTime)
                capsules.add(data)
            }
            _monthlyCapsules.value = capsules.sortedByDescending { it.monthYear }
            _isLoading.value = false
        }
    }

    private suspend fun generateCapsuleForMonth(
        userEmail: String,
        monthYear: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): MonthlySoundCapsuleData = withContext(Dispatchers.IO) {

        val playLogsFlow = songDao.getPlayLogsForUserInMonth(userEmail, startTimeMillis, endTimeMillis)
        val playLogsInMonth = playLogsFlow.firstOrNull() ?: emptyList()

        if (playLogsInMonth.isEmpty()) {
            return@withContext MonthlySoundCapsuleData(monthYear = monthYear, hasData = false, timeListenedMinutes = null, dailyAverageMinutes = null, topArtistName = null, topArtistImageUrl = null, totalArtistsListenedThisMonth = null, topArtistsList = null, topSongName = null, topSongImageUrl = null, totalSongsPlayedThisMonth = null, topSongsList = null, dayStreakCount = null, dayStreakSongName = null, dayStreakSongArtist = null, dayStreakFullText = null, dayStreakDateRange = null, dayStreakImage = null)
        }

        // Time Listened
        val totalTimeListenedMillis = playLogsInMonth.sumOf { it.durationListenedMillis }
        val timeListenedMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeListenedMillis).toInt()
        val daysInMonth = Calendar.getInstance().apply { timeInMillis = startTimeMillis }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailyAverageMinutes = if (daysInMonth > 0) timeListenedMinutes / daysInMonth else 0


        // Top Artists
        val topArtistsStats = songDao.getTopArtistsInMonthByPlayCount(userEmail, startTimeMillis, endTimeMillis).firstOrNull() ?: emptyList()
        val topArtistEntity = topArtistsStats.firstOrNull()
        val topArtistName = topArtistEntity?.artist
        val topArtistImageUrlFromDb = topArtistName?.let {
            songDao.getSongsByUser(userEmail).firstOrNull()
                ?.find { song -> song.artist == it }?.artworkUri // Simple way to get an image
        }
        val topArtistsListUi = topArtistsStats.take(4).mapIndexed { index, stats ->
            val anArtistImage = songDao.getSongsByUser(userEmail).firstOrNull()
                ?.find { s -> s.artist == stats.artist }?.artworkUri ?: ""
            ArtistData(index + 1, stats.artist, anArtistImage)
        }
        val totalArtistsListened = topArtistsStats.size


        // Top Songs
        val topSongsStats = songDao.getTopSongsInMonthByPlayCount(userEmail, startTimeMillis, endTimeMillis).firstOrNull() ?: emptyList()
        val topSongStat = topSongsStats.firstOrNull()
        val topSongName = topSongStat?.title
        val topSongImageUrl = topSongStat?.artworkUri
        val topSongsListUi = topSongsStats.take(4).mapIndexed { index, stats ->
            SongData(index + 1, stats.title, stats.artist, stats.artworkUri ?: "", stats.playCount)
        }
        val totalSongsPlayedThisMonth = topSongsStats.map { it.songId }.distinct().size


        // Day Streak (This is a simplified version)
        var maxStreak = 0
        var streakSongId: Int? = null
        var streakSongDetails: com.tubesmobile.purrytify.data.model.SongPlayStats? = null

        val songsPlayedThisMonthIds = playLogsInMonth.map { it.songId }.distinct()
        for (sId in songsPlayedThisMonthIds) {
            val logsForThisSong = playLogsInMonth.filter { it.songId == sId }
                .sortedBy { it.playedAtTimestamp }
                .map {
                    // Normalize to day start
                    Calendar.getInstance().apply {
                        timeInMillis = it.playedAtTimestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }.distinct()

            if (logsForThisSong.size < 2) continue

            var currentStreak = 1
            for (k in 1 until logsForThisSong.size) {
                val dayDiff = TimeUnit.MILLISECONDS.toDays(logsForThisSong[k] - logsForThisSong[k - 1])
                if (dayDiff == 1L) {
                    currentStreak++
                } else if (dayDiff > 1L) {
                    if (currentStreak > maxStreak) {
                        maxStreak = currentStreak
                        streakSongId = sId
                    }
                    currentStreak = 1
                }
            }
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
                streakSongId = sId
            }
        }

        if (maxStreak >= 2 && streakSongId != null) {
            streakSongDetails = topSongsStats.find { it.songId == streakSongId }
        }


        return@withContext MonthlySoundCapsuleData(
            monthYear = monthYear,
            timeListenedMinutes = timeListenedMinutes,
            dailyAverageMinutes = dailyAverageMinutes,
            topArtistName = topArtistName,
            topArtistImageUrl = topArtistImageUrlFromDb,
            totalArtistsListenedThisMonth = totalArtistsListened,
            topArtistsList = topArtistsListUi,
            topSongName = topSongName,
            topSongImageUrl = topSongImageUrl,
            totalSongsPlayedThisMonth = totalSongsPlayedThisMonth,
            topSongsList = topSongsListUi,
            dayStreakCount = if (maxStreak >=2) maxStreak else null,
            dayStreakSongName = if (maxStreak >=2) streakSongDetails?.title else null,
            dayStreakSongArtist = if (maxStreak >=2) streakSongDetails?.artist else null,
            dayStreakFullText = if (maxStreak >=2 && streakSongDetails != null) "You played ${streakSongDetails.title} by ${streakSongDetails.artist} for $maxStreak days." else null,
            dayStreakDateRange = null, // TODO: Determine actual date range for streak
            dayStreakImage = if (maxStreak >=2) streakSongDetails?.artworkUri else null,
            hasData = true
        )
    }

    private fun getMonthStartAndEndTimestamps(calendarForMonth: Calendar): Pair<Long, Long> {
        val startCal = Calendar.getInstance(TimeZone.getDefault())
        startCal.timeInMillis = calendarForMonth.timeInMillis
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)
        val startTime = startCal.timeInMillis

        val endCal = Calendar.getInstance(TimeZone.getDefault())
        endCal.timeInMillis = calendarForMonth.timeInMillis
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)
        val endTime = endCal.timeInMillis
        return Pair(startTime, endTime)
    }
}