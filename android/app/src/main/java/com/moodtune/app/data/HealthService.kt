@file:OptIn(ExperimentalMindfulnessSessionApi::class)

package com.moodtune.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.moodtune.app.model.HealthAccessStatus
import com.moodtune.app.model.HealthLoadResult
import com.moodtune.app.model.HealthSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthService(private val context: Context) {
    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(MindfulnessSessionRecord::class),
    )

    fun availability(): HealthAccessStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAccessStatus.Ready
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            HealthAccessStatus.Failed("Update Health Connect to read health data.")
        else -> HealthAccessStatus.Unavailable
    }

    suspend fun loadSnapshot(): HealthLoadResult {
        val status = availability()
        if (status != HealthAccessStatus.Ready) {
            return HealthLoadResult(status = status, snapshot = null)
        }
        val snapshot = fetchSnapshot()
        return if (snapshot.hasAnySignal) {
            HealthLoadResult(status = HealthAccessStatus.Ready, snapshot = snapshot)
        } else {
            HealthLoadResult(status = HealthAccessStatus.Empty, snapshot = snapshot)
        }
    }

    private suspend fun fetchSnapshot(): HealthSnapshot {
        val client = HealthConnectClient.getOrCreate(context)
        val workout = readWorkout(client)
        return HealthSnapshot(
            stepsToday = readSteps(client),
            activeCalories = readCalories(client),
            latestHeartRate = readHeartRate(client),
            heartRateVariabilityMS = readHrv(client),
            sleepHours = readSleepHours(client),
            mindfulMinutes = readMindfulMinutes(client),
            recentWorkoutMinutes = workout?.minutes,
            hoursSinceWorkout = workout?.hoursAgo,
            isSampleData = false,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun readSteps(client: HealthConnectClient): Double? = read {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
            ),
        )
        response[StepsRecord.COUNT_TOTAL]?.toDouble()
    }

    private suspend fun readCalories(client: HealthConnectClient): Double? = read {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
            ),
        )
        response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    }

    private suspend fun readHeartRate(client: HealthConnectClient): Double? = read {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
                ascendingOrder = false,
                pageSize = 1,
            ),
        )
        response.records.firstOrNull()?.samples?.maxByOrNull { it.time }?.beatsPerMinute?.toDouble()
    }

    private suspend fun readHrv(client: HealthConnectClient): Double? = read {
        val since = Instant.now().minus(Duration.ofDays(7))
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, Instant.now()),
                ascendingOrder = false,
                pageSize = 1,
            ),
        )
        response.records.firstOrNull()?.heartRateVariabilityMillis
    }

    private suspend fun readSleepHours(client: HealthConnectClient): Double? = read {
        val start = startOfToday().minus(Duration.ofHours(18))
        val end = startOfToday().plus(Duration.ofHours(12))
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = true,
                pageSize = 100,
            ),
        )
        val asleep = setOf(
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP,
            SleepSessionRecord.STAGE_TYPE_REM,
        )
        var minutes = 0.0
        for (session in response.records) {
            val stages = session.stages.filter { it.stage in asleep }
            if (stages.isEmpty()) {
                minutes += minutesBetween(session.startTime, session.endTime)
            } else {
                minutes += stages.sumOf { minutesBetween(it.startTime, it.endTime) }
            }
        }
        if (minutes > 0.0) minutes / 60.0 else null
    }

    private suspend fun readMindfulMinutes(client: HealthConnectClient): Double? = read {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = MindfulnessSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
                pageSize = 50,
            ),
        )
        val minutes = response.records.sumOf { minutesBetween(it.startTime, it.endTime) }
        if (minutes > 0.0) minutes else null
    }

    private suspend fun readWorkout(client: HealthConnectClient): WorkoutTiming? = read {
        val since = Instant.now().minus(Duration.ofHours(36))
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, Instant.now()),
                ascendingOrder = false,
                pageSize = 10,
            ),
        )
        val latest = response.records.maxByOrNull { it.endTime } ?: return@read null
        WorkoutTiming(
            minutes = minutesBetween(latest.startTime, latest.endTime),
            hoursAgo = Duration.between(latest.endTime, Instant.now()).toMinutes() / 60.0,
        )
    }

    private suspend fun <T> read(block: suspend () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private fun startOfToday(): Instant =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun minutesBetween(start: Instant, end: Instant): Double =
        Duration.between(start, end).toMillis() / 60_000.0

    private data class WorkoutTiming(val minutes: Double, val hoursAgo: Double)
}
