package com.example.marketplace.service;

import com.example.marketplace.entity.RefreshToken;
import com.example.marketplace.entity.User;
import com.example.marketplace.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для создания, проверки и удаления refresh-токенов.
 *
 * Ротация: при каждом обновлении старый токен удаляется, новый создаётся.
 * Это делает невозможным повторное использование перехваченного токена.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    // Время жизни refresh-токена в миллисекундах (из application.properties).
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    /**
     * Создаёт новый refresh-токен для пользователя.
     * Перед созданием удаляет все старые токены этого пользователя (один активный токен).
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Один пользователь — один активный refresh-токен.
        refreshTokenRepository.deleteByUser(user);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));

        return refreshTokenRepository.save(token);
    }

    /**
     * Ищет токен по значению и проверяет, что он не истёк.
     * Возвращает Optional.empty(), если токен не найден или просрочен.
     */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findValid(String tokenValue) {
        return refreshTokenRepository.findByToken(tokenValue)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Удаляет конкретный токен (после его использования или при логауте).
     */
    @Transactional
    public void delete(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }

    /**
     * Удаляет все токены пользователя (при логауте со всех устройств).
     */
    @Transactional
    public void deleteAllForUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
