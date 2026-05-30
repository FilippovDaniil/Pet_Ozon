package com.example.marketplace.controller;

import com.example.marketplace.payment.BnplService;
import com.example.marketplace.payment.FullPaymentService;
import com.example.marketplace.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Публичный контроллер для обработки редиректов от шлюза Альфа Банка.
 *
 * GET /api/payment/callback?orderId=xxx — банк редиректит сюда после успешной оплаты
 * GET /api/payment/fail                 — банк редиректит сюда при ошибке
 *
 * Оба endpoint-а НЕ требуют авторизации (permitAll в SecurityConfig).
 * Возвращают HTML-страницу для отображения результата клиенту.
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final FullPaymentService fullPaymentService;
    private final BnplService        bnplService;
    private final CardService        cardService;

    /**
     * Универсальный callback от банка.
     * Определяет тип платежа по orderId и вызывает нужный confirm().
     */
    @GetMapping(value = "/callback", produces = "text/html;charset=UTF-8")
    public String callback(@RequestParam String orderId) {
        log.info("ACTION=PAYMENT_CALLBACK alfaOrderId={}", orderId);
        try {
            // Сначала пробуем как BNPL pre-auth, потом как полную оплату.
            String result = tryConfirm(orderId);
            return switch (result) {
                case "paid"    -> successHtml("Оплата прошла успешно! Заказ оформлен.");
                case "failed"  -> failHtml("Оплата отклонена банком. Попробуйте снова.");
                default        -> failHtml("Платёж обрабатывается. Обновите страницу через минуту.");
            };
        } catch (Exception e) {
            log.error("Payment callback error for orderId={}: {}", orderId, e.getMessage());
            return failHtml("Ошибка обработки платежа: " + e.getMessage());
        }
    }

    /**
     * Callback после прохождения формы привязки карты.
     * Проверяет статус, сохраняет карту, отменяет списание 1₽.
     */
    @GetMapping(value = "/card-bind-callback", produces = "text/html;charset=UTF-8")
    public String cardBindCallback(@RequestParam String orderId) {
        log.info("ACTION=CARD_BIND_CALLBACK alfaOrderId={}", orderId);
        try {
            String result = cardService.confirmBinding(orderId);
            return switch (result) {
                case "completed" -> htmlPage(
                        "✅ Карта успешно привязана!",
                        "#22c55e",
                        "Вернуться в личный кабинет",
                        "/client.html");
                case "failed" -> htmlPage(
                        "❌ Оплата отклонена банком. Попробуйте снова.",
                        "#ef4444",
                        "Вернуться в личный кабинет",
                        "/client.html");
                case "no_binding", "completed_no_card" -> htmlPage(
                        "⚠️ Оплата прошла, но карта не привязана. Попробуйте ещё раз.",
                        "#f59e0b",
                        "Вернуться в личный кабинет",
                        "/client.html");
                default -> htmlPage(
                        "⏳ Карта обрабатывается. Обновите страницу через момент.",
                        "#f59e0b",
                        "Вернуться в личный кабинет",
                        "/client.html");
            };
        } catch (IllegalArgumentException e) {
            // orderId не принадлежит bind-запросу — пробуем обычный callback
            return callback(orderId);
        } catch (Exception e) {
            log.error("Card bind callback error for orderId={}: {}", orderId, e.getMessage());
            return htmlPage("❌ Ошибка при привязке карты: " + e.getMessage(),
                    "#ef4444", "Вернуться", "/client.html");
        }
    }

    @GetMapping(value = "/fail", produces = "text/html;charset=UTF-8")
    public String fail(@RequestParam(required = false) String orderId) {
        log.warn("ACTION=PAYMENT_FAIL alfaOrderId={}", orderId);
        return failHtml("Оплата не завершена. Вернитесь в личный кабинет и попробуйте снова.");
    }

    // Пробуем сначала BNPL, потом Full — у каждого свой репозиторий.
    private String tryConfirm(String alfaOrderId) {
        try {
            return bnplService.confirmPreAuth(alfaOrderId);
        } catch (IllegalArgumentException ignored) {
            // не BNPL-платёж — пробуем Full
        }
        return fullPaymentService.confirm(alfaOrderId);
    }

    private String successHtml(String message) {
        return htmlPage("✅ " + message,
                "#22c55e",
                "Перейти в личный кабинет",
                "/client.html");
    }

    private String failHtml(String message) {
        return htmlPage("❌ " + message,
                "#ef4444",
                "Вернуться в магазин",
                "/client.html");
    }

    /** Собирает простую HTML-страницу результата (цвет, текст, кнопка) через Tailwind CDN. */
    private String htmlPage(String message, String color, String btnText, String btnHref) {
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Результат оплаты</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                </head>
                <body class="min-h-screen bg-gray-50 flex items-center justify-center">
                  <div class="bg-white rounded-2xl shadow-lg p-10 max-w-md w-full text-center">
                    <p class="text-xl font-semibold mb-6" style="color:%s">%s</p>
                    <a href="%s"
                       class="inline-block px-6 py-3 rounded-xl text-white font-medium"
                       style="background:%s">%s</a>
                  </div>
                </body>
                </html>
                """.formatted(color, message, btnHref, color, btnText);
    }
}
