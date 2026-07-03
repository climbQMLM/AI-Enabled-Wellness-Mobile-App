package sg.edu.nus.iss.wellness.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.edu.nus.iss.wellness.user.User;
import sg.edu.nus.iss.wellness.user.UserRepository;

import java.io.IOException;
import java.util.List;

/**
 *
 * 每个请求都会经过这个过滤器一次（OncePerRequestFilter 保证不会重复执行）。
 * 逻辑很简单：
 *   1. 取 Authorization header，没有 "Bearer xxx" 格式就直接放行
 *      （放行不代表请求会成功——后面 SecurityConfig 里配置的鉴权规则会拦截
 *      未认证的请求访问受保护接口，这里只负责"如果有 token 就尝试认证"）
 *   2. token 无效（过期/篡改）也直接放行，同样交给后面的鉴权规则去 401
 *   3. token 有效 → 查出对应 User → 写入 SecurityContext，
 *      后续 Controller 里可以用 @AuthenticationPrincipal 或
 *      SecurityContextHolder 拿到当前登录用户
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (!jwtUtil.isValid(token)) {
            // 无效/过期 token：不报错，就当没登录处理，交给 SecurityConfig
            // 的鉴权规则去判断这个接口是不是必须登录
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = jwtUtil.extractUserId(token);

        // 已经认证过（比如同一请求被过滤器链调用了两次）就不用重复查库
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findById(userId).ifPresent(user -> authenticate(request, user));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, User user) {
        // 第二个参数(credentials)传 null：JWT 认证不需要再带密码，
        // 能解出有效 token 本身就已经证明了身份。
        // 第三个参数(authorities)这里项目没有角色/权限分级，给个空列表即可。
        var authToken = new UsernamePasswordAuthenticationToken(user, null, List.of());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
