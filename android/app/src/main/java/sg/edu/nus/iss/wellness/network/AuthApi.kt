package sg.edu.nus.iss.wellness.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import sg.edu.nus.iss.wellness.model.AuthResponse
import sg.edu.nus.iss.wellness.model.LoginRequest
import sg.edu.nus.iss.wellness.model.RegisterRequest

/**
 * Corresponds to backend /api/auth/... endpoints.
 * Wrapped in Response<T> so callers can inspect the HTTP status code and errorBody.
 */
interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
}
