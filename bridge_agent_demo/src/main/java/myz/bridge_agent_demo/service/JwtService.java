package myz.bridge_agent_demo.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 手写 JWT HS256。不上 jjwt，避开 Spring Boot 4 / Jackson 3 的依赖冲突。
 * 载荷只放 userId / username / ver / exp，不放密码、密钥、昵称。
 * HMAC 密钥只放本机 application-local.properties，不进 Git。
 */
@Slf4j
@Service
public class JwtService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours:8}")
    private int expireHours;

    @PostConstruct
    void checkSecret() {
        if (!StringUtils.hasText(secret) || secret.trim().length() < 32) {
            throw new IllegalStateException("app.jwt.secret 至少 32 个字符，只放本机 application-local.properties，不要提交");
        }
        if (expireHours <= 0) {
            throw new IllegalStateException("app.jwt.expire-hours 必须大于 0");
        }
    }

    /**
     * 签发一张票。{@code ver} 写入时取用户表当前 {@code token_version}。
     *
     * @return 完整 JWT 字符串
     */
    public String issue(long userId, String username, int ver) {
        long exp = Instant.now().getEpochSecond() + expireHours * 3600L;
        Claims claims = new Claims();
        claims.setUserId(userId);
        claims.setUsername(username);
        claims.setVer(ver);
        claims.setExp(exp);
        String header = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(JSON.writeValueAsBytes(claims));
        String signingInput = header + "." + payload;
        return signingInput + "." + base64Url(hmac(signingInput));
    }

    /**
     * 验签并解析。签名不对、过期、缺字段都返回 null，由拦截器统一 401，不向浏览器区分原因。
     */
    public Claims parse(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            return null;
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected;
        byte[] actual;
        try {
            expected = hmac(signingInput);
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (RuntimeException e) {
            return null;
        }
        if (expected.length != actual.length || !MessageDigest.isEqual(expected, actual)) {
            return null;
        }
        Claims claims;
        try {
            claims = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), Claims.class);
        } catch (RuntimeException e) {
            return null;
        }
        if (claims == null
                || claims.getUserId() == null
                || !StringUtils.hasText(claims.getUsername())
                || claims.getVer() == null
                || claims.getExp() == null) {
            return null;
        }
        if (Instant.now().getEpochSecond() >= claims.getExp()) {
            return null;
        }
        return claims;
    }

    public int expireHours() {
        return expireHours;
    }

    public long expireAtEpochSecond(String token) {
        Claims claims = parse(token);
        return claims == null ? 0L : claims.getExp();
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HS256 不可用", e);
        }
    }

    private static String base64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** JWT 载荷。字段名与规划锁定的一致。 */
    @Data
    public static class Claims {
        private Long userId;
        private String username;
        /** 对应 sys_user.token_version */
        private Integer ver;
        /** Unix 秒 */
        private Long exp;
    }
}
