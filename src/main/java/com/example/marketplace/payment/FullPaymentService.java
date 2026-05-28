package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.AlfaBankOrder;
import com.example.marketplace.entity.Invoice;
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

        String orderNumber = "FP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long   amountKopecks = invoice.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        JsonNode response = gateway.registerOrder(
                orderNumber,
                amountKopecks,
                props.getReturnUrl(),
                props.getFailUrl()
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

                // Сохраняем привязку карты для будущих платежей.
                cardService.saveFromStatusResponse(record.getInvoice().getOrder().getUser(), status);

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
}
