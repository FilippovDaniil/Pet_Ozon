package com.example.marketplace.service;

import com.example.marketplace.dto.response.CardBindingResponse;
import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.CardBindingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты сервиса привязанных карт (Mockito, без Spring-контекста).
 * Проверяем сохранение привязок из ответа банка (production bindingInfo и UAT cardAuthInfo),
 * управление дефолтной картой, удаление и форматирование срока действия.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock CardBindingRepository cardRepo;
    @InjectMocks CardService cardService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("client@test.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private CardBinding makeCard(Long id, User user, String bindingId, boolean isDefault) {
        CardBinding c = new CardBinding();
        c.setId(id);
        c.setUser(user);
        c.setBindingId(bindingId);
        c.setMaskedPan("411111**1111");
        c.setExpiry("122026");
        c.setDefault(isDefault);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    // Вспомогательный метод: создаёт JSON-ответ Альфа Банка с bindingInfo.
    private JsonNode makeStatusWithBinding(String bindingId, String pan, String expiry) throws Exception {
        String json = """
                {
                    "orderStatus": 2,
                    "bindingInfo": {
                        "bindingId": "%s",
                        "label": "%s",
                        "expiryDate": "%s"
                    }
                }
                """.formatted(bindingId, pan, expiry);
        return objectMapper.readTree(json);
    }

    // ── saveFromStatusResponse ────────────────────────────────────────────────

    @Test
    void saveFromStatusResponse_newCard_savesAsDefault_whenFirstCard() throws Exception {
        User user = makeUser(1L);
        JsonNode status = makeStatusWithBinding("binding-001", "411111**1111", "122026");

        when(cardRepo.existsByBindingId("binding-001")).thenReturn(false);
        when(cardRepo.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.empty()); // первая карта
        when(cardRepo.save(any(CardBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        cardService.saveFromStatusResponse(user, status);

        // Первая карта должна стать дефолтной
        verify(cardRepo).save(argThat(c -> c.isDefault() && "binding-001".equals(c.getBindingId())));
    }

    @Test
    void saveFromStatusResponse_newCard_notDefault_whenAnotherDefaultExists() throws Exception {
        User user = makeUser(1L);
        JsonNode status = makeStatusWithBinding("binding-002", "555555**4444", "062027");
        CardBinding existingDefault = makeCard(10L, user, "binding-001", true);

        when(cardRepo.existsByBindingId("binding-002")).thenReturn(false);
        when(cardRepo.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(existingDefault));
        when(cardRepo.save(any(CardBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        cardService.saveFromStatusResponse(user, status);

        // Вторая карта НЕ дефолтная
        verify(cardRepo).save(argThat(c -> !c.isDefault() && "binding-002".equals(c.getBindingId())));
    }

    // Карта уже сохранена → save не вызывается (нет дублей).
    @Test
    void saveFromStatusResponse_existingCard_skips() throws Exception {
        User user = makeUser(1L);
        JsonNode status = makeStatusWithBinding("binding-001", "411111**1111", "122026");

        when(cardRepo.existsByBindingId("binding-001")).thenReturn(true); // уже есть

        cardService.saveFromStatusResponse(user, status);

        verify(cardRepo, never()).save(any());
    }

    // Нет bindingId в ответе → сохранять нечего.
    @Test
    void saveFromStatusResponse_noBindingId_skips() throws Exception {
        User user = makeUser(1L);
        JsonNode status = objectMapper.readTree("{\"orderStatus\":2}"); // нет bindingInfo

        cardService.saveFromStatusResponse(user, status);

        verify(cardRepo, never()).save(any());
    }

    // ── saveAfterPayment (авто-привязка карты после оплаты) ─────────────────────

    @Test
    void saveAfterPayment_withBindingInfo_savesRealCard() throws Exception {
        User user = makeUser(1L);
        JsonNode status = makeStatusWithBinding("binding-777", "411111**1111", "122026");

        when(cardRepo.existsByBindingId("binding-777")).thenReturn(false);
        when(cardRepo.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.empty());
        when(cardRepo.save(any(CardBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        cardService.saveAfterPayment(user, "alfa-1", status);

        // Production-путь: bindingInfo есть → сохраняем реальный bindingId как дефолтную карту.
        verify(cardRepo).save(argThat(c -> "binding-777".equals(c.getBindingId()) && c.isDefault()));
    }

    @Test
    void saveAfterPayment_noBindingInfo_doesNotSaveSyntheticCard() throws Exception {
        User user = makeUser(1L);
        // bindingInfo пуст (клиент не поставил «Сохранить карту») — связки нет.
        // cardAuthInfo есть, но синтетическую карту из него мы больше НЕ создаём.
        JsonNode status = objectMapper.readTree("""
                {"orderStatus":2,"cardAuthInfo":{"maskedPan":"411111**1111","expiration":"203412"}}
                """);

        cardService.saveAfterPayment(user, "abcdef0123456789aaaa", status);

        // Карта не сохраняется: списываемой связки нет, синтетика вводит в заблуждение.
        verify(cardRepo, never()).save(any());
    }

    // ── getCards ──────────────────────────────────────────────────────────────

    // Список карт возвращается в порядке репозитория (дефолтная первой).
    @Test
    void getCards_returnsSortedList() {
        User user = makeUser(1L);
        CardBinding c1 = makeCard(1L, user, "b1", true);
        CardBinding c2 = makeCard(2L, user, "b2", false);

        when(cardRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(user)).thenReturn(List.of(c1, c2));

        List<CardBindingResponse> result = cardService.getCards(user);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isDefault()).isTrue();
        assertThat(result.get(1).isDefault()).isFalse();
    }

    // ── setDefault ────────────────────────────────────────────────────────────

    // Установка дефолтной: сначала сброс флага у всех карт, затем флаг на выбранной.
    @Test
    void setDefault_changesDefaultCard() {
        User user = makeUser(1L);
        CardBinding card = makeCard(5L, user, "b5", false);

        when(cardRepo.findById(5L)).thenReturn(Optional.of(card));
        when(cardRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cardService.setDefault(5L, user);

        verify(cardRepo).clearDefaultForUser(user);
        verify(cardRepo).save(argThat(CardBinding::isDefault));
    }

    // Чужая карта → IllegalStateException «Нет доступа».
    @Test
    void setDefault_wrongOwner_throwsException() {
        User owner = makeUser(1L);
        User other = makeUser(99L);
        CardBinding card = makeCard(5L, owner, "b5", false);

        when(cardRepo.findById(5L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.setDefault(5L, other))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нет доступа");
    }

    // Несуществующая карта → ResourceNotFoundException.
    @Test
    void setDefault_cardNotFound_throwsResourceNotFoundException() {
        User user = makeUser(1L);
        when(cardRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.setDefault(99L, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    // Удаление своей карты → вызов repo.delete.
    @Test
    void delete_ownCard_succeeds() {
        User user = makeUser(1L);
        CardBinding card = makeCard(5L, user, "b5", false);

        when(cardRepo.findById(5L)).thenReturn(Optional.of(card));

        cardService.delete(5L, user);

        verify(cardRepo).delete(card);
    }

    // Удаление чужой карты → исключение, repo.delete не вызывается.
    @Test
    void delete_otherUsersCard_throwsException() {
        User owner = makeUser(1L);
        User other = makeUser(99L);
        CardBinding card = makeCard(5L, owner, "b5", false);

        when(cardRepo.findById(5L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.delete(5L, other))
                .isInstanceOf(IllegalStateException.class);
        verify(cardRepo, never()).delete(any());
    }

    // ── toResponse / getExpiryFormatted ───────────────────────────────────────

    @Test
    void toResponse_formatsExpiryCorrectly() {
        User user = makeUser(1L);
        CardBinding card = makeCard(1L, user, "b1", true);
        card.setExpiry("122026");

        CardBindingResponse r = cardService.toResponse(card);

        // MMYYYY → "12/2026"
        assertThat(r.expiry()).isEqualTo("12/2026");
        assertThat(r.maskedPan()).isEqualTo("411111**1111");
        assertThat(r.isDefault()).isTrue();
    }
}
