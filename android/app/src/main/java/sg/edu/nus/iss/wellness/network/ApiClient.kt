package sg.edu.nus.iss.wellness.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sg.edu.nus.iss.wellness.data.SessionManager

/**
 *
 * Retrofit singleton factory.
 *
 * BASE_URL notes:
 *   - In the Android emulator 10.0.2.2 maps to the host machine's localhost, so backend localhost:8080
 *     must be written as http://10.0.2.2:8080/ inside the emulator
 *   - For real-device debugging, replace with the host LAN IP, e.g. http://192.168.1.x:8080/
 *
 * AuthInterceptor：
 *   OkHttp interceptor: automatically attaches the JWT token from SessionManager to every request,
 *   as an Authorization: Bearer <token> header.
 *   This way no API call needs to manually set the header.
 */
object ApiClient {

    // Emulator → host localhost via 10.0.2.2; real device → host LAN IP
    private const val BASE_URL = "http://10.0.2.2:8080/"

    /** Lazily initialized; built on first use */
    private var retrofit: Retrofit? = null

    /**
     * Get a Retrofit instance with the JWT auth interceptor attached.
     * [sessionManager] used to read the stored JWT token.
     */
    fun getInstance(sessionManager: SessionManager): Retrofit {
        if (retrofit == null) {
            // Request logger interceptor (debug builds only — shows full request/response body)
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Auth interceptor: attaches Authorization header to every request
            val authInterceptor = okhttp3.Interceptor { chain ->
                val token = sessionManager.getToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Ollama inference may be slow
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    /**
     * Reset the Retrofit instance (called on logout, ensures next login uses a fresh token).
     */
    fun reset() {
        retrofit = null
    }
}
