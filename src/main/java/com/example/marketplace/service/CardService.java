package com.example.marketplace.service;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.CardBindingResponse;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.CardBindRequest;
import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.User;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.payment.AlfaBankGatewayClient;
import com.example.marketplace.repository.CardBindRequestRepository;
import com.example.marketplace.repository.CardBindingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Управление привязанными картами пользователей и их сохранение после оплаты.
 *
 * Отвечает за весь жизненный цикл карты:
 *   • привязку без покупки (initiateBinding/confirmBinding — холд 1₽ → deposit → refund);
 *   • авто-сохранение после оплаты заказа (saveAfterPayment);
 *   • резолв реального bindingId для тихих списаний (resolveChargeableBindingId);
 *   • CRUD карт в личном кабинете (список, выбор дефолтной, удаление).
 *
 * Из-за различий production/UAT источник данных карты ищется по цепочке
 * bindingInfo → cardAuthInfo → getBindings (см. saveAfterPayment / confirmBinding).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardBindingRepository    cardRepo;
    private final CardBindRequestRepository bindRequestRepo;
    private final AlfaBankGatewayClient    gateway;
    private final AlfaBankProperties       props;

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

    /**
     * Привязывает карту клиента после успешной оплаты заказа
     * (полная оплата или первый взнос BNPL).
     *
     * Источники bindingId в порядке надёжности:
     *   1. bindingInfo  — production: реальный bindingId присутствует в ответе.
     *   2. cardAuthInfo — UAT: реальная карта именно ЭТОГО заказа (синтетический CARDAUTH-id).
     *   3. getBindings  — последний резерв (список привязок клиента).
     *
     * Новая карта добавляется в ЛК; уже сохранённая связка — пропускается.
     * Метод не бросает исключений: сбой привязки не должен ломать подтверждение оплаты.
     */
    @Transactional
    public void saveAfterPayment(User user, String alfaOrderId, JsonNode statusNode) {
        try {
            String bindingId = statusNode.path("bindingInfo").path("bindingId").asText(null);
            if (bindingId != null && !bindingId.isBlank()) {
                saveFromStatusResponse(user, statusNode);          // production: bindingInfo
                return;
            }
            // UAT: bindingInfo пуст — берём реальную карту из cardAuthInfo этого заказа.
            if (saveFromCardAuthInfo(user, alfaOrderId, statusNode)) {
                return;
            }
            // Последний резерв: список привязок клиента.
            saveFromGetBindings(user, "user-" + user.getId());
        } catch (Exception e) {
            log.warn("ACTION=CARD_SAVE_AFTER_PAYMENT_FAILED userId={} orderId={}: {}",
                    user.getId(), alfaOrderId, e.getMessage());
        }
    }

    /** Карты пользователя для личного кабинета (дефолтная — первой). */
    public List<CardBindingResponse> getCards(User user) {
        return cardRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(user)
                .stream().map(this::toResponse).toList();
    }

    /** Делает карту дефолтной: снимает флаг со всех карт владельца и ставит на выбранную. */
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

    /** Удаляет карту пользователя (с проверкой владельца). */
    @Transactional
    public void delete(Long cardId, User user) {
        CardBinding card = cardRepo.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Карта не найдена: " + cardId));
        if (!card.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к карте #" + cardId);
        }
        cardRepo.delete(card);
    }

    /** Дефолтная карта пользователя — с неё идут авто-списания и админ-оплата. */
    public Optional<CardBinding> getDefault(User user) {
        return cardRepo.findByUserAndIsDefaultTrue(user);
    }

    /**
     * Возвращает реальный bindingId для тихого списания по карте.
     * Синтетический "CARDAUTH-" в шлюзе не существует → берём настоящую связку через getBindings.do.
     * null — если списываемой связки нет.
     */
    public String resolveChargeableBindingId(CardBinding card, String clientId) {
        String stored = card.getBindingId();
        if (stored != null && !stored.startsWith("CARDAUTH-")) {
            return stored;  // реальный bindingId (production)
        }
        try {
            JsonNode resp = gateway.getBindings(clientId);
            for (JsonNode b : resp.path("bindings")) {
                String id = b.path("bindingId").asText(null);
                if (id != null && !id.isBlank()) return id;
            }
        } catch (Exception e) {
            log.warn("getBindings failed clientId={}: {}", clientId, e.getMessage());
        }
        return null;
    }

    // ── Привязка карты без оплаты ─────────────────────────────────────────────

    /**
     * Инициирует привязку карты: регистрирует платёж в 1₽, возвращает formUrl.
     * После прохождения формы банк редиректит на card-bind-callback,
     * где платёж отменяется через reverse.do, а карта сохраняется.
     */
    @Transactional
    public PaymentInitResponse initiateBinding(User user) {
        String orderNumber = "BIND-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // registerPreAuth (двухстадийный): холдируем 1₽.
        // После того как клиент платит, мы сами вызываем deposit.do →
        // Альфа Банк гарантированно возвращает bindingInfo без UI-тоггла "Сохранить карту".
        // Затем сразу refund.do — возвращаем 1₽ клиенту.
        String clientId = "user-" + user.getId();
        JsonNode response = gateway.registerPreAuthForBinding(
                orderNumber,
                100L,
                props.getCardBindReturnUrl(),
                props.getFailUrl(),
                clientId
        );
        String alfaOrderId = response.path("orderId").asText();
        String formUrl     = response.path("formUrl").asText();

        CardBindRequest req = new CardBindRequest();
        req.setUser(user);
        req.setOrderNumber(orderNumber);
        req.setAlfaOrderId(alfaOrderId);
        bindRequestRepo.save(req);

        log.info("ACTION=CARD_BIND_INITIATED userId={} orderNumber={}", user.getId(), orderNumber);
        return new PaymentInitResponse(formUrl, alfaOrderId, null);
    }

    /**
     * Подтверждает привязку карты после callback от банка.
     * Сохраняет CarBinding, отменяет списание 1₽ через reverse.do.
     * Возвращает "completed" / "failed" / "pending".
     */
    @Transactional
    public String confirmBinding(String alfaOrderId) {
        CardBindRequest req = bindRequestRepo.findByAlfaOrderId(alfaOrderId)
                .orElseThrow(() -> new IllegalArgumentException("not a card bind request: " + alfaOrderId));

        if (!"PENDING".equals(req.getStatus())) {
            return req.getStatus().toLowerCase();
        }

        JsonNode status  = gateway.getOrderStatusExtended(alfaOrderId);
        int orderStatus  = status.path("orderStatus").asInt(-1);

        // orderStatus=1 (APPROVED) — pre-auth прошёл, деньги захолдированы.
        // Делаем deposit.do → после него bindingInfo гарантированно появляется в ответе.
        if (orderStatus == 1) {
            log.info("ACTION=CARD_BIND_DEPOSIT orderId={}", alfaOrderId);
            try {
                gateway.deposit(alfaOrderId, 100L); // подтверждаем 1₽
            } catch (Exception e) {
                log.error("Deposit failed for card bind orderId={}: {}", alfaOrderId, e.getMessage());
                req.setStatus("FAILED");
                bindRequestRepo.save(req);
                return "failed";
            }
            // Перечитываем статус — теперь bindingInfo должен быть заполнен
            status = gateway.getOrderStatusExtended(alfaOrderId);
            orderStatus = status.path("orderStatus").asInt(-1);
        }

        if (orderStatus == 2) { // DEPOSITED — оплата/депозит прошёл
            String bindingId = status.path("bindingInfo").path("bindingId").asText(null);
            log.info("ACTION=CARD_BIND_CONFIRM orderId={} bindingId={}", alfaOrderId, bindingId);

            if (bindingId != null && !bindingId.isBlank()) {
                // Production: bindingInfo заполнен — самый надёжный источник.
                saveFromStatusResponse(req.getUser(), status);
            } else {
                // UAT: bindingInfo отсутствует. cardAuthInfo содержит РЕАЛЬНУЮ карту
                // из ЭТОГО заказа — приоритетный источник. getBindings.do в UAT
                // возвращает чужую/устаревшую привязку (не ту, что привязывали сейчас),
                // поэтому он только последний резерв.
                log.info("ACTION=CARD_BIND_FALLBACK_CARDAUTHINFO orderId={}", alfaOrderId);
                boolean saved = saveFromCardAuthInfo(req.getUser(), alfaOrderId, status);

                if (!saved) {
                    // Последний резерв: getBindings.do
                    String clientId = "user-" + req.getUser().getId();
                    log.info("ACTION=CARD_BIND_FALLBACK_GETBINDINGS clientId={}", clientId);
                    saveFromGetBindings(req.getUser(), clientId);
                }
            }

            req.setStatus("COMPLETED");
            bindRequestRepo.save(req);

            // Возвращаем 1₽ клиенту через refund
            try {
                gateway.refund(alfaOrderId, 100L);
                log.info("ACTION=CARD_BIND_REFUNDED orderId={}", alfaOrderId);
            } catch (Exception e) {
                log.warn("Could not refund card bind payment {}: {}", alfaOrderId, e.getMessage());
            }

            return "completed";
        } else if (orderStatus == 6) { // DECLINED
            req.setStatus("FAILED");
            bindRequestRepo.save(req);
            return "failed";
        }
        return "pending";
    }

    /**
     * Получает список привязок клиента через getBindings.do и сохраняет новые.
     * Возвращает bindingId последней сохранённой привязки или null.
     */
    private String saveFromGetBindings(User user, String clientId) {
        try {
            JsonNode response = gateway.getBindings(clientId);
            JsonNode bindings = response.path("bindings");
            if (!bindings.isArray() || bindings.isEmpty()) {
                log.warn("ACTION=CARD_BIND_EMPTY_BINDINGS clientId={}", clientId);
                return null;
            }

            String lastSaved = null;
            for (JsonNode binding : bindings) {
                String bindingId = binding.path("bindingId").asText(null);
                if (bindingId == null || bindingId.isBlank()) continue;
                if (cardRepo.existsByBindingId(bindingId)) continue; // уже есть

                String maskedPan = binding.path("maskedPan").asText(null);
                if (maskedPan == null || maskedPan.isBlank()) {
                    maskedPan = binding.path("label").asText("****");
                }
                String expiry = normalizeExpiry(binding.path("expiryDate").asText(null));

                boolean isFirst = cardRepo.findByUserAndIsDefaultTrue(user).isEmpty();
                CardBinding card = new CardBinding();
                card.setUser(user);
                card.setBindingId(bindingId);
                card.setMaskedPan(maskedPan);
                card.setExpiry(expiry);
                card.setDefault(isFirst);
                cardRepo.save(card);
                lastSaved = bindingId;
                log.info("ACTION=CARD_SAVED_FROM_GETBINDINGS userId={} maskedPan={} isDefault={}",
                        user.getId(), maskedPan, isFirst);
            }
            return lastSaved;
        } catch (Exception e) {
            log.error("Failed to get bindings for clientId={}: {}", clientId, e.getMessage());
            return null;
        }
    }

    /**
     * Сохраняет карту из cardAuthInfo ответа getOrderStatusExtended.
     * Приоритетный источник в UAT: cardAuthInfo описывает РЕАЛЬНУЮ карту, использованную
     * именно в ЭТОМ заказе (в отличие от getBindings.do, который в UAT отдаёт чужую привязку).
     *
     * cardAuthInfo.expiration формат: YYYYMM ("203412" = декабрь 2034)
     * Конвертируем в MMYYYY ("122034") для совместимости с CardBinding.getExpiryFormatted().
     *
     * bindingId = "CARDAUTH-{alfaOrderId}" — синтетический уникальный ID.
     * В production этот fallback не будет срабатывать: реальный bindingId придёт из bindingInfo.
     *
     * @return true если карта сохранена (или уже была), false если cardAuthInfo пуст.
     */
    private boolean saveFromCardAuthInfo(User user, String alfaOrderId, JsonNode statusNode) {
        String maskedPan = statusNode.path("cardAuthInfo").path("maskedPan").asText(null);
        if (maskedPan == null || maskedPan.isBlank()) {
            log.warn("ACTION=CARD_BIND_NO_CARDAUTHINFO orderId={}", alfaOrderId);
            return false;
        }

        String syntheticBindingId = "CARDAUTH-" + alfaOrderId.replace("-", "").substring(0, 16);
        if (cardRepo.existsByBindingId(syntheticBindingId)) {
            log.info("Card already saved for orderId={}", alfaOrderId);
            return true;
        }

        String expiry = normalizeExpiry(
                statusNode.path("cardAuthInfo").path("expiration").asText(null));

        boolean isFirst = cardRepo.findByUserAndIsDefaultTrue(user).isEmpty();
        CardBinding card = new CardBinding();
        card.setUser(user);
        card.setBindingId(syntheticBindingId);
        card.setMaskedPan(maskedPan);
        card.setExpiry(expiry);
        card.setDefault(isFirst);
        cardRepo.save(card);

        log.info("ACTION=CARD_SAVED_FROM_CARDAUTHINFO userId={} maskedPan={} isDefault={}",
                user.getId(), maskedPan, isFirst);
        return true;
    }

    private String extractMaskedPan(JsonNode statusNode) {
        // Сначала ищем в bindingInfo.label, потом в cardAuthInfo.pan
        String label = statusNode.path("bindingInfo").path("label").asText(null);
        if (label != null && !label.isBlank()) return label;
        return statusNode.path("cardAuthInfo").path("pan").asText("****");
    }

    private String extractExpiry(JsonNode statusNode) {
        String exp = statusNode.path("bindingInfo").path("expiryDate").asText(null);
        if (exp == null || exp.isBlank()) {
            exp = statusNode.path("cardAuthInfo").path("expiration").asText(null);
        }
        return normalizeExpiry(exp);
    }

    /**
     * Нормализует срок действия карты в формат MMYYYY.
     * Альфа Банк может возвращать как MMYYYY ("122034"), так и YYYYMM ("203412").
     * Если первые два символа > 12 — это год, значит формат YYYYMM → конвертируем.
     */
    private String normalizeExpiry(String exp) {
        if (exp == null || exp.length() != 6) return exp;
        try {
            int firstTwo = Integer.parseInt(exp.substring(0, 2));
            if (firstTwo > 12) {
                // YYYYMM → MMYYYY: "203412" → "122034"
                return exp.substring(4) + exp.substring(0, 4);
            }
        } catch (NumberFormatException ignored) {}
        return exp;
    }

    /** Маппинг сущности карты в безопасный для UI DTO (без bindingId, срок в MM/YYYY). */
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
