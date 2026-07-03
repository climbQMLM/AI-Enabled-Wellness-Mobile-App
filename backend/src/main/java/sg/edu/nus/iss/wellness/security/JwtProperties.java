package sg.edu.nus.iss.wellness.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 * 把 application.yml 里 wellness.jwt.* 配置绑定成一个类型安全的对象，
 * 比到处用 @Value("${wellness.jwt.secret}") 字符串硬编码更不容易写错 key。
 */
@ConfigurationProperties(prefix = "wellness.jwt")
public class JwtProperties {

    /** HS256 签名密钥（base64 字符串），见 application.yml 注释 */
    private String secret;

    /** token 有效期，单位毫秒。当前配置为 7 天 */
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
