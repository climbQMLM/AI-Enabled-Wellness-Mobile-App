package sg.edu.nus.iss.wellness.model

/**
 * Corresponds to backend /api/auth/... endpoints.
 */

data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String?
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String?
)

/** Token + user info returned by backend after successful login/register */
data class AuthResponse(
    val token: String,
    val user: UserDto
)
