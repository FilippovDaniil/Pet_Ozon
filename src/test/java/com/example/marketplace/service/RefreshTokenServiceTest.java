package com.example.marketplace.service;

import com.example.marketplace.entity.RefreshToken;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты RefreshTokenService: создание, поиск и удаление refresh-токенов.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        // @Value не инжектируется через MockitoExtension — подставляем вручную.
        // 7 дней в миллисекундах.
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 604_800_000L);
    }

    private User makeUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setRole(Role.CLIENT);
        u.setBalance(BigDecimal.ZERO);
        return u;
    }

    // ── createRefreshToken ────────────────────────────────────────────────────

    @Test
    void createRefreshToken_savesAndReturnsToken() {
        User user = makeUser();
        // Имитируем save: возвращаем переданный объект как есть.
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken result = refreshTokenService.createRefreshToken(user);

        // Старые токены удалены перед созданием нового.
        verify(refreshTokenRepository).deleteByUser(user);
        // Токен сохранён.
        verify(refreshTokenRepository).save(any());
        // Поля заполнены корректно.
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void createRefreshToken_deletesOldTokensBeforeSaving() {
        User user = makeUser();
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.createRefreshToken(user);

        // deleteByUser должен вызываться ПЕРЕД save — проверяем порядок.
        var inOrder = inOrder(refreshTokenRepository);
        inOrder.verify(refreshTokenRepository).deleteByUser(user);
        inOrder.verify(refreshTokenRepository).save(any());
    }

    // ── findValid ─────────────────────────────────────────────────────────────

    @Test
    void findValid_existingNotExpired_returnsToken() {
        User user = makeUser();
        RefreshToken token = new RefreshToken();
        token.setToken("valid-uuid");
        token.setUser(user);
        // Истекает через 1 час.
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByToken("valid-uuid")).thenReturn(Optional.of(token));

        Optional<RefreshToken> result = refreshTokenService.findValid("valid-uuid");

        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo("valid-uuid");
    }

    @Test
    void findValid_expiredToken_returnsEmpty() {
        RefreshToken token = new RefreshToken();
        token.setToken("expired-uuid");
        // Истёк 1 час назад.
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        when(refreshTokenRepository.findByToken("expired-uuid")).thenReturn(Optional.of(token));

        Optional<RefreshToken> result = refreshTokenService.findValid("expired-uuid");

        assertThat(result).isEmpty();
    }

    @Test
    void findValid_unknownToken_returnsEmpty() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThat(refreshTokenService.findValid("unknown")).isEmpty();
    }

    // ── delete / deleteAllForUser ─────────────────────────────────────────────

    @Test
    void delete_callsRepositoryDelete() {
        RefreshToken token = new RefreshToken();
        token.setToken("some-uuid");

        refreshTokenService.delete(token);

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void deleteAllForUser_callsDeleteByUser() {
        User user = makeUser();

        refreshTokenService.deleteAllForUser(user);

        verify(refreshTokenRepository).deleteByUser(user);
    }
}
