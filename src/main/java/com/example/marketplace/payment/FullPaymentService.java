package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.AlfaBankOrder;
import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.Invoice;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.AlfaBankOrderStatus;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.repository.AlfaBankOrderRepository;
import com.example.marketplace.service.CardService;
import com.example.marketplace.service.InvoiceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Полная (одностадийная) оплата через шлюз Альфа Банка.
 *
 * Схема:
 *   initiate() → register.do → formUrl → клиент платит на форме банка
 *   → банк редиректит на /api/payment/callback?orderId=xxx
 *   → confirm() → getOrderStatusExtended.do → если DEPOSITED → markAsPaid()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullPaymentService {

    private final AlfaBankGatewayClient  gateway;
    private final AlfaBankProperties     props;
    private final AlfaBankOrderRepository alfaBankOrderRepo;
    private final InvoiceService          invoiceService;
    private final CardService             cardService;

    /**
     * Инициирует платёж: регистрирует заказ в шлюзе, сохраняет запись,
     * возвращает formUrl для редиректа клиента.
     */
    @Transactional
    public PaymentInitResponse initiate(Long invoiceId) {
        Invoice invoice = invoiceService.findEntityById(invoiceId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Счёт #" + invoiceId + " уже оплачен");
        }

        // Префикс FP- (Full Payment) + 16 символов UUID — уникальный orderNumber для шлюза.
        String orderNumber = "FP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // Шлюз принимает сумму в копейках, поэтому рубли × 100.
        long   amountKopecks = invoice.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // clientId обязателен для привязки карты: с ним Альфа Банк связывает платёж
        // с клиентом и отдаёт данные карты (bindingInfo / cardAuthInfo) в getOrderStatusExtended.
        String clientId = "user-" + invoice.getOrder().getUser().getId();
        JsonNode response = gateway.registerOrderForBinding(
                orderNumber,
                amountKopecks,
                props.getReturnUrl(),
                props.getFailUrl(),
                clientId
        );

        String alfaOrderId = response.path("orderId").asText();
        String formUrl     = response.path("formUrl").asText();

        AlfaBankOrder record = new AlfaBankOrder();
        record.setOrderNumber(orderNumber);
        record.setAlfaOrderId(alfaOrderId);
        record.setInvoice(invoice);
        record.setAmountKopecks(amountKopecks);
        record.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        record.setFormUrl(formUrl);
        alfaBankOrderRepo.save(record);

        log.info("ACTION=FULL_PAYMENT_INITIATE invoiceId={} alfaOrderId={}", invoiceId, alfaOrderId);

        return new PaymentInitResponse(formUrl, alfaOrderId, null);
    }

    /**
     * Подтверждает платёж после редиректа от банка.
     * Возвращает строку-статус: "paid", "failed", "pending".
     */
    @Transactional
    public String confirm(String alfaOrderId) {
        AlfaBankOrder record = alfaBankOrderRepo.findByAlfaOrderId(alfaOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: " + alfaOrderId));

        // Идемпотентность: если уже обработали — не вызываем банк повторно.
        if (record.getStatus() == AlfaBankOrderStatus.DEPOSITED) return "paid";
        if (record.getStatus() == AlfaBankOrderStatus.FAILED)    return "failed";

        JsonNode status = gateway.getOrderStatusExtended(alfaOrderId);
        int orderStatus = status.path("orderStatus").asInt(-1);

        return switch (orderStatus) {
            case 2 -> {
                // DEPOSITED — деньги списаны.
                String bindingId = status.path("bindingInfo").path("bindingId").asText(null);
                record.setBindingId(bindingId);
                record.setStatus(AlfaBankOrderStatus.DEPOSITED);
                alfaBankOrderRepo.save(record);

                // Привязываем карту клиента для будущих платежей (устойчиво: bindingInfo→cardAuthInfo→getBindings).
                cardService.saveAfterPayment(record.getInvoice().getOrder().getUser(), alfaOrderId, status);

                invoiceService.markAsPaid(record.getInvoice(), "CARD");

                log.info("ACTION=FULL_PAYMENT_CONFIRMED alfaOrderId={} invoiceId={}",
                        alfaOrderId, record.getInvoice().getId());
                yield "paid";
            }
            case 6 -> {
                record.setStatus(AlfaBankOrderStatus.FAILED);
                alfaBankOrderRepo.save(record);
                yield "failed";
            }
            default -> "pending";
        };
    }

    /**
     * Тихое списание полной суммы счёта с дефолтной карты клиента (без редиректа и 3DS).
     * Используется администратором: «Оплатить заказ с карты клиента».
     * Возвращает "paid" при успехе.
     */
    @Transactional
    public String payByDefaultCard(Long invoiceId) {
        Invoice invoice = invoiceService.findEntityById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Счёт #" + invoiceId + " уже оплачен");
        }

        User user = invoice.getOrder().getUser();
        CardBinding card = cardService.getDefault(user)
                .orElseThrow(() -> new IllegalStateException("У клиента нет привязанной карты"));

        long amountKopecks = invoice.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        String clientId = "user-" + user.getId();
        String bindingId = cardService.resolveChargeableBindingId(card, clientId);
        if (bindingId == null) {
            throw new IllegalStateException("Не найдена связка карты для списания");
        }

        String orderNumber = "ADM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        // Шаг 1: register.do с clientId → mdOrder
        JsonNode reg = gateway.registerOrderForBinding(
                orderNumber, amountKopecks, props.getReturnUrl(), props.getFailUrl(), clientId);
        String mdOrder = reg.path("orderId").asText(null);
        if (mdOrder == null || mdOrder.isBlank()) {
            throw new IllegalStateException("Не удалось зарегистрировать заказ в шлюзе");
        }

        // Шаг 2: тихое списание по связке (tii=U внутри gateway → без CVC/3DS)
        JsonNode result = gateway.paymentOrderBinding(mdOrder, amountKopecks, bindingId);
        if (!result.path("acsUrl").asText("").isBlank()) {
            throw new IllegalStateException("Банк запросил 3DS — тихое списание недоступно для этой карты");
        }
        String alfaOrderId = result.path("orderId").asText(mdOrder);

        // Фиксируем операцию шлюза и оплачиваем счёт.
        AlfaBankOrder record = new AlfaBankOrder();
        record.setOrderNumber(orderNumber);
        record.setAlfaOrderId(alfaOrderId);
        record.setInvoice(invoice);
        record.setAmountKopecks(amountKopecks);
        record.setStatus(AlfaBankOrderStatus.DEPOSITED);
        record.setBindingId(bindingId);
        alfaBankOrderRepo.save(record);

        invoiceService.markAsPaid(invoice, "CARD");

        log.info("ACTION=ADMIN_FULL_PAYMENT_BY_CARD invoiceId={} alfaOrderId={} kopecks={}",
                invoiceId, alfaOrderId, amountKopecks);
        return "paid";
    }

    /**
     * Оплата счёта администратором с карты клиента с fallback на форму.
     * Есть реальная связка → тихое списание ({@link #payByDefaultCard}), возвращает {@code null}.
     * Связки нет (UAT / карта не привязана на стороне банка) → регистрирует форму
     * ({@link #initiate}) и возвращает {@link PaymentInitResponse} с {@code formUrl}.
     */
    @Transactional
    public PaymentInitResponse payByDefaultCardOrForm(Long invoiceId) {
        Invoice invoice = invoiceService.findEntityById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Счёт #" + invoiceId + " уже оплачен");
        }

        User user = invoice.getOrder().getUser();
        CardBinding card = cardService.getDefault(user).orElse(null);
        String clientId = "user-" + user.getId();
        String bindingId = (card == null) ? null : cardService.resolveChargeableBindingId(card, clientId);

        if (bindingId != null) {
            payByDefaultCard(invoiceId);   // реальная связка → тихое списание
            return null;
        }
        // Нет реальной связки → оплата через форму банка (как первый платёж).
        return initiate(invoiceId);
    }
}
