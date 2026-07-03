package sg.edu.nus.iss.wellness.model

/**
 * Agentic data classes corresponding to backend /api/agent/... and /api/recommendations endpoints.
 */

data class Recommendation(
    val id: Long,
    val recDate: String,
    val type: String,
    val createdBy: String,
    val content: String,
    val createdAt: String?
)
