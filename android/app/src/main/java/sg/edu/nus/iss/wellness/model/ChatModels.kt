package sg.edu.nus.iss.wellness.model

/**
 * Corresponds to backend /api/chat/... endpoints.
 */

data class ChatRequest(val message: String)

data class ChatResponse(val reply: String)

/** Single message returned by GET /api/chat/history */
data class ChatMessage(
    val id: Long,
    val role: String,       // "user" | "assistant"
    val content: String,
    val createdAt: String?
)
