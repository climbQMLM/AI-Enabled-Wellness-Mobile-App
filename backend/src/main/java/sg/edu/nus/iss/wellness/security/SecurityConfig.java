package sg.edu.nus.iss.wellness.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 *
 * 整个后端的安全策略集中在这一个类里：
 *   - 关闭 CSRF（纯 REST API + JWT，不用 session/cookie，没有 CSRF 攻击面）
 *   - 关闭 Spring Security 默认的 session（STATELESS），认证状态完全由
 *     每次请求带的 JWT 决定，服务端不保存登录状态
 *   - /api/auth/** 放行（注册登录本身当然不需要先登录）
 *   - 其余 /api/** 必须带有效 JWT
 *   - 把 JwtAuthFilter 插在 Spring Security 默认的用户名密码过滤器之前，
 *     这样请求先被我们的过滤器解析 JWT、填好 SecurityContext，
 *     后面的鉴权规则才能正确判断"这个请求有没有登录"
 *   - CORS：PROJECT_SPEC.md §0 要求后端必须开 CORS，方便 Android/Postman 联调
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${wellness.cors.allowed-origins}")
    private String allowedOrigins;

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // "*" 时用 setAllowedOriginPatterns 而不是 setAllowedOrigins，
        // 否则 allowCredentials(true) 和通配符 origin 同时存在 Spring 会直接报错
        config.setAllowedOriginPatterns(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** BCrypt 是 Spring Security 推荐的密码哈希算法，对应 users.password_hash 字段 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 暴露 AuthenticationManager 给 AuthService 用，
     * 登录时调用 authenticationManager.authenticate(...) 来校验邮箱+密码。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
