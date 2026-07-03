package sg.edu.nus.iss.wellness.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.wellness.auth.dto.AuthResponse;
import sg.edu.nus.iss.wellness.auth.dto.LoginRequest;
import sg.edu.nus.iss.wellness.auth.dto.RegisterRequest;
import sg.edu.nus.iss.wellness.auth.dto.UserDto;
import sg.edu.nus.iss.wellness.common.ApiException;
import sg.edu.nus.iss.wellness.security.JwtUtil;
import sg.edu.nus.iss.wellness.user.User;
import sg.edu.nus.iss.wellness.user.UserRepository;

/**
 *
 * 注册/登录的业务逻辑。Controller 层只负责接收请求和返回响应，
 * 所有判断（邮箱是否已存在、密码是否正确）都放在这里。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            // 用 409 而不是 400：这不是请求格式错误，是和已有资源冲突，
            // 跟 PROJECT_SPEC.md §4 里 POST /api/wellness 重复日期返回 409 是同一个语义
            throw ApiException.conflict("email already registered");
        }

        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hash, request.displayName());
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }

    public AuthResponse login(LoginRequest request) {
        // 交给 Spring Security 的 AuthenticationManager 处理"查用户 + 校验密码"，
        // 密码不对会抛 BadCredentialsException，由 GlobalExceptionHandler 统一转 401。
        // 这样不管是"邮箱不存在"还是"密码错误"，前端收到的错误信息都一样，
        // 不会暴露"这个邮箱到底存不存在"。
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> ApiException.unauthorized("invalid email or password"));

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }
}
