package sg.edu.nus.iss.wellness.auth.dto;

/**
 *
 * register / login 接口的响应体：{"token": "...", "user": {...}}
 * 对应 PROJECT_SPEC.md §4。
 */
public record AuthResponse(String token, UserDto user) {
}
