// SoundCapsuleViewModel.kt
package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.data.local.db.SongDao
import com.tubesmobile.purrytify.data.model.ArtistData // Model Anda
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData // Model Anda
import com.tubesmobile.purrytify.data.model.SongData // Model Anda
import com.tubesmobile.purrytify.service.DataKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SoundCapsuleViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao: SongDao = AppDatabase.getDatabase(application).songDao()
    private var currentUserEmail = DataKeeper.email ?: ""

    private val _monthlyCapsules = MutableStateFlow<List<MonthlySoundCapsuleData>>(emptyList())
    val monthlyCapsules: StateFlow<List<MonthlySoundCapsuleData>> = _monthlyCapsules

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
                for (i in 0..2) {
                    val monthCalendar = Calendar.getInstance(TimeZone.getDefault())
                    monthCalendar.add(Calendar.MONTH, -i)
                    val monthYearStr = String.format(
                        Locale.getDefault(),
                        "%tB %tY",
                        monthCalendar,
                        monthCalendar
                    )
                    val (startTime, endTime) = getMonthStartAndEndTimestamps(monthCalendar)
                    capsules.add(generateCapsuleForMonth(currentUserEmail, monthYearStr, startTime, endTime))
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
                totalSongsPlayedThisMonth = null, // Sesuai model, default 0, tapi null lebih baik
                topSongsList = null,
                dayStreakCount = null, // Sesuai model, default 0, tapi null lebih baik
                dayStreakSongName = null,
                dayStreakSongArtist = null,
                dayStreakFullText = null,
                dayStreakDateRange = null,
                dayStreakImage = null
                // hasData akan dievaluasi berdasarkan nullability field di atas
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
            // Return MonthlySoundCapsuleData with all nullable fields as null, hasData will be false
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
        val topArtistsStats = songDao.getTopArtistsInMonthByPlayCount(userEmail, startTimeMillis, endTimeMillis).firstOrNull() ?: emptyList()
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
        val topSongsStats = songDao.getTopSongsInMonthByPlayCount(userEmail, startTimeMillis, endTimeMillis).firstOrNull() ?: emptyList()
        val topSongStat = topSongsStats.firstOrNull()
        val topSongName = topSongStat?.title
        val topSongImageUrl = topSongStat?.artworkUri
        val topSongsListUi: List<SongData>? = if (topSongsStats.isNotEmpty()) {
            topSongsStats.take(5).mapIndexed { index, stats ->
                SongData(
                    rank = index + 1,
                    title = stats.title,
                    artists = stats.artist, // Menggunakan SongPlayStats.artist untuk SongData.artists
                    imageUrl = stats.artworkUri ?: "",
                    plays = stats.playCount
                )
            }
        } else null
        val totalSongsPlayedThisMonth = if (topSongsStats.isNotEmpty()) topSongsStats.size else null // Jumlah lagu UNIK

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
                // Jika streak hanya satu hari (maxStreak=1), startDate dan endDate bisa sama.
                // Tapi karena kita filter maxStreak >= 2, startDate dan endDate harusnya berbeda
                // kecuali ada kesalahan dalam logika di atas.
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
            topArtistName = topArtistName,
            topArtistImageUrl = topArtistImageUrlFromDb,
            totalArtistsListenedThisMonth = totalArtistsListened,
            topArtistsList = topArtistsListUi,
            topSongName = topSongName,
            topSongImageUrl = topSongImageUrl,
            totalSongsPlayedThisMonth = totalSongsPlayedThisMonth,
            topSongsList = topSongsListUi,
            dayStreakCount = finalDayStreakCount,
            dayStreakSongName = finalDayStreakSongName,
            dayStreakSongArtist = finalDayStreakSongArtist,
            dayStreakFullText = finalDayStreakFullText,
            dayStreakDateRange = dayStreakDateRangeStr,
            dayStreakImage = finalDayStreakImage
            // hasData akan dievaluasi secara otomatis oleh data class berdasarkan nilai-nilai ini
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