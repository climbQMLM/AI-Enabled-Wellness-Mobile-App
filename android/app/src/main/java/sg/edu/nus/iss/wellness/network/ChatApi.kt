package sg.edu.nus.iss.wellness.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import sg.edu.nus.iss.wellness.model.ChatMessage
import sg.edu.nus.iss.wellness.model.ChatRequest
import sg.edu.nus.iss.wellness.model.ChatResponse

/**
 * Corresponds to backend /api/chat/... endpoints.
 */
interface ChatApi {

    /** Send a message to the wellness assistant; backend injects 7-day context then calls Ollama */
    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>

    /** Fetch the full conversation history (ascending by time) */
    @GET("api/chat/history")
    suspend fun history(): Response<List<ChatMessage>>
}
