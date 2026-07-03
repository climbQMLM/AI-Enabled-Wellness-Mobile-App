package sg.edu.nus.iss.wellness.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 *
 * POST /api/auth/register 的请求体。@Valid 注解在 Controller 方法参数上
 * 触发这里的校验，失败会被 GlobalExceptionHandler 转成 400 + {"error": "..."}。
 */
public record RegisterRequest(

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 6, message = "must be at least 6 characters")
        String password,

        // 昵称允许不填，没有 @NotBlank
        String displayName
) {
}
