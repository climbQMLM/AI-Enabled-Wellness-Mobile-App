package sg.edu.nus.iss.wellness.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 *
 * POST /api/auth/login 的请求体。这里不加 @Email/@Size 校验——
 * 登录失败的原因应该统一回答"invalid email or password"（见 AuthService），
 * 不应该让攻击者从"邮箱格式都不对"这种校验错误里探测出账号是否存在。
 */
public record LoginRequest(

        @NotBlank(message = "must not be blank")
        String email,

        @NotBlank(message = "must not be blank")
        String password
) {
}
