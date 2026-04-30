package com.example.marketplace.service;

import com.example.marketplace.dto.request.UpdateProfileRequest;
import com.example.marketplace.entity.Cart;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.CartRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Бизнес-логика для работы с пользователями.
 *
 * @Service — Spring создаёт один экземпляр (singleton-бин) и внедряет его
 *            везде, где он нужен (контроллеры, другие сервисы).
 *
 * @RequiredArgsConstructor (Lombok) — генерирует конструктор со всеми final-полями.
 * Spring использует этот конструктор для Dependency Injection (внедрения зависимостей).
 * Это предпочтительнее, чем @Autowired на поле — легче тестировать.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;

    public User getById(Long id) {
        // orElseThrow — если пользователь не найден, выбрасываем нашу кастомную
        // ResourceNotFoundException, которую GlobalExceptionHandler поймает и вернёт 404.
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /**
     * Регистрация нового покупателя.
     *
     * @Transactional — операция выполняется в одной транзакции.
     * Если что-то пойдёт не так после save(user) но до save(cart),
     * вся транзакция откатится — не будет пользователя без корзины.
     */
    @Transactional
    public User registerClient(String email, String password, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        User user = new User();
        user.setEmail(email);
        // Никогда не сохраняем открытый пароль. BCrypt создаёт хэш с солью.
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(Role.CLIENT);
        user.setBalance(BigDecimal.ZERO);
        User saved = userRepository.save(user);

        // Корзина создаётся сразу при регистрации — у каждого клиента своя корзина.
        Cart cart = new Cart();
        cart.setUser(saved);
        cartRepository.save(cart);

        log.info("ACTION=REGISTER userId={} email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    /**
     * Обновление профиля пользователя (имя, адрес, название магазина).
     * Null-поля в запросе пропускаются — обновляются только переданные данные.
     * Это паттерн «частичного обновления» (PATCH-семантика).
     */
    @Transactional
    public User updateProfile(Long id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        if (request.getShopName() != null) user.setShopName(request.getShopName());
        log.info("ACTION=UPDATE_PROFILE userId={}", id);
        return userRepository.save(user);
    }
}
