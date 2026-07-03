package sg.edu.nus.iss.wellness.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import sg.edu.nus.iss.wellness.model.Recommendation

/**
 * Corresponds to backend /api/agent/run and /api/recommendations endpoints.
 */
interface AgentApi {

    /** Manually trigger the agentic workflow to generate today's recommendations (calls Ollama, may be slow) */
    @POST("api/agent/run")
    suspend fun run(): Response<Recommendation>

    /** Query recommendation history; from/to are optional */
    @GET("api/recommendations")
    suspend fun recommendations(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<List<Recommendation>>
}
