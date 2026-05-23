package com.example.marketplace.repository;

import com.example.marketplace.entity.RefreshToken;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для refresh-токенов.
 *
 * deleteByUser — удаляет все токены пользователя при логауте или смене пароля.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);
}
