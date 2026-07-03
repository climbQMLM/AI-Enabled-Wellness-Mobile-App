package sg.edu.nus.iss.wellness.auth.dto;

import sg.edu.nus.iss.wellness.user.User;

/**
 *
 * 返回给前端的用户信息，特意排除 passwordHash——绝对不能把密码哈希
 * 序列化进 API 响应，哪怕只是哈希值也不行。
 */
public record UserDto(Long id, String email, String displayName) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
