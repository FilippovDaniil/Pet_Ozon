package com.example.marketplace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Утилита для работы с JWT-токенами (JSON Web Token).
 *
 * Что такое JWT?
 * Это строка из трёх частей, разделённых точками: Header.Payload.Signature
 *   Header    — алгоритм подписи (HS256).
 *   Payload   — данные (subject = email, issued at, expiration).
 *   Signature — HMAC-подпись, гарантирующая, что токен не подделан.
 *
 * Зачем нужен?
 * После логина клиент получает токен и отправляет его в каждом запросе
 * в заголовке: Authorization: Bearer <token>.
 * Сервер проверяет подпись и доверяет токену — без обращения к БД.
 * Это «stateless» аутентификация: сессии на сервере не хранятся.
 *
 * Секрет и время жизни берутся из application.properties (jwt.secret, jwt.expiration).
 */
@Component
public class JwtUtil {

    // @Value("${...}") — Spring инжектирует значение из application.properties.
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;  // в миллисекундах

    /**
     * Генерирует новый JWT-токен для пользователя.
     * В subject помещается email (UserDetails.getUsername() возвращает email).
     */
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey())
                .compact();
    }

    /** Извлекает email из токена (хранится в поле "sub" — subject). */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Проверяет, что токен:
     *   1. принадлежит данному пользователю (email совпадает),
     *   2. не просрочен.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Универсальный метод извлечения любого поля (claim) из токена.
     * resolver — функция, которая говорит ЧТО извлечь из Claims.
     * Пример: Claims::getSubject, Claims::getExpiration.
     */
    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload());
    }

    /**
     * Создаёт криптографический ключ из секрета (Base64-строка из application.properties).
     * HMAC-SHA ключ используется для подписи и проверки токенов.
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
