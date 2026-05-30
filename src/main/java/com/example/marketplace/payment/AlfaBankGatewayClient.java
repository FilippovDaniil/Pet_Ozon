package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP-клиент для API шлюза Альфа Банка.
 *
 * Все методы отправляют POST application/x-www-form-urlencoded и возвращают
 * JsonNode. При errorCode != "0" бросается RuntimeException.
 *
 * Суммы принимаются в КОПЕЙКАХ (рубли × 100).
 * amount = 0 в deposit.do означает «списать всю авторизованную сумму».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlfaBankGatewayClient {

    private final AlfaBankProperties props;
    private final ObjectMapper        objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ─── Одностадийная регистрация ───────────────────────────────────────────

    /**
     * Регистрация одностадийного заказа. Возвращает {orderId, formUrl}.
     */
    public JsonNode registerOrder(String orderNumber, long amountKopecks, String returnUrl, String failUrl) {
        Map<String, String> params = baseParams();
        params.put("orderNumber", orderNumber);
        params.put("amount",      String.valueOf(amountKopecks));
        params.put("returnUrl",   returnUrl);
        params.put("failUrl",     failUrl);
        return call("register.do", params);
    }

    /**
     * Регистрация одностадийного заказа с clientId — обязательно для сохранения привязки карты.
     * При передаче clientId Альфа Банк возвращает bindingInfo в getOrderStatusExtended
     * после успешной оплаты, что позволяет сохранить bindingId для рекуррентных платежей.
     */
    public JsonNode registerOrderForBinding(String orderNumber, long amountKopecks,
                                            String returnUrl, String failUrl, String clientId) {
        Map<String, String> params = baseParams();
        params.put("orderNumber", orderNumber);
        params.put("amount",      String.valueOf(amountKopecks));
        params.put("returnUrl",   returnUrl);
        params.put("failUrl",     failUrl);
        params.put("clientId",    clientId);   // связывает платёж с клиентом → создаёт bindingId
        return call("register.do", params);
    }

    // ─── Двухстадийная регистрация (pre-auth / BNPL) ─────────────────────────

    /**
     * Регистрация двухстадийного заказа (pre-auth). Деньги холдируются.
     */
    public JsonNode registerPreAuth(String orderNumber, long amountKopecks, String returnUrl, String failUrl) {
        Map<String, String> params = baseParams();
        params.put("orderNumber", orderNumber);
        params.put("amount",      String.valueOf(amountKopecks));
        params.put("returnUrl",   returnUrl);
        params.put("failUrl",     failUrl);
        return call("registerPreAuth.do", params);
    }

    /**
     * Pre-auth с clientId специально для привязки карты.
     * После deposit.do Альфа Банк гарантированно возвращает bindingInfo.bindingId —
     * не требует UI-тоггла "Сохранить карту" на форме оплаты.
     */
    public JsonNode registerPreAuthForBinding(String orderNumber, long amountKopecks,
                                              String returnUrl, String failUrl, String clientId) {
        Map<String, String> params = baseParams();
        params.put("orderNumber", orderNumber);
        params.put("amount",      String.valueOf(amountKopecks));
        params.put("returnUrl",   returnUrl);
        params.put("failUrl",     failUrl);
        params.put("clientId",    clientId);
        return call("registerPreAuth.do", params);
    }

    /**
     * Подтверждение (депозит) pre-auth заказа.
     * amount = 0 → списать всю авторизованную сумму; иначе — частичное списание.
     */
    public JsonNode deposit(String alfaOrderId, long amountKopecks) {
        Map<String, String> params = baseParams();
        params.put("orderId", alfaOrderId);
        params.put("amount",  String.valueOf(amountKopecks));
        return call("deposit.do", params);
    }

    /**
     * Отмена авторизации (reverse) — частичная или полная.
     * amount = 0 → отменить всю авторизацию.
     */
    public JsonNode reverse(String alfaOrderId, long amountKopecks) {
        Map<String, String> params = baseParams();
        params.put("orderId", alfaOrderId);
        if (amountKopecks > 0) {
            params.put("amount", String.valueOf(amountKopecks));
        }
        return call("reverse.do", params);
    }

    /**
     * Возврат средств (refund) — частичный или полный.
     */
    public JsonNode refund(String alfaOrderId, long amountKopecks) {
        Map<String, String> params = baseParams();
        params.put("orderId", alfaOrderId);
        params.put("amount",  String.valueOf(amountKopecks));
        return call("refund.do", params);
    }

    // ─── Рекуррентные платежи (BNPL последующие взносы) ──────────────────────

    /**
     * Оплата по привязке карты (без ввода данных карты клиентом).
     *
     * ВАЖНО: mdOrder — это orderId от Альфа Банка, полученный через register.do.
     * Нельзя передавать наш внутренний orderNumber — банк вернёт "mdOrder не задан".
     * Правильный flow:
     *   1. registerOrder(orderNumber, amount, ...) → orderId (= mdOrder)
     *   2. paymentOrderBinding(orderId, amount, bindingId) → instant charge
     */
    public JsonNode paymentOrderBinding(String mdOrder, long amountKopecks, String bindingId) {
        Map<String, String> params = baseParams();
        params.put("mdOrder",   mdOrder);     // Alfa Bank orderId из register.do
        params.put("amount",    String.valueOf(amountKopecks));
        params.put("bindingId", bindingId);
        params.put("ip",        "127.0.0.1"); // обязательный параметр шлюза
        // tii=U — Merchant Initiated Transaction (Credential-on-File).
        // Помечает списание как инициированное магазином по ранее данному согласию клиента.
        // Это освобождает от CVC и 3DS → тихое рекуррентное списание без участия клиента.
        params.put("tii",       "U");
        return call("paymentOrderBinding.do", params);
    }

    // ─── Статус заказа ───────────────────────────────────────────────────────

    /**
     * Расширенный статус заказа. Включает bindingId и детали карты.
     */
    public JsonNode getOrderStatusExtended(String alfaOrderId) {
        Map<String, String> params = baseParams();
        params.put("orderId", alfaOrderId);
        return call("getOrderStatusExtended.do", params);
    }

    /**
     * Получить все привязки карт для клиента.
     * Используется после одностадийного платежа с clientId —
     * Альфа Банк не всегда возвращает bindingInfo в getOrderStatusExtended,
     * но getBindings.do гарантированно отдаёт актуальный список привязок.
     *
     * Ответ: { "errorCode": "0", "bindings": [ { "bindingId": "...", "maskedPan": "...", "expiryDate": "MMYYYY" } ] }
     */
    public JsonNode getBindings(String clientId) {
        Map<String, String> params = baseParams();
        params.put("clientId", clientId);
        return call("getBindings.do", params);
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    /** Базовый набор параметров для любого вызова шлюза — логин/пароль мерчанта. */
    private Map<String, String> baseParams() {
        Map<String, String> p = new LinkedHashMap<>();   // LinkedHashMap — сохраняет порядок параметров
        p.put("userName", props.getUserName());
        p.put("password", props.getPassword());
        return p;
    }

    /**
     * Единая точка вызова шлюза: form-urlencoded POST + разбор JSON + проверка errorCode.
     * При errorCode != "0" или сетевой ошибке бросает RuntimeException.
     */
    private JsonNode call(String method, Map<String, String> params) {
        String body = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(),   StandardCharsets.UTF_8)
                        + "=" +
                          URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getGatewayUrl() + method))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Alfa Bank [{}] response: {}", method, response.body());

            JsonNode json = objectMapper.readTree(response.body());

            // Альфа Банк НЕ включает errorCode в тело при успехе → дефолт "0".
            // При ошибке errorCode присутствует и содержит ненулевое значение.
            String errorCode = json.path("errorCode").asText("0");
            if (!"0".equals(errorCode)) {
                String msg = json.path("errorMessage").asText("Неизвестная ошибка шлюза");
                log.error("Alfa Bank [{}] error {}: {}", method, errorCode, msg);
                throw new RuntimeException("Alfa Bank error " + errorCode + ": " + msg);
            }

            return json;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Alfa Bank [{}] connection error: {}", method, e.getMessage());
            throw new RuntimeException("Ошибка соединения с платёжным шлюзом: " + e.getMessage(), e);
        }
    }
}
