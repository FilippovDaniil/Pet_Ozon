package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.BnplInstallmentResponse;
import com.example.marketplace.entity.*;
import com.example.marketplace.entity.enums.*;
import com.example.marketplace.service.InvoiceService;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Юнит-тесты ключевых методов BnplService:
// — перенос взноса (postponeInstallment)
// — досрочная оплата (payInstallmentsNow)
// — управление статусом товаров (issueItem, cancelItem, returnItem)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BnplServiceTest {

    @Mock AlfaBankGatewayClient     gateway;
    @Mock AlfaBankProperties        props;
    @Mock AlfaBankOrderRepository   alfaBankOrderRepo;
    @Mock BnplContractRepository    contractRepo;
    @Mock BnplInstallmentRepository installmentRepo;
    @Mock InvoiceService            invoiceService;
    @Mock OrderRepository           orderRepo;
    @Mock OrderItemRepository       orderItemRepo;
    @Mock InvoiceRepository         invoiceRepo;
    @Mock CardService               cardService;

    @InjectMocks BnplService bnplService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("client@test.com");
        return u;
    }

    private BnplContract makeActiveContract(Long id, User user, long totalKopecks) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setTotalAmount(BigDecimal.valueOf(totalKopecks / 100));
        order.setItems(new ArrayList<>());

        BnplContract c = new BnplContract();
        c.setId(id);
        c.setOrder(order);
        c.setProduct(BnplProduct.BIWEEKLY_4);
        c.setTotalAmountKopecks(totalKopecks);
        c.setCommissionKopecks(0L);
        c.setInstallmentCount(4);
        c.setStatus(BnplContractStatus.ACTIVE);
        c.setAlfaPreAuthOrderId("alfa-pre-001");
        c.setDepositedAmountKopecks(200_00L); // первый взнос уже оплачен
        c.setInstallments(new ArrayList<>());
        return c;
    }

    private BnplInstallment makePendingInstallment(Long id, BnplContract contract, int number,
                                                    long amountKopecks, LocalDate dueDate) {
        BnplInstallment inst = new BnplInstallment();
        inst.setId(id);
        inst.setContract(contract);
        inst.setInstallmentNumber(number);
        inst.setAmountKopecks(amountKopecks);
        inst.setDueDate(dueDate);
        inst.setStatus(BnplInstallmentStatus.PENDING);
        inst.setDaysPostponed(0);
        inst.setPostponeFeePaidKopecks(0L);
        return inst;
    }

    // ── postponeInstallment ───────────────────────────────────────────────────

    @Test
    void postponeInstallment_validDays_updatesDueDateAndAddsCommission() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);

        // Следующий взнос: 200 руб., через 5 дней
        BnplInstallment nextInst = makePendingInstallment(
                100L, contract, 2, 200_00L, LocalDate.now().plusDays(5));
        contract.getInstallments().add(nextInst);

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Переносим на 3 дня
        BnplInstallmentResponse result = bnplService.postponeInstallment(10L, 3, user);

        // dueDate должна сдвинуться на 3 дня
        assertThat(result.dueDate()).isEqualTo(LocalDate.now().plusDays(5 + 3));

        // Комиссия = 200_00 × 0.0005 × 3 = 30 копеек
        long expectedFee = Math.round(200_00L * 0.0005 * 3);
        assertThat(result.amountKopecks()).isEqualTo(200_00L + expectedFee);

        // daysPostponed = 3
        assertThat(result.daysPostponed()).isEqualTo(3);
        assertThat(result.daysPostponeLeft()).isEqualTo(14 - 3);
    }

    @Test
    void postponeInstallment_exceedsLimit_throwsException() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);

        BnplInstallment nextInst = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(5));
        nextInst.setDaysPostponed(12); // уже использовано 12 дней из 14
        contract.getInstallments().add(nextInst);

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));

        // Пытаемся перенести ещё на 5 дней (12 + 5 = 17 > 14)
        assertThatThrownBy(() -> bnplService.postponeInstallment(10L, 5, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Превышен лимит");
    }

    @Test
    void postponeInstallment_multiplePostpones_accumulate() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);

        BnplInstallment nextInst = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(10));
        nextInst.setDaysPostponed(5); // уже перенесён на 5 дней
        contract.getInstallments().add(nextInst);

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Второй перенос на 4 дня (5 + 4 = 9 ≤ 14 — допустимо)
        BnplInstallmentResponse result = bnplService.postponeInstallment(10L, 4, user);

        assertThat(result.daysPostponed()).isEqualTo(9);
        assertThat(result.daysPostponeLeft()).isEqualTo(5);
    }

    @Test
    void postponeInstallment_inactiveContract_throwsException() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        contract.setStatus(BnplContractStatus.COMPLETED); // завершён

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> bnplService.postponeInstallment(10L, 3, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не активен");
    }

    @Test
    void postponeInstallment_wrongOwner_throwsException() {
        User owner = makeUser(1L);
        User other = makeUser(99L);
        BnplContract contract = makeActiveContract(10L, owner, 800_00L);

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> bnplService.postponeInstallment(10L, 3, other))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нет доступа");
    }

    @Test
    void postponeInstallment_noPendingInstallments_throwsException() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        // Нет PENDING взносов — все оплачены

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> bnplService.postponeInstallment(10L, 3, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нет активных взносов");
    }

    // ── payInstallmentsNow ────────────────────────────────────────────────────

    @Test
    void payInstallmentsNow_nextInstallment_chargesAndMarksPaid() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");
        card.setMaskedPan("411111**1111");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // register.do → mdOrder, затем тихое списание по связке (tii=U внутри gateway)
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-123\"}"));
        JsonNode gatewayResp = objectMapper.readTree("{\"orderId\":\"pay-123\"}");
        when(gateway.paymentOrderBinding(anyString(), eq(200_00L), eq("b-001")))
                .thenReturn(gatewayResp);

        List<BnplInstallmentResponse> result = bnplService.payInstallmentsNow(10L, null, user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("PAID");
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("b-001"));
    }

    @Test
    void payInstallmentsNow_customAmount_coversMultipleInstallments() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);

        BnplInstallment inst2 = makePendingInstallment(101L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        BnplInstallment inst3 = makePendingInstallment(102L, contract, 3, 200_00L, LocalDate.now().plusDays(21));
        BnplInstallment inst4 = makePendingInstallment(103L, contract, 4, 200_00L, LocalDate.now().plusDays(35));
        contract.getInstallments().addAll(List.of(inst2, inst3, inst4));

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-456\"}"));
        JsonNode gatewayResp = objectMapper.readTree("{\"orderId\":\"pay-456\"}");
        when(gateway.paymentOrderBinding(anyString(), eq(400_00L), eq("b-001")))
                .thenReturn(gatewayResp);

        // Оплачиваем 400 руб. = 2 взноса
        List<BnplInstallmentResponse> result = bnplService.payInstallmentsNow(10L, 400_00L, user);

        assertThat(result).hasSize(2);
        // Все оплаченные взносы имеют статус PAID
        assertThat(result).allMatch(r -> "PAID".equals(r.status()));
        // Вызов банка на суммарную сумму
        verify(gateway).paymentOrderBinding(anyString(), eq(400_00L), eq("b-001"));
    }

    @Test
    void payInstallmentsNow_noDefaultCard_throwsException() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.empty()); // нет карты

        assertThatThrownBy(() -> bnplService.payInstallmentsNow(10L, null, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нет привязанной карты");
    }

    @Test
    void payInstallmentsNow_belowMinimum_throwsException() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));

        // Частичные платежи разрешены, но минимум — 50 ₽. Пытаемся оплатить 49 ₽.
        assertThatThrownBy(() -> bnplService.payInstallmentsNow(10L, 49_00L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Минимальная сумма");
    }

    @Test
    void payInstallmentsNow_syntheticBindingId_resolvesRealViaGetBindings() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        CardBinding card = new CardBinding();
        card.setBindingId("CARDAUTH-0211dd764fd37b99"); // синтетический — напрямую не списывается

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // getBindings.do отдаёт РЕАЛЬНУЮ связку клиента
        when(gateway.getBindings("user-1")).thenReturn(
                objectMapper.readTree("{\"errorCode\":\"0\",\"bindings\":[{\"bindingId\":\"real-99\"}]}"));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        when(gateway.paymentOrderBinding(anyString(), eq(200_00L), eq("real-99")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-1\"}"));

        List<BnplInstallmentResponse> result = bnplService.payInstallmentsNow(10L, null, user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("PAID");
        verify(gateway).getBindings("user-1");
        // списание прошло именно по РЕАЛЬНОМУ bindingId, а не по синтетическому
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("real-99"));
    }

    @Test
    void payInstallmentsNow_threeDsResponse_throwsAndKeepsPending() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        CardBinding card = new CardBinding();
        card.setBindingId("b-001"); // реальный

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        // Банк ответил acsUrl → требуется 3DS, тихое списание невозможно
        when(gateway.paymentOrderBinding(anyString(), anyLong(), eq("b-001")))
                .thenReturn(objectMapper.readTree("{\"acsUrl\":\"https://acs\",\"orderId\":\"x\"}"));

        assertThatThrownBy(() -> bnplService.payInstallmentsNow(10L, null, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3DS");

        // Взнос НЕ помечен оплаченным
        assertThat(next.getStatus()).isEqualTo(BnplInstallmentStatus.PENDING);
        verify(installmentRepo, never()).save(any());
    }

    @Test
    void payInstallmentsNow_noRealBinding_throws() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        CardBinding card = new CardBinding();
        card.setBindingId("CARDAUTH-xxx"); // синтетический

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        // getBindings пуст → реальной связки нет
        when(gateway.getBindings("user-1")).thenReturn(
                objectMapper.readTree("{\"errorCode\":\"2\",\"bindings\":[]}"));

        assertThatThrownBy(() -> bnplService.payInstallmentsNow(10L, null, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("связка");

        verify(gateway, never()).registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString());
        verify(gateway, never()).paymentOrderBinding(anyString(), anyLong(), anyString());
    }

    @Test
    void payInstallmentsNow_inactiveContract_throws() {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        contract.setStatus(BnplContractStatus.COMPLETED);

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> bnplService.payInstallmentsNow(10L, null, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не активен");
    }

    @Test
    void payInstallmentsNow_partialBelowInstallment_chargesPartialAndKeepsPending() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L); // deposited=200_00 изначально
        BnplInstallment next = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now().plusDays(7));
        contract.getInstallments().add(next);

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        // Платим 100 ₽ при взносе 200 ₽ → частичный платёж
        when(gateway.paymentOrderBinding(anyString(), eq(100_00L), eq("b-001")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-1\"}"));

        bnplService.payInstallmentsNow(10L, 100_00L, user);

        // Взнос остаётся PENDING (не покрыт целиком)
        assertThat(next.getStatus()).isEqualTo(BnplInstallmentStatus.PENDING);
        // Депозит увеличен на 100 ₽: было 200_00 → стало 300_00
        assertThat(contract.getDepositedAmountKopecks()).isEqualTo(300_00L);
        verify(gateway).paymentOrderBinding(anyString(), eq(100_00L), eq("b-001"));
    }

    // ── processInstallment (планировщик авто-списания) ──────────────────────────

    @Test
    void processInstallment_silentSuccess_marksPaid() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        contract.setBindingId("b-real"); // реальный bindingId в контракте
        BnplInstallment inst = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now());
        contract.getInstallments().add(inst);

        when(installmentRepo.save(any())).thenAnswer(invn -> invn.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(invn -> invn.getArgument(0));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        when(gateway.paymentOrderBinding(anyString(), eq(200_00L), eq("b-real")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-1\"}"));

        bnplService.processInstallment(inst);

        assertThat(inst.getStatus()).isEqualTo(BnplInstallmentStatus.PAID);
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("b-real"));
    }

    @Test
    void processInstallment_threeDsRequested_marksOverdue() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        contract.setBindingId("b-real");
        BnplInstallment inst = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now());
        contract.getInstallments().add(inst);

        when(installmentRepo.save(any())).thenAnswer(invn -> invn.getArgument(0));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        when(gateway.paymentOrderBinding(anyString(), anyLong(), eq("b-real")))
                .thenReturn(objectMapper.readTree("{\"acsUrl\":\"https://acs\"}"));

        bnplService.processInstallment(inst);

        // 3DS при авто-списании → взнос помечается просроченным
        assertThat(inst.getStatus()).isEqualTo(BnplInstallmentStatus.OVERDUE);
    }

    @Test
    void processInstallment_noBinding_skipsWithoutCharge() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeActiveContract(10L, user, 800_00L);
        contract.setBindingId(null); // нет сохранённого bindingId
        BnplInstallment inst = makePendingInstallment(100L, contract, 2, 200_00L, LocalDate.now());
        contract.getInstallments().add(inst);

        // getBindings пуст → реальной связки нет
        when(gateway.getBindings("user-1")).thenReturn(objectMapper.readTree("{\"bindings\":[]}"));

        bnplService.processInstallment(inst);

        assertThat(inst.getStatus()).isEqualTo(BnplInstallmentStatus.PENDING);
        verify(gateway, never()).registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString());
        verify(gateway, never()).paymentOrderBinding(anyString(), anyLong(), anyString());
    }

    // ── issueItem / cancelItem / returnItem ────────────────────────────────────

    @Test
    void issueItem_pendingItem_changesStatusToIssued() throws Exception {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("1000.00"));

        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("1000.00"));

        OrderItem item = new OrderItem();
        item.setId(50L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrder(new BigDecimal("1000.00"));
        item.setItemStatus(ItemStatus.PENDING_ISSUE);

        order.setItems(new ArrayList<>(List.of(item)));

        BnplContract contract = makeActiveContract(10L, user, 100_000L);
        contract.setOrder(order);
        BnplInstallment first = makePendingInstallment(100L, contract, 1, 25_000L, LocalDate.now());
        contract.getInstallments().add(first);
        contract.setDepositedAmountKopecks(0L); // ещё не было deposit

        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setOrder(order);
        invoice.setAmount(BigDecimal.valueOf(1000));
        invoice.setStatus(InvoiceStatus.UNPAID);

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(contractRepo.findByOrder(order)).thenReturn(Optional.of(contract));
        when(installmentRepo.findByContractAndInstallmentNumber(contract, 1)).thenReturn(Optional.of(first));
        when(orderItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepo.findByOrder(order)).thenReturn(Optional.of(invoice));
        doNothing().when(invoiceService).markAsPaid(any(), anyString());

        // deposit.do возвращает успешный ответ
        JsonNode depositResp = objectMapper.readTree("{\"orderStatus\":2}");
        when(gateway.deposit(anyString(), eq(25_000L))).thenReturn(depositResp);

        // getOrderStatusExtended после deposit
        JsonNode statusResp = objectMapper.readTree("{\"orderStatus\":2,\"bindingInfo\":{\"bindingId\":\"b-001\"}}");
        when(gateway.getOrderStatusExtended(anyString())).thenReturn(statusResp);
        doNothing().when(cardService).saveFromStatusResponse(any(), any());
        doNothing().when(invoiceService).markAsPaid(any(), anyString());

        bnplService.issueItem(5L, 50L);

        // Статус товара → ISSUED
        assertThat(item.getItemStatus()).isEqualTo(ItemStatus.ISSUED);
        // deposit.do вызван
        verify(gateway).deposit(anyString(), eq(25_000L));
    }

    @Test
    void cancelItem_pendingItem_reversesShare() throws Exception {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("1000.00"));

        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("1000.00"));

        OrderItem item = new OrderItem();
        item.setId(50L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrder(new BigDecimal("1000.00"));
        item.setItemStatus(ItemStatus.PENDING_ISSUE);
        order.setItems(new ArrayList<>(List.of(item)));

        BnplContract contract = makeActiveContract(10L, user, 100_000L);
        contract.setOrder(order);
        contract.setDepositedAmountKopecks(0L); // deposit ещё не был

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(contractRepo.findByOrder(order)).thenReturn(Optional.of(contract));
        when(orderItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // reverse.do — успех
        JsonNode reverseResp = objectMapper.readTree("{\"errorCode\":\"0\"}");
        when(gateway.reverse(anyString(), anyLong())).thenReturn(reverseResp);

        bnplService.cancelItem(5L, 50L);

        // Статус товара → CANCELLED
        assertThat(item.getItemStatus()).isEqualTo(ItemStatus.CANCELLED);
        // Частичный reverse был вызван
        verify(gateway).reverse(eq("alfa-pre-001"), anyLong());
    }

    @Test
    void cancelItem_issuedItem_throwsException() {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("1000.00"));

        OrderItem item = new OrderItem();
        item.setId(50L);
        item.setOrder(order);
        item.setItemStatus(ItemStatus.ISSUED); // уже выдан
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));

        // Нельзя отменить выданный товар
        assertThatThrownBy(() -> bnplService.cancelItem(5L, 50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_ISSUE");
    }

    @Test
    void returnItem_issuedItem_refunds() throws Exception {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("1000.00"));

        Product product = new Product();
        product.setId(1L);

        OrderItem item = new OrderItem();
        item.setId(50L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrder(new BigDecimal("1000.00"));
        item.setItemStatus(ItemStatus.ISSUED); // уже выдан
        order.setItems(new ArrayList<>(List.of(item)));

        BnplContract contract = makeActiveContract(10L, user, 100_000L);
        contract.setOrder(order);
        contract.setDepositedAmountKopecks(25_000L); // первый взнос оплачен

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(contractRepo.findByOrder(order)).thenReturn(Optional.of(contract));
        when(orderItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JsonNode refundResp = objectMapper.readTree("{\"errorCode\":\"0\"}");
        when(gateway.refund(anyString(), anyLong())).thenReturn(refundResp);

        bnplService.returnItem(5L, 50L);

        assertThat(item.getItemStatus()).isEqualTo(ItemStatus.RETURNED);
        verify(gateway).refund(eq("alfa-pre-001"), anyLong());
    }

    @Test
    void returnItem_pendingItem_throwsException() {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("1000.00"));

        OrderItem item = new OrderItem();
        item.setId(50L);
        item.setOrder(order);
        item.setItemStatus(ItemStatus.PENDING_ISSUE); // не выдан
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));

        // Нельзя вернуть невыданный товар
        assertThatThrownBy(() -> bnplService.returnItem(5L, 50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("выданный");
    }
}
