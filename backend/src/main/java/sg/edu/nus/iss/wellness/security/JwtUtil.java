package sg.edu.nus.iss.wellness.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 *
 * 负责 JWT 的生成和解析校验。整个项目只有这一个地方碰 JWT 的底层细节，
 * 其他代码（JwtAuthFilter / AuthService）只调用这里暴露的方法，
 * 不要在别处直接用 io.jsonwebtoken 的 API。
 *
 * Token payload 里只放 userId 和 email 两个 claim，不放密码等敏感信息——
 * JWT 的 payload 只是 base64 编码，不是加密，谁都能解出来看。
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(JwtProperties jwtProperties) {
        // jjwt 要求签名密钥至少 256 bit（HS256），application.yml 里的 secret
        // 是 base64 编码的随机串，这里解码还原成原始字节再喂给 Keys.hmacShaKeyFor
        byte[] keyBytes = java.util.Base64.getDecoder().decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = jwtProperties.getExpirationMs();
    }

    /** 登录/注册成功后调用，生成一个 7 天有效期的 token */
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))   // sub claim 存 userId，鉴权时直接拿来查用户
                .claim("email", email)             // 顺带存 email，方便日志/调试，不用每次都查库
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析并校验 token，失败（过期/签名不对/格式错）统一抛 JwtException，
     * 由调用方（JwtAuthFilter）决定怎么处理（一律视为未登录）。
     */
    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 过期、签名不对、token 格式不对，统一当作"无效 token"处理，
            // 不区分具体原因返回给客户端（避免泄露细节给攻击者）
            return false;
        }
    }
}
