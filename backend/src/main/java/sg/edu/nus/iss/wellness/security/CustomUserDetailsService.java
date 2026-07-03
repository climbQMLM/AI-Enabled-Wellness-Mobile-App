package sg.edu.nus.iss.wellness.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.wellness.user.UserRepository;

import java.util.List;

/**
 *
 * Spring Security 的 AuthenticationManager（登录时用）依赖这个接口
 * 按"用户名"（这里就是 email）查出用户信息去和提交的密码比对。
 *
 * 只在登录(AuthService.login)这一个场景会用到这个类——日常请求的鉴权
 * 走的是 JwtAuthFilter 直接用 userId 查库，不经过这里。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("no user with email " + email));

        // 用 Spring Security 自带的 User 实现包一层，username=email，
        // password 用的是数据库里已经哈希过的 password_hash（不是明文），
        // DaoAuthenticationProvider 内部会自动用 PasswordEncoder.matches()
        // 拿用户提交的明文密码和这里的哈希比对，不需要我们手动调用。
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of())
                .build();
    }
}
