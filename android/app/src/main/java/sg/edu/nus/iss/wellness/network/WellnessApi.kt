package sg.edu.nus.iss.wellness.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import sg.edu.nus.iss.wellness.model.ImportResponse
import sg.edu.nus.iss.wellness.model.WellnessCreateRequest
import sg.edu.nus.iss.wellness.model.WellnessLog
import sg.edu.nus.iss.wellness.model.WellnessPatchRequest

/**
 * Corresponds to backend /api/wellness/... endpoints.
 */
interface WellnessApi {

    /** Fetch list; from/to are optional (backend defaults to last 30 days) */
    @GET("api/wellness")
    suspend fun list(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<List<WellnessLog>>

    /** Manually create a wellness log entry */
    @POST("api/wellness")
    suspend fun create(@Body request: WellnessCreateRequest): Response<WellnessLog>

    /** Partial update (PATCH semantics — null fields are left unchanged) */
    @PUT("api/wellness/{id}")
    suspend fun patch(
        @Path("id") id: Long,
        @Body request: WellnessPatchRequest
    ): Response<WellnessLog>

    /** Delete a log entry */
    @DELETE("api/wellness/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Void>

    /**
     * Upload CSV/ZIP files to import RingConn data.
     * Multipart upload; part name must be "files" (matching backend @RequestParam("files")).
     */
    @Multipart
    @POST("api/wellness/import")
    suspend fun import(@Part files: List<MultipartBody.Part>): Response<ImportResponse>
}
