package com.example.marketplace.service;

import com.example.marketplace.dto.response.CardBindingResponse;
import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.User;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.CardBindingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardBindingRepository cardRepo;

    /**
     * Сохраняет привязку карты из ответа getOrderStatusExtended.
     * Если карта уже есть в БД — пропускает.
     * Если это первая карта пользователя — ставит её как дефолтную.
     */
    @Transactional
    public void saveFromStatusResponse(User user, JsonNode statusNode) {
        JsonNode bindingInfo = statusNode.path("bindingInfo");
        String bindingId = bindingInfo.path("bindingId").asText(null);
        if (bindingId == null || bindingId.isBlank()) return;
        if (cardRepo.existsByBindingId(bindingId)) return;

        String maskedPan = extractMaskedPan(statusNode);
        String expiry    = extractExpiry(statusNode);

        boolean isFirst = cardRepo.findByUserAndIsDefaultTrue(user).isEmpty();

        CardBinding card = new CardBinding();
        card.setUser(user);
        card.setBindingId(bindingId);
        card.setMaskedPan(maskedPan);
        card.setExpiry(expiry);
        card.setDefault(isFirst);
        cardRepo.save(card);

        log.info("ACTION=CARD_SAVED userId={} maskedPan={} isDefault={}", user.getId(), maskedPan, isFirst);
    }

    public List<CardBindingResponse> getCards(User user) {
        return cardRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(user)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void setDefault(Long cardId, User user) {
        CardBinding card = cardRepo.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Карта не найдена: " + cardId));
        if (!card.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к карте #" + cardId);
        }
        cardRepo.clearDefaultForUser(user);
        card.setDefault(true);
        cardRepo.save(card);
    }

    @Transactional
    public void delete(Long cardId, User user) {
        CardBinding card = cardRepo.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Карта не найдена: " + cardId));
        if (!card.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к карте #" + cardId);
        }
        cardRepo.delete(card);
    }

    public Optional<CardBinding> getDefault(User user) {
        return cardRepo.findByUserAndIsDefaultTrue(user);
    }

    private String extractMaskedPan(JsonNode statusNode) {
        // Сначала ищем в bindingInfo.label, потом в cardAuthInfo.pan
        String label = statusNode.path("bindingInfo").path("label").asText(null);
        if (label != null && !label.isBlank()) return label;
        return statusNode.path("cardAuthInfo").path("pan").asText("****");
    }

    private String extractExpiry(JsonNode statusNode) {
        // expiration в форматах MMYYYY или YYYYMM — Альфа Банк возвращает MMYYYY
        String exp = statusNode.path("bindingInfo").path("expiryDate").asText(null);
        if (exp == null || exp.isBlank()) {
            exp = statusNode.path("cardAuthInfo").path("expiration").asText(null);
        }
        return exp;
    }

    public CardBindingResponse toResponse(CardBinding c) {
        return new CardBindingResponse(
                c.getId(),
                c.getMaskedPan(),
                c.getExpiryFormatted(),
                c.isDefault(),
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : null
        );
    }
}
