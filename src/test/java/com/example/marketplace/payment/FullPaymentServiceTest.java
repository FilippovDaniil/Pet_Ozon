package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.AlfaBankOrder;
import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.Invoice;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.AlfaBankOrderStatus;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.repository.AlfaBankOrderRepository;
import com.example.marketplace.service.CardService;
import com.example.marketplace.service.InvoiceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты сервиса полной одностадийной оплаты через Альфа Банк.
 * Проверяем инициацию платежа, обработку callback (DEPOSITED/DECLINED/pending/идемпотентность)
 * и оплату админом с дефолтной карты.
 * LENIENT — общие стабы в @BeforeEach не используются в тестах с ранним throw.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FullPaymentServiceTest {

    @Mock AlfaBankGatewayClient    gateway;
    @Mock AlfaBankProperties       props;
    @Mock AlfaBankOrderRepository  alfaBankOrderRepo;
    @Mock InvoiceService           invoiceService;
    @Mock CardService              cardService;

    @InjectMocks FullPaymentService fullPaymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setupProps() {
        when(props.getReturnUrl()).thenReturn("http://localhost:30667/api/payment/callback");
        when(props.getFailUrl()).thenReturn("http://localhost:30667/api/payment/fail");
    }

    private Invoice makeInvoice(Long id, InvoiceStatus status, BigDecimal amount) {
        User user = new User();
        user.setId(1L);
        user.setEmail("client@test.com");

        Order order = new Order();
        order.setId(1L);
        order.setUser(user);

        Invoice inv = new Invoice();
        inv.setId(id);
        inv.setOrder(order);
        inv.setAmount(amount);
        inv.setStatus(status);
        return inv;
    }

    // ── initiate ─────────────────────────────────────────────────────────────

    // Неоплаченный счёт → регистрация в шлюзе + сохранение записи + возврат formUrl.
    @Test
    void initiate_unpaidInvoice_registersOrderAndReturnsFormUrl() throws Exception {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.UNPAID, new BigDecimal("1000.00"));
        when(invoiceService.findEntityById(1L)).thenReturn(invoice);
        when(alfaBankOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JsonNode gatewayResp = objectMapper.readTree(
                """
                {"orderId":"alfa-123","formUrl":"https://alfa.rbsuat.com/form?id=alfa-123","errorCode":"0"}
                """);
        when(gateway.registerOrderForBinding(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(gatewayResp);

        PaymentInitResponse result = fullPaymentService.initiate(1L);

        assertThat(result.formUrl()).isEqualTo("https://alfa.rbsuat.com/form?id=alfa-123");
        assertThat(result.alfaOrderId()).isEqualTo("alfa-123");
        assertThat(result.contractId()).isNull(); // полная оплата, не BNPL
        verify(alfaBankOrderRepo).save(any(AlfaBankOrder.class));
    }

    // Уже оплаченный счёт → исключение, шлюз не вызывается.
    @Test
    void initiate_alreadyPaidInvoice_throwsIllegalState() {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.PAID, new BigDecimal("1000.00"));
        when(invoiceService.findEntityById(1L)).thenReturn(invoice);

        assertThatThrownBy(() -> fullPaymentService.initiate(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже оплачен");
        verify(gateway, never()).registerOrderForBinding(anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void initiate_amountConvertedToKopecks() throws Exception {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.UNPAID, new BigDecimal("123.45"));
        when(invoiceService.findEntityById(1L)).thenReturn(invoice);
        when(alfaBankOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JsonNode gatewayResp = objectMapper.readTree("{\"orderId\":\"x\",\"formUrl\":\"http://f\"}");
        when(gateway.registerOrderForBinding(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(gatewayResp);

        fullPaymentService.initiate(1L);

        // 123.45 руб. = 12345 копеек
        verify(gateway).registerOrderForBinding(anyString(), eq(12345L), anyString(), anyString(), anyString());
    }

    // ── confirm ───────────────────────────────────────────────────────────────

    @Test
    void confirm_deposited_marksInvoicePaidAndReturnsSuccess() throws Exception {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.UNPAID, new BigDecimal("1000.00"));
        AlfaBankOrder record = new AlfaBankOrder();
        record.setAlfaOrderId("alfa-123");
        record.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        record.setInvoice(invoice);

        when(alfaBankOrderRepo.findByAlfaOrderId("alfa-123")).thenReturn(Optional.of(record));
        when(alfaBankOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Банк вернул orderStatus = 2 (DEPOSITED)
        JsonNode statusResp = objectMapper.readTree("""
                {"orderStatus":2,"bindingInfo":{"bindingId":"b-001","label":"411111**1111","expiryDate":"122026"}}
                """);
        when(gateway.getOrderStatusExtended("alfa-123")).thenReturn(statusResp);
        doNothing().when(cardService).saveAfterPayment(any(), anyString(), any());
        doNothing().when(invoiceService).markAsPaid(any(), anyString());

        String result = fullPaymentService.confirm("alfa-123");

        assertThat(result).isEqualTo("paid");
        verify(invoiceService).markAsPaid(invoice, "CARD");
        verify(cardService).saveAfterPayment(any(), anyString(), any());
    }

    @Test
    void confirm_declined_returnsFailed() throws Exception {
        AlfaBankOrder record = new AlfaBankOrder();
        record.setAlfaOrderId("alfa-456");
        record.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        record.setInvoice(makeInvoice(2L, InvoiceStatus.UNPAID, new BigDecimal("500.00")));

        when(alfaBankOrderRepo.findByAlfaOrderId("alfa-456")).thenReturn(Optional.of(record));
        when(alfaBankOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Банк вернул orderStatus = 6 (DECLINED)
        JsonNode statusResp = objectMapper.readTree("{\"orderStatus\":6}");
        when(gateway.getOrderStatusExtended("alfa-456")).thenReturn(statusResp);

        String result = fullPaymentService.confirm("alfa-456");

        assertThat(result).isEqualTo("failed");
        verify(invoiceService, never()).markAsPaid(any(), anyString());
    }

    @Test
    void confirm_pending_returnsPending() throws Exception {
        AlfaBankOrder record = new AlfaBankOrder();
        record.setAlfaOrderId("alfa-789");
        record.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        record.setInvoice(makeInvoice(3L, InvoiceStatus.UNPAID, new BigDecimal("300.00")));

        when(alfaBankOrderRepo.findByAlfaOrderId("alfa-789")).thenReturn(Optional.of(record));

        // Банк вернул orderStatus = 0 (ещё не оплачен)
        JsonNode statusResp = objectMapper.readTree("{\"orderStatus\":0}");
        when(gateway.getOrderStatusExtended("alfa-789")).thenReturn(statusResp);

        String result = fullPaymentService.confirm("alfa-789");

        assertThat(result).isEqualTo("pending");
    }

    @Test
    void confirm_alreadyDeposited_idempotentReturnsPaid() {
        AlfaBankOrder record = new AlfaBankOrder();
        record.setAlfaOrderId("alfa-000");
        record.setStatus(AlfaBankOrderStatus.DEPOSITED); // уже обработан

        when(alfaBankOrderRepo.findByAlfaOrderId("alfa-000")).thenReturn(Optional.of(record));

        String result = fullPaymentService.confirm("alfa-000");

        assertThat(result).isEqualTo("paid");
        verify(gateway, never()).getOrderStatusExtended(anyString()); // повторный запрос к банку не нужен
    }

    // Неизвестный orderId → IllegalArgumentException (даёт callback'у попробовать BNPL).
    @Test
    void confirm_unknownOrderId_throwsException() {
        when(alfaBankOrderRepo.findByAlfaOrderId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fullPaymentService.confirm("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── payByDefaultCard (админ платит за клиента с дефолтной карты) ────────────

    @Test
    void payByDefaultCard_chargesFullAmountAndMarksPaid() throws Exception {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.UNPAID, new BigDecimal("1000.00"));
        CardBinding card = new CardBinding();
        card.setBindingId("b-real");

        when(invoiceService.findEntityById(1L)).thenReturn(invoice);
        when(cardService.getDefault(any())).thenReturn(Optional.of(card));
        when(cardService.resolveChargeableBindingId(any(), eq("user-1"))).thenReturn("b-real");
        when(alfaBankOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        when(gateway.paymentOrderBinding(anyString(), eq(100000L), eq("b-real")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-1\"}"));
        doNothing().when(invoiceService).markAsPaid(any(), anyString());

        String result = fullPaymentService.payByDefaultCard(1L);

        assertThat(result).isEqualTo("paid");
        // Списана полная сумма счёта (1000 ₽ = 100000 коп) по дефолтной карте.
        verify(gateway).paymentOrderBinding(anyString(), eq(100000L), eq("b-real"));
        verify(invoiceService).markAsPaid(invoice, "CARD");
    }

    // Нет дефолтной карты → исключение, списание не выполняется.
    @Test
    void payByDefaultCard_noCard_throws() {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.UNPAID, new BigDecimal("500.00"));
        when(invoiceService.findEntityById(1L)).thenReturn(invoice);
        when(cardService.getDefault(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fullPaymentService.payByDefaultCard(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нет привязанной карты");
        verify(gateway, never()).paymentOrderBinding(anyString(), anyLong(), anyString());
    }

    // Счёт уже оплачен → исключение до обращения к карте.
    @Test
    void payByDefaultCard_alreadyPaid_throws() {
        Invoice invoice = makeInvoice(1L, InvoiceStatus.PAID, new BigDecimal("500.00"));
        when(invoiceService.findEntityById(1L)).thenReturn(invoice);

        assertThatThrownBy(() -> fullPaymentService.payByDefaultCard(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже оплачен");
    }
}
