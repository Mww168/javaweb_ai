package com.mww.utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class JwtUtils {

    // 密钥必须 >= 32 个字符（256 位），建议从配置文件读取
    private static final String SECRET_STRING = "mww-secret-key-2026-must-be-32-chars!";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            SECRET_STRING.getBytes(StandardCharsets.UTF_8)
    );

    public static String generateJwt(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                // jti：给每个 token 一个唯一 ID，登出时按它写黑名单（JWT 本身无状态，靠 jti 才能定点失效）
                .setId(UUID.randomUUID().toString())
                .setExpiration(new Date(System.currentTimeMillis() + 3600 * 1000))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256) // 使用 Key 对象签名
                .compact();
    }

    // 解析时也使用同一个 SECRET_KEY
    public static Claims parseJwt(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }
}