package sg.edu.nus.iss.wellness.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.auth.dto.AuthResponse;
import sg.edu.nus.iss.wellness.auth.dto.LoginRequest;
import sg.edu.nus.iss.wellness.auth.dto.RegisterRequest;

/**
 *
 * 对应 PROJECT_SPEC.md §4 Auth 部分。这三个接口在 SecurityConfig 里被配置成
 * permitAll，不需要带 JWT 就能访问（注册/登录之前当然没有 token）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * 登出是无状态的：JWT 本身不存储在服务端，所谓"登出"就是客户端把本地存的
     * token 丢掉。这个接口存在只是为了给前端一个明确的"登出动作"调用点，
     * 以及未来如果要加 token 黑名单功能时有地方挂逻辑（见 PROJECT_SPEC.md §4）。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
