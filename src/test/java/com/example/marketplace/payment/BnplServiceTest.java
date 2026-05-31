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

/**
 * Юнит-тесты ключевых методов BnplService (Mockito, без Spring-контекста):
 *   — перенос взноса (postponeInstallment): лимит 14 дней, комиссия, накопление;
 *   — досрочная/произвольная оплата (payInstallmentsNow): точное списание, частичный платёж,
 *     резолв реального bindingId, отказ при 3DS;
 *   — авто-списание планировщиком (processInstallment): успех / 3DS→OVERDUE / нет связки→skip;
 *   — статусы позиций (issue/cancel/return): reverse/refund доли, авто-закрытие заказа;
 *   — новый флоу инициации (initiate / confirmPreAuth): списывается ТОЛЬКО первый взнос.
 * LENIENT — часть общих стабов не используется в тестах с ранним throw.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BnplServiceTest {

    @Mock AlfaBankGatewayClient     gateway;
    @Mock AlfaBankProperties        props;
    @Mock AlfaBankOrderRepository   alfaBankOrderRepo;
    @Mock BnplContractRepository    contractRepo;
    @Mock BnplInstallmentRepository installmentRepo;
    @Mock BnplPaymentRepository     paymentRepo;
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

    // Полный график: count взносов по perInstallment коп., взнос №1 уже оплачен (deposited = perInstallment).
    private BnplContract makeContractWithSchedule(Long id, User user, long perInstallment, int count) {
        BnplContract c = makeActiveContract(id, user, perInstallment * count);
        c.setInstallmentCount(count);
        c.setDepositedAmountKopecks(perInstallment); // первый взнос оплачен
        List<BnplInstallment> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            BnplInstallment inst = makePendingInstallment(
                    (long) (100 + i), c, i, perInstallment, LocalDate.now().plusDays((long) (i - 1) * 30));
            if (i == 1) {
                inst.setStatus(BnplInstallmentStatus.PAID);
                inst.setPaidAt(LocalDateTime.now());
            }
            list.add(inst);
        }
        c.setInstallments(list);
        return c;
    }

    // Стабы успешного тихого списания через шлюз (register + paymentOrderBinding без 3DS).
    private void stubSilentCharge(long expectedKopecks, String bindingId) throws Exception {
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-x\"}"));
        when(gateway.paymentOrderBinding(anyString(), eq(expectedKopecks), eq(bindingId)))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-x\"}"));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
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

    // Перенос на чужом контракте → IllegalStateException «Нет доступа».
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
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4); // total 800_00, deposited 200_00, №1 PAID

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");
        card.setMaskedPan("411111**1111");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        stubSilentCharge(200_00L, "b-001"); // ближайший взнос №2 = 200_00

        List<BnplInstallmentResponse> result = bnplService.payInstallmentsNow(10L, null, user);

        // Возвращается весь график; списан ближайший взнос №2.
        assertThat(result).hasSize(4);
        assertThat(contract.getDepositedAmountKopecks()).isEqualTo(400_00L);
        assertThat(contract.getInstallments().get(1).getStatus()).isEqualTo(BnplInstallmentStatus.PAID);    // №2
        assertThat(contract.getInstallments().get(2).getStatus()).isEqualTo(BnplInstallmentStatus.PENDING); // №3
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("b-001"));
        verify(paymentRepo).save(any()); // платёж зафиксирован в журнале
    }

    @Test
    void payInstallmentsNow_customAmount_coversMultipleInstallments() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4); // deposited 200_00, №1 PAID

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        stubSilentCharge(400_00L, "b-001");

        // Оплачиваем произвольные 400 ₽ = два взноса (№2 и №3)
        List<BnplInstallmentResponse> result = bnplService.payInstallmentsNow(10L, 400_00L, user);

        assertThat(result).hasSize(4);
        assertThat(contract.getDepositedAmountKopecks()).isEqualTo(600_00L);
        long paidCount = contract.getInstallments().stream()
                .filter(i -> i.getStatus() == BnplInstallmentStatus.PAID).count();
        assertThat(paidCount).isEqualTo(3); // №1 (был) + №2 + №3
        assertThat(contract.getInstallments().get(3).getStatus()).isEqualTo(BnplInstallmentStatus.PENDING); // №4
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
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4);

        CardBinding card = new CardBinding();
        card.setBindingId("CARDAUTH-0211dd764fd37b99"); // синтетический — напрямую не списывается

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // getBindings.do отдаёт РЕАЛЬНУЮ связку клиента
        when(gateway.getBindings("user-1")).thenReturn(
                objectMapper.readTree("{\"errorCode\":\"0\",\"bindings\":[{\"bindingId\":\"real-99\"}]}"));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        when(gateway.paymentOrderBinding(anyString(), eq(200_00L), eq("real-99")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-1\"}"));

        bnplService.payInstallmentsNow(10L, null, user);

        // Ближайший взнос №2 списан по РЕАЛЬНОМУ bindingId, а не по синтетическому.
        verify(gateway).getBindings("user-1");
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("real-99"));
        assertThat(contract.getInstallments().get(1).getStatus()).isEqualTo(BnplInstallmentStatus.PAID);
    }

    @Test
    void payInstallmentsNow_threeDsResponse_throwsAndKeepsPending() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4);

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

        // Платёж не засчитан: депозит не изменился, журнал пуст, взнос №2 остаётся PENDING.
        assertThat(contract.getDepositedAmountKopecks()).isEqualTo(200_00L);
        assertThat(contract.getInstallments().get(1).getStatus()).isEqualTo(BnplInstallmentStatus.PENDING);
        verify(paymentRepo, never()).save(any());
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

    // Оплата по неактивному контракту (COMPLETED) → исключение «не активен».
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
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4); // deposited 200_00, №1 PAID

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        stubSilentCharge(100_00L, "b-001");

        // Платим 100 ₽ при взносе 200 ₽ → частичный платёж (предоплата)
        bnplService.payInstallmentsNow(10L, 100_00L, user);

        // Депозит вырос ровно на 100 ₽ (платёж зафиксирован), но взнос №2 ещё не покрыт целиком.
        assertThat(contract.getDepositedAmountKopecks()).isEqualTo(300_00L);
        assertThat(contract.getInstallments().get(1).getStatus()).isEqualTo(BnplInstallmentStatus.PENDING);
        verify(gateway).paymentOrderBinding(anyString(), eq(100_00L), eq("b-001"));
        verify(paymentRepo).save(any());
    }

    @Test
    void payInstallmentByAdmin_realBinding_chargesSilently() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4);

        CardBinding card = new CardBinding();
        card.setBindingId("b-001");

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        stubSilentCharge(200_00L, "b-001");

        // Админ платит без передачи user — владелец берётся из контракта.
        var res = bnplService.payInstallmentByAdmin(10L, null);

        assertThat(res.formUrl()).isNull(); // реальная связка → тихо, без формы
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("b-001"));
        assertThat(contract.getInstallments().get(1).getStatus()).isEqualTo(BnplInstallmentStatus.PAID);
    }

    // ── payInstallmentByClient (клиент: тихо или форма) ─────────────────────────

    // Реальная связка → тихое списание; formUrl == null, возвращается график.
    @Test
    void payInstallmentByClient_realBinding_chargesSilently() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4);

        CardBinding card = new CardBinding();
        card.setBindingId("b-001"); // реальный bindingId

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        stubSilentCharge(200_00L, "b-001");

        var res = bnplService.payInstallmentByClient(10L, null, user);

        assertThat(res.formUrl()).isNull();
        assertThat(res.installments()).hasSize(4);
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("b-001"));
        verify(gateway, never()).getBindings(anyString());
    }

    // Синтетическая связка + пустой getBindings → fallback на форму банка.
    @Test
    void payInstallmentByClient_noBinding_returnsFormUrl() throws Exception {
        User user = makeUser(1L);
        BnplContract contract = makeContractWithSchedule(10L, user, 200_00L, 4);

        CardBinding card = new CardBinding();
        card.setBindingId("CARDAUTH-0211dd764fd37b99"); // синтетический — не списывается напрямую

        when(contractRepo.findById(10L)).thenReturn(Optional.of(contract));
        when(cardService.getDefault(user)).thenReturn(Optional.of(card));
        // getBindings пуст → реальной связки нет
        when(gateway.getBindings("user-1"))
                .thenReturn(objectMapper.readTree("{\"errorCode\":\"2\",\"bindings\":[]}"));
        // регистрация одностадийного заказа на сумму взноса → formUrl
        when(gateway.registerOrderForBinding(anyString(), eq(200_00L), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree(
                        "{\"orderId\":\"md-form\",\"formUrl\":\"https://alfa.rbsuat.com/form?o=md-form\"}"));
        when(alfaBankOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = bnplService.payInstallmentByClient(10L, null, user);

        assertThat(res.installments()).isNull();
        assertThat(res.formUrl()).isEqualTo("https://alfa.rbsuat.com/form?o=md-form");
        // Тихое списание НЕ выполнялось — только регистрация формы.
        verify(gateway, never()).paymentOrderBinding(anyString(), anyLong(), anyString());
        verify(alfaBankOrderRepo).save(any());
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
        when(paymentRepo.save(any())).thenAnswer(invn -> invn.getArgument(0));
        when(gateway.registerOrderForBinding(anyString(), anyLong(), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"md-1\"}"));
        when(gateway.paymentOrderBinding(anyString(), eq(200_00L), eq("b-real")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"pay-1\"}"));

        bnplService.processInstallment(inst);

        assertThat(inst.getStatus()).isEqualTo(BnplInstallmentStatus.PAID);
        verify(gateway).paymentOrderBinding(anyString(), eq(200_00L), eq("b-real"));
        verify(paymentRepo).save(any()); // авто-списание зафиксировано в журнале
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
    void issueItem_pendingItem_changesStatusToIssued_withoutDeposit() {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);

        OrderItem item = new OrderItem();
        item.setId(50L);
        item.setOrder(order);
        item.setItemStatus(ItemStatus.PENDING_ISSUE);
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bnplService.issueItem(5L, 50L);

        // Статус товара → ISSUED
        assertThat(item.getItemStatus()).isEqualTo(ItemStatus.ISSUED);
        // Депозит первого взноса теперь делается при оплате (confirmPreAuth), НЕ при выдаче товара.
        verify(gateway, never()).deposit(anyString(), anyLong());
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
        // Единственная позиция отменена → заказ и контракт закрываются
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(contract.getStatus()).isEqualTo(BnplContractStatus.CANCELLED);
    }

    @Test
    void cancelItem_partialCancellation_orderAndContractStayActive() throws Exception {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(new BigDecimal("1000.00"));

        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("500.00"));

        OrderItem item1 = new OrderItem();
        item1.setId(50L);
        item1.setOrder(order);
        item1.setProduct(product);
        item1.setQuantity(1);
        item1.setPriceAtOrder(new BigDecimal("500.00"));
        item1.setItemStatus(ItemStatus.PENDING_ISSUE);

        OrderItem item2 = new OrderItem();
        item2.setId(51L);
        item2.setOrder(order);
        item2.setProduct(product);
        item2.setQuantity(1);
        item2.setPriceAtOrder(new BigDecimal("500.00"));
        item2.setItemStatus(ItemStatus.PENDING_ISSUE);

        order.setItems(new ArrayList<>(List.of(item1, item2)));

        BnplContract contract = makeActiveContract(10L, user, 100_000L);
        contract.setOrder(order);
        contract.setStatus(BnplContractStatus.ACTIVE);
        contract.setDepositedAmountKopecks(0L);

        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(contractRepo.findByOrder(order)).thenReturn(Optional.of(contract));
        when(orderItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.reverse(anyString(), anyLong()))
                .thenReturn(objectMapper.readTree("{\"errorCode\":\"0\"}"));

        // Отменяем только одну из двух позиций
        bnplService.cancelItem(5L, 50L);

        assertThat(item1.getItemStatus()).isEqualTo(ItemStatus.CANCELLED);
        // Вторая позиция ещё активна → заказ и контракт НЕ закрываются
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(contract.getStatus()).isEqualTo(BnplContractStatus.ACTIVE);
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
        // Возвращена единственная позиция → заказ и контракт закрываются
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(contract.getStatus()).isEqualTo(BnplContractStatus.CANCELLED);
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

    // ── initiate / confirmPreAuth (новый флоу: списываем первый взнос) ──────────

    @Test
    void initiate_chargesFirstInstallmentOnly() throws Exception {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setItems(new ArrayList<>());

        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setOrder(order);
        invoice.setAmount(BigDecimal.valueOf(800)); // 800 руб
        invoice.setStatus(InvoiceStatus.UNPAID);

        when(invoiceService.findEntityById(1L)).thenReturn(invoice);
        when(contractRepo.findByOrder(order)).thenReturn(Optional.empty());
        when(props.getReturnUrl()).thenReturn("http://ret");
        when(props.getFailUrl()).thenReturn("http://fail");
        when(contractRepo.save(any())).thenAnswer(inv -> {
            BnplContract c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.registerPreAuthForBinding(anyString(), anyLong(), any(), any(), eq("user-1")))
                .thenReturn(objectMapper.readTree("{\"orderId\":\"alfa-1\",\"formUrl\":\"http://form\"}"));

        var resp = bnplService.initiate(1L, "BIWEEKLY_4");

        // 800 руб, 0% → total = 80000 коп; первый взнос = 80000 / 4 = 20000 коп (200 руб).
        // На форму банка уходит ИМЕННО первый взнос, а не вся сумма заказа.
        verify(gateway).registerPreAuthForBinding(anyString(), eq(20_000L), any(), any(), eq("user-1"));
        verify(gateway, never()).registerPreAuth(anyString(), anyLong(), any(), any());
        assertThat(resp.formUrl()).isEqualTo("http://form");
    }

    @Test
    void confirmPreAuth_approved_depositsFirstInstallmentActivatesAndBindsCard() throws Exception {
        User user = makeUser(1L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setItems(new ArrayList<>());

        BnplContract contract = new BnplContract();
        contract.setId(10L);
        contract.setOrder(order);
        contract.setProduct(BnplProduct.BIWEEKLY_4);
        contract.setTotalAmountKopecks(80_000L);
        contract.setCommissionKopecks(0L);
        contract.setInstallmentCount(4);
        contract.setStatus(BnplContractStatus.AWAITING_PAYMENT);
        contract.setAlfaPreAuthOrderId("alfa-pre-001");
        contract.setDepositedAmountKopecks(0L);
        contract.setInstallments(new ArrayList<>());

        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setOrder(order);
        invoice.setAmount(BigDecimal.valueOf(800));
        invoice.setStatus(InvoiceStatus.UNPAID);

        BnplInstallment first = makePendingInstallment(100L, contract, 1, 20_000L, LocalDate.now());

        when(contractRepo.findByAlfaPreAuthOrderId("alfa-pre-001")).thenReturn(Optional.of(contract));
        // pre-auth APPROVED (orderStatus = 1) + bindingInfo
        when(gateway.getOrderStatusExtended("alfa-pre-001"))
                .thenReturn(objectMapper.readTree("{\"orderStatus\":1,\"bindingInfo\":{\"bindingId\":\"b-001\"}}"));
        when(gateway.deposit("alfa-pre-001", 20_000L))
                .thenReturn(objectMapper.readTree("{\"bindingInfo\":{\"bindingId\":\"b-001\"}}"));
        when(installmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(installmentRepo.findByContractAndInstallmentNumber(contract, 1)).thenReturn(Optional.of(first));
        when(invoiceRepo.findByOrder(order)).thenReturn(Optional.of(invoice));
        doNothing().when(invoiceService).markAsPaid(any(), anyString());
        doNothing().when(cardService).saveAfterPayment(any(), anyString(), any());

        String result = bnplService.confirmPreAuth("alfa-pre-001");

        assertThat(result).isEqualTo("paid");
        // Списан первый взнос (200 руб), а не вся сумма
        verify(gateway).deposit("alfa-pre-001", 20_000L);
        // Контракт активирован, первый взнос засчитан
        assertThat(contract.getStatus()).isEqualTo(BnplContractStatus.ACTIVE);
        assertThat(contract.getDepositedAmountKopecks()).isEqualTo(20_000L);
        assertThat(first.getStatus()).isEqualTo(BnplInstallmentStatus.PAID);
        // Счёт оплачен и карта привязана
        verify(invoiceService).markAsPaid(eq(invoice), anyString());
        verify(cardService).saveAfterPayment(eq(user), anyString(), any());
    }
}
