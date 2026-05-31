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
     * Сохраняем карту ТОЛЬКО при реальном bindingId из bindingInfo — он появляется,
     * если клиент поставил галочку «Сохранить карту» на форме банка. Это единственный
     * способ создать списываемую связку (привилегии AUTO_PAYMENT/FORCE_CREATE_BINDING
     * у UAT-мерчанта отключены, см. CLAUDE.md → «Что РЕАЛЬНО создаёт связку»).
     *
     * Синтетическую карту из cardAuthInfo больше НЕ создаём: она выглядит как карта,
     * но шлюзом не списывается и вводит клиента в заблуждение. Нет галочки → карта не
     * сохраняется; оплата взноса проходит через fallback-форму (BnplService.payInstallmentByClient).
     *
     * Метод не бросает исключений: сбой привязки не должен ломать подтверждение оплаты.
     */
    @Transactional
    public void saveAfterPayment(User user, String alfaOrderId, JsonNode statusNode) {
        try {
            String bindingId = statusNode.path("bindingInfo").path("bindingId").asText(null);
            if (bindingId != null && !bindingId.isBlank()) {
                saveFromStatusResponse(user, statusNode);          // реальный bindingId (галочка отмечена)
                return;
            }
            log.info("ACTION=CARD_NOT_SAVED_NO_BINDING userId={} orderId={} "
                    + "(клиент не поставил «Сохранить карту» — реальной связки нет)",
                    user.getId(), alfaOrderId);
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

            String result;
            if (bindingId != null && !bindingId.isBlank()) {
                // Реальная связка (клиент поставил галочку «Сохранить карту»).
                saveFromStatusResponse(req.getUser(), status);
                req.setStatus("COMPLETED");
                result = "completed";
            } else {
                // Галочки не было → реальной связки нет. Синтетическую карту НЕ создаём.
                log.info("ACTION=CARD_BIND_NO_BINDING orderId={} "
                        + "(галочка «Сохранить карту» не отмечена — связка не создана)", alfaOrderId);
                req.setStatus("FAILED");
                result = "no_binding";
            }
            bindRequestRepo.save(req);

            // В любом случае возвращаем удержанный 1₽.
            try {
                gateway.refund(alfaOrderId, 100L);
                log.info("ACTION=CARD_BIND_REFUNDED orderId={}", alfaOrderId);
            } catch (Exception e) {
                log.warn("Could not refund card bind payment {}: {}", alfaOrderId, e.getMessage());
            }

            return result;
        } else if (orderStatus == 6) { // DECLINED
            req.setStatus("FAILED");
            bindRequestRepo.save(req);
            return "failed";
        }
        return "pending";
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
