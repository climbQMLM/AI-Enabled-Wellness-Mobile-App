package sg.edu.nus.iss.wellness.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 *
 * Encrypted local storage for JWT token and user info.
 *
 * Uses EncryptedSharedPreferences (AndroidX Security):
 *   - Values encrypted with AES-256-GCM, keys encrypted with AES-256-SIV
 *   - MasterKey lives in Android Keystore; deleted automatically when the app is uninstalled
 *   - More secure than plain SharedPreferences — plaintext token is inaccessible even on rooted devices
 *
 * Why not a database?
 *   The token is just a string — Room is overkill; EncryptedSharedPreferences is the recommended lightweight option.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        // MasterKey uses AES256_GCM spec, stored in Android Keystore
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "wellness_session",        // encrypted file name
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Save the JWT token received after a successful login */
    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()

    /** Read the token; returns null if not logged in */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Save the display name (used in Dashboard greeting) */
    fun saveDisplayName(name: String?) = prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)

    /** Save the user email */
    fun saveEmail(email: String) = prefs.edit().putString(KEY_EMAIL, email).apply()
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Whether logged in — having a token is sufficient; expiry is handled via server 401 responses */
    fun isLoggedIn(): Boolean = getToken() != null

    /** Logout: clear all stored credentials */
    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
    }
}
