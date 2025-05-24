package com.tubesmobile.purrytify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import com.tubesmobile.purrytify.service.DataKeeper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileWriter
import android.os.Environment
import android.content.Context
import android.util.Log
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import com.tubesmobile.purrytify.data.local.db.entities.SongPlaybackHistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


data class MonthlyAnalytics(
    val monthYearDisplay: String,
    val totalTimeListenedMinutes: Long = 0,
    val topArtist: String? = null,
    val topSongTitle: String? = null,
    val topSongArtist: String? = null,
    val dayStreakSongTitle: String? = null,
    val dayStreakSongArtist: String? = null,
    val dayStreakCount: Int = 0,
    val isEmpty: Boolean = true
)

class SoundCapsuleViewModel(application: Application) : AndroidViewModel(application) {
    private val playbackHistoryDao = AppDatabase.getDatabase(application).songPlaybackHistoryDao()
    private val userEmail = DataKeeper.email ?: ""

    private val internalMonthYearFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val monthDisplayFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private val _selectedMonthYear = MutableStateFlow(getCurrentMonthYearString())
    val selectedMonthYear: StateFlow<String> = _selectedMonthYear

    val monthlyAnalytics: StateFlow<MonthlyAnalytics?> = _selectedMonthYear.flatMapLatest { monthYear ->
        if (userEmail.isBlank() || monthYear.isBlank()) {
            flowOf(null)
        } else {
            combine(
                playbackHistoryDao.getTotalListenTimeForMonth(userEmail, monthYear),
                playbackHistoryDao.getTopArtistForMonth(userEmail, monthYear),
                playbackHistoryDao.getTopSongForMonth(userEmail, monthYear),
                playbackHistoryDao.getPlaybackHistoryForMonth(userEmail, monthYear)
            ) { totalTimeMs, topArtistInfo, topSongInfo, history ->
                Log.d("SoundCapsuleVM_Top", "Month: $monthYear, TopArtistDAO: $topArtistInfo, TopSongDAO: $topSongInfo")
                Log.d("SoundCapsuleVM_Time", "Month: $monthYear, Raw TotalTimeMs from DAO: $totalTimeMs")
                val totalMinutes = totalTimeMs?.let { TimeUnit.MILLISECONDS.toMinutes(it) } ?: 0L
                Log.d("SoundCapsuleVM_Time", "Month: $monthYear, Calculated TotalMinutes: $totalMinutes")
                val streakInfo = calculateLongestStreak(history, userEmail, monthYear)


                MonthlyAnalytics(
                    monthYearDisplay = getDisplayMonthYear(monthYear),
                    totalTimeListenedMinutes = totalMinutes,
                    topArtist = topArtistInfo?.songArtist,
                    topSongTitle = topSongInfo?.songTitle,
                    topSongArtist = topSongInfo?.songArtist,
                    dayStreakSongTitle = streakInfo?.songTitle,
                    dayStreakSongArtist = streakInfo?.songArtist,
                    dayStreakCount = streakInfo?.streakCount ?: 0,
                    isEmpty = (totalTimeMs == null || totalTimeMs == 0L) && topArtistInfo == null && topSongInfo == null && streakInfo == null
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)


    private fun calculateLongestStreak(history: List<SongPlaybackHistoryEntity>, userEmail: String, monthYear: String): StreakInfo? {
        if (history.isEmpty()) return null

        val songPlayDates = mutableMapOf<Int, MutableSet<LocalDate>>()

        history.forEach { event ->
            val eventCalendar = Calendar.getInstance().apply { timeInMillis = event.startTimestampMs }
            val eventMonthYear = internalMonthYearFormat.format(eventCalendar.time)

            if (eventMonthYear == monthYear) {
                val localDate = Instant.ofEpochMilli(event.startTimestampMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                songPlayDates.getOrPut(event.songId) { mutableSetOf() }.add(localDate)
            }
        }

        var longestStreakSongId: Int? = null
        var longestStreakCount = 0

        songPlayDates.forEach { (songId, dates) ->
            if (dates.size < 2) return@forEach

            val sortedDates = dates.sorted()
            var currentStreak = 1
            for (i in 1 until sortedDates.size) {
                if (sortedDates[i].isEqual(sortedDates[i - 1].plusDays(1))) {
                    currentStreak++
                } else {
                    if (currentStreak > longestStreakCount && currentStreak >= 2) {
                        longestStreakCount = currentStreak
                        longestStreakSongId = songId
                    }
                    currentStreak = 1
                }
            }
            if (currentStreak > longestStreakCount && currentStreak >= 2) {
                longestStreakCount = currentStreak
                longestStreakSongId = songId
            }
        }

        if (longestStreakSongId != null && longestStreakCount >= 2) {
            val songDetails = history.firstOrNull { it.songId == longestStreakSongId }
            return StreakInfo(
                songTitle = songDetails?.songTitle ?: "Unknown Song",
                songArtist = songDetails?.songArtist ?: "Unknown Artist",
                streakCount = longestStreakCount
            )
        }
        return null
    }

    data class StreakInfo(val songTitle: String, val songArtist: String, val streakCount: Int)


    fun changeMonth(offset: Int) {
        val current = Calendar.getInstance()
        try {
            current.time = internalMonthYearFormat.parse(_selectedMonthYear.value)!!
        } catch (e: Exception) { /* fallback to current month */ }
        current.add(Calendar.MONTH, offset)
        _selectedMonthYear.value = internalMonthYearFormat.format(current.time)
    }

    private fun getCurrentMonthYearString(): String {
        return internalMonthYearFormat.format(Calendar.getInstance().time)
    }

    private fun getDisplayMonthYear(internalMonthYear: String): String {
        return try {
            val date = internalMonthYearFormat.parse(internalMonthYear)
            date?.let { monthDisplayFormat.format(it) } ?: "Invalid Date"
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    fun exportAnalyticsToCSV(context: Context, monthYear: String, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val userEmail = DataKeeper.email ?: ""
            if (userEmail.isBlank()) {
                onFinished(false, "User not logged in.")
                return@launch
            }
            val history = playbackHistoryDao.getRawPlaybackHistoryForMonthSync(userEmail, monthYear)
            val analytics = monthlyAnalytics.value // Get current analytics

            if (history.isEmpty() && (analytics == null || analytics.isEmpty)) {
                onFinished(false, "No data to export for $monthYear.")
                return@launch
            }

            val fileName = "Purrytify_SoundCapsule_${monthYear.replace("-", "_")}.csv"
            try {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
                FileWriter(file).use { writer ->

                    writer.append("Purrytify Sound Capsule: ${analytics?.monthYearDisplay ?: monthYear}\n")
                    writer.append("Metric,Value\n")
                    writer.append("Total Time Listened (minutes),${analytics?.totalTimeListenedMinutes ?: 0}\n")
                    writer.append("Top Artist,${analytics?.topArtist ?: "N/A"}\n")
                    writer.append("Top Song,${analytics?.topSongTitle ?: "N/A"} by ${analytics?.topSongArtist ?: "N/A"}\n")
                    writer.append("Longest Day Streak,${analytics?.dayStreakCount ?: 0} days for ${analytics?.dayStreakSongTitle ?: "N/A"} by ${analytics?.dayStreakSongArtist ?: "N/A"}\n")
                    writer.append("\n") // Separator

                    writer.append("Detailed Playback History\n")
                    writer.append("Song Title,Artist,Played At (Local Time),Duration Listened (seconds)\n") // CSV Header
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    history.forEach {
                        writer.append("\"${it.songTitle.replace("\"", "\"\"")}\",") // Handle commas in title
                        writer.append("\"${it.songArtist.replace("\"", "\"\"")}\",") // Handle commas in artist
                        writer.append("${sdf.format(it.startTimestampMs)},")
                        writer.append("${TimeUnit.MILLISECONDS.toSeconds(it.durationListenedMs)}\n")
                    }
                }
                onFinished(true, "Exported to Documents/$fileName")
            } catch (e: Exception) {
                Log.e("SoundCapsuleVM", "Error exporting to CSV", e)
                onFinished(false, "Error exporting: ${e.message}")
            }
        }
    }


    fun exportAnalyticsToPDF(context: Context, monthYear: String, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val userEmail = DataKeeper.email ?: ""
            if (userEmail.isBlank()) {
                onFinished(false, "User not logged in.")
                return@launch
            }

            val history = playbackHistoryDao.getRawPlaybackHistoryForMonthSync(userEmail, monthYear)
            val analytics = monthlyAnalytics.value

            if (history.isEmpty() && (analytics == null || analytics.isEmpty)) {
                onFinished(false, "No data to export for $monthYear.")
                return@launch
            }

            val fileName = "Purrytify_SoundCapsule_${monthYear.replace("-", "_")}.pdf"
            try {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
                val pdfWriter = PdfWriter(file)
                val pdfDocument = PdfDocument(pdfWriter)
                val document = Document(pdfDocument)

                document.add(Paragraph("Purrytify Sound Capsule: ${analytics?.monthYearDisplay ?: monthYear}").setBold().setFontSize(18f))
                document.add(Paragraph("\n"))

                document.add(Paragraph("Summary").setBold())
                val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 2f))).useAllAvailableWidth()
                summaryTable.addCell("Metric")
                summaryTable.addCell("Value")
                summaryTable.addCell("Total Time Listened (minutes)")
                summaryTable.addCell(analytics?.totalTimeListenedMinutes?.toString() ?: "0")
                summaryTable.addCell("Top Artist")
                summaryTable.addCell(analytics?.topArtist ?: "N/A")
                summaryTable.addCell("Top Song")
                summaryTable.addCell("${analytics?.topSongTitle ?: "N/A"} by ${analytics?.topSongArtist ?: "N/A"}")
                summaryTable.addCell("Longest Day Streak")
                summaryTable.addCell("${analytics?.dayStreakCount ?: 0} days for ${analytics?.dayStreakSongTitle ?: "N/A"} by ${analytics?.dayStreakSongArtist ?: "N/A"}")
                document.add(summaryTable)
                document.add(Paragraph("\n"))

                document.add(Paragraph("Detailed Playback History").setBold())
                val detailTable = Table(UnitValue.createPercentArray(floatArrayOf(2f, 2f, 2f, 1.5f))).useAllAvailableWidth()
                detailTable.addHeaderCell("Song Title")
                detailTable.addHeaderCell("Artist")
                detailTable.addHeaderCell("Played At (Local Time)")
                detailTable.addHeaderCell("Duration (sec)")

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                history.forEach {
                    detailTable.addCell(it.songTitle)
                    detailTable.addCell(it.songArtist)
                    detailTable.addCell(sdf.format(it.startTimestampMs))
                    detailTable.addCell(TimeUnit.MILLISECONDS.toSeconds(it.durationListenedMs).toString())
                }
                document.add(detailTable)
                document.close()
                onFinished(true, "Exported to Documents/$fileName")
            } catch (e: Exception) {
                Log.e("SoundCapsuleVM", "Error exporting to PDF", e)
                onFinished(false, "Error exporting: ${e.message}")
            }
        }
    }
}