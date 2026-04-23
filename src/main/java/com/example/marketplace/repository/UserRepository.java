package com.example.marketplace.repository;

import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с пользователями в БД.
 *
 * Spring Data JPA: достаточно объявить интерфейс, расширяющий JpaRepository<Сущность, ТипId>.
 * Spring автоматически создаст реализацию со стандартными методами:
 *   findById, findAll, save, delete, count, existsById и т.д.
 *
 * Имена методов — это «язык запросов» Spring Data:
 *   findByEmail → SELECT * FROM users WHERE email = ?
 *   existsByEmail → SELECT COUNT(*) > 0 FROM users WHERE email = ?
 * Никакого SQL или JPQL писать не нужно — Spring генерирует запрос по имени метода.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // Optional<> — безопасная альтернатива null: либо найденный User, либо Optional.empty().
    Optional<User> findByEmail(String email);

    // Используется в UserService.registerClient() для проверки уникальности email.
    boolean existsByEmail(String email);
}
