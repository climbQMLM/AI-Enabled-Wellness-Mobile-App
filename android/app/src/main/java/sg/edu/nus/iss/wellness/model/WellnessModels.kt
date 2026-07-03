package sg.edu.nus.iss.wellness.model

import com.google.gson.annotations.SerializedName

/**
 * Corresponds to backend /api/wellness/... endpoints.
 * Field names match backend JSON keys (camelCase); Gson maps them automatically.
 */

/** Single wellness log returned by GET /api/wellness */
data class WellnessLog(
    val id: Long,
    val logDate: String,            // "2026-06-28" ISO date string
    val source: String,             // "manual" | "ringconn"
    val steps: Int?,
    val caloriesKcal: Int?,
    val hrAvg: Int?,
    val hrMin: Int?,
    val hrMax: Int?,
    val spo2Avg: Int?,
    val spo2Min: Int?,
    val spo2Max: Int?,
    val hrvAvg: Int?,
    val hrvMin: Int?,
    val hrvMax: Int?,
    val timeAsleepMin: Int?,
    val deepSleepMin: Int?,
    val remSleepMin: Int?,
    val lightSleepMin: Int?,
    val awakeMin: Int?,
    val sleepRatio: Double?,
    val readinessScore: Int?,
    val createdAt: String?,
    val updatedAt: String?
)

/** Request body for POST /api/wellness (manual create) */
data class WellnessCreateRequest(
    val logDate: String,
    val steps: Int? = null,
    val caloriesKcal: Int? = null,
    val hrAvg: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    val spo2Avg: Int? = null,
    val spo2Min: Int? = null,
    val spo2Max: Int? = null,
    val hrvAvg: Int? = null,
    val hrvMin: Int? = null,
    val hrvMax: Int? = null,
    val timeAsleepMin: Int? = null,
    val deepSleepMin: Int? = null,
    val remSleepMin: Int? = null,
    val lightSleepMin: Int? = null,
    val awakeMin: Int? = null
)

/** Request body for PUT /api/wellness/{id} partial update (null = keep existing value) */
data class WellnessPatchRequest(
    val steps: Int? = null,
    val caloriesKcal: Int? = null,
    val hrAvg: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    val spo2Avg: Int? = null,
    val spo2Min: Int? = null,
    val spo2Max: Int? = null,
    val hrvAvg: Int? = null,
    val hrvMin: Int? = null,
    val hrvMax: Int? = null,
    val timeAsleepMin: Int? = null,
    val deepSleepMin: Int? = null,
    val remSleepMin: Int? = null,
    val lightSleepMin: Int? = null,
    val awakeMin: Int? = null
)

/** Summary response from successful POST /api/wellness/import */
data class ImportResponse(
    val importedDates: Int,
    val updated: Int,
    val inserted: Int,
    val dateRange: List<String>,    // ["2026-01-01", "2026-06-29"]
    val skippedRows: Int
)
