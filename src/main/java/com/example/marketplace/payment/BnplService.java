package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.BnplContractResponse;
import com.example.marketplace.dto.response.BnplInstallmentResponse;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.*;
import com.example.marketplace.entity.enums.*;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.*;
import com.example.marketplace.service.CardService;
import com.example.marketplace.service.InvoiceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сервис BNPL-рассрочки.
 *
 * Схема:
 *   initiate()        → registerPreAuth.do (холд полной BNPL-суммы) → formUrl
 *   confirmPreAuth()  → getOrderStatusExtended.do → если APPROVED → контракт ACTIVE
 *   issueItem()       → первый вызов → deposit.do на 1-й взнос + сохранить bindingId
 *   cancelItem()      → reverse.do на долю товара (если deposit ещё не был)
 *   returnItem()      → refund.do на долю товара (после deposit)
 *
 * Последующие взносы 2..N списываются автоматически планировщиком BnplSchedulerService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BnplService {

    private final AlfaBankGatewayClient    gateway;
    private final AlfaBankProperties       props;
    private final AlfaBankOrderRepository  alfaBankOrderRepo;
    private final BnplContractRepository   contractRepo;
    private final BnplInstallmentRepository installmentRepo;
    private final InvoiceService           invoiceService;
    private final OrderRepository          orderRepo;
    private final OrderItemRepository      orderItemRepo;
    private final InvoiceRepository        invoiceRepo;
    private final CardService              cardService;

    // ─── Инициация ────────────────────────────────────────────────────────────

    @Transactional
    public PaymentInitResponse initiate(Long invoiceId, String bnplProductName) {
        Invoice invoice = invoiceService.findEntityById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Счёт #" + invoiceId + " уже оплачен");
        }

        BnplProduct product = BnplProduct.valueOf(bnplProductName.toUpperCase());
        Order order = invoice.getOrder();

        // Если контракт уже есть в статусе AWAITING_PAYMENT — вернуть его формUrl повторно.
        // Это бывает когда прошлый pre-auth не был подтверждён (returnUrl указывал на неверный порт).
        var existing = contractRepo.findByOrder(order);
        if (existing.isPresent()) {
            BnplContract ex = existing.get();
            if (ex.getStatus() == BnplContractStatus.AWAITING_PAYMENT) {
                // Регистрируем новый pre-auth поверх старого контракта.
                contractRepo.delete(ex);
            } else {
                throw new IllegalStateException("По заказу #" + order.getId() + " уже создан BNPL-контракт");
            }
        }

        // Считаем итоговую сумму с комиссией.
        BigDecimal orderTotal    = invoice.getAmount();
        BigDecimal commission    = orderTotal.multiply(product.commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bnplTotal     = orderTotal.add(commission);
        long       totalKopecks  = bnplTotal.multiply(BigDecimal.valueOf(100)).longValue();
        long       commKopecks   = commission.multiply(BigDecimal.valueOf(100)).longValue();

        String orderNumber = "BNPL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        JsonNode response = gateway.registerPreAuth(
                orderNumber, totalKopecks,
                props.getReturnUrl(), props.getFailUrl()
        );

        String alfaOrderId = response.path("orderId").asText();
        String formUrl     = response.path("formUrl").asText();

        // Создаём контракт.
        BnplContract contract = new BnplContract();
        contract.setOrder(order);
        contract.setProduct(product);
        contract.setTotalAmountKopecks(totalKopecks);
        contract.setCommissionKopecks(commKopecks);
        contract.setInstallmentCount(product.installmentCount);
        contract.setStatus(BnplContractStatus.AWAITING_PAYMENT);
        contract.setAlfaPreAuthOrderId(alfaOrderId);
        contract = contractRepo.save(contract);

        // Создаём AlfaBankOrder для pre-auth операции.
        AlfaBankOrder alfaRecord = new AlfaBankOrder();
        alfaRecord.setOrderNumber(orderNumber);
        alfaRecord.setAlfaOrderId(alfaOrderId);
        alfaRecord.setBnplContract(contract);
        alfaRecord.setAmountKopecks(totalKopecks);
        alfaRecord.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        alfaRecord.setFormUrl(formUrl);
        alfaBankOrderRepo.save(alfaRecord);

        // Помечаем все позиции заказа как ожидающие выдачи.
        for (OrderItem item : order.getItems()) {
            item.setItemStatus(ItemStatus.PENDING_ISSUE);
        }
        order.setPaymentType(switch (product) {
            case BIWEEKLY_4 -> PaymentType.BNPL_4_BIWEEKLY;
            case MONTHLY_4  -> PaymentType.BNPL_4_MONTHLY;
            case MONTHLY_6  -> PaymentType.BNPL_6_MONTHLY;
        });
        orderRepo.save(order);

        log.info("ACTION=BNPL_INITIATE invoiceId={} product={} totalKopecks={} alfaOrderId={}",
                invoiceId, product, totalKopecks, alfaOrderId);

        return new PaymentInitResponse(formUrl, alfaOrderId, contract.getId());
    }

    // ─── Подтверждение pre-auth (callback) ────────────────────────────────────

    /**
     * Вызывается из PaymentController.callback().
     * Бросает IllegalArgumentException если alfaOrderId не BNPL — controller пробует Full.
     */
    @Transactional
    public String confirmPreAuth(String alfaOrderId) {
        BnplContract contract = contractRepo.findByAlfaPreAuthOrderId(alfaOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Not a BNPL pre-auth: " + alfaOrderId));

        if (contract.getStatus() == BnplContractStatus.ACTIVE)     return "paid";
        if (contract.getStatus() == BnplContractStatus.CANCELLED)  return "failed";

        JsonNode statusNode = gateway.getOrderStatusExtended(alfaOrderId);
        int orderStatus = statusNode.path("orderStatus").asInt(-1);

        if (orderStatus == 1) {
            // APPROVED — деньги захолдированы, активируем контракт и создаём график.
            contract.setStatus(BnplContractStatus.ACTIVE);
            buildInstallmentSchedule(contract);
            contractRepo.save(contract);

            // Обновляем запись Альфа Банка.
            alfaBankOrderRepo.findByAlfaOrderId(alfaOrderId).ifPresent(r -> {
                r.setStatus(AlfaBankOrderStatus.APPROVED);
                alfaBankOrderRepo.save(r);
            });

            // Счёт пока остаётся UNPAID — станет PAID после выдачи первого товара.
            log.info("ACTION=BNPL_PRE_AUTH_CONFIRMED contractId={}", contract.getId());
            return "paid";  // клиент видит «успех» — деньги захолдированы
        }

        if (orderStatus == 6) {
            contract.setStatus(BnplContractStatus.CANCELLED);
            contractRepo.save(contract);
            return "failed";
        }

        return "pending";
    }

    // ─── Управление товарами ───────────────────────────────────────────────────

    /** Выдача товара. Первая выдача — deposit 1-го взноса + сохранить bindingId. */
    @Transactional
    public void issueItem(Long orderId, Long itemId) {
        OrderItem item = getOwnedItem(orderId, itemId);
        if (item.getItemStatus() != ItemStatus.PENDING_ISSUE) {
            throw new IllegalStateException("Нельзя выдать товар со статусом: " + item.getItemStatus());
        }

        item.setItemStatus(ItemStatus.ISSUED);
        orderItemRepo.save(item);

        BnplContract contract = getContract(item.getOrder());

        // Если первый взнос ещё не задепозирован — делаем это сейчас.
        if (contract.getDepositedAmountKopecks() == 0L) {
            depositFirstInstallment(contract);
        }

        log.info("ACTION=BNPL_ITEM_ISSUED orderId={} itemId={}", orderId, itemId);
    }

    /** Отмена товара (до выдачи). Частичный reverse на долю этого товара. */
    @Transactional
    public void cancelItem(Long orderId, Long itemId) {
        OrderItem item = getOwnedItem(orderId, itemId);
        if (item.getItemStatus() != ItemStatus.PENDING_ISSUE) {
            throw new IllegalStateException("Отменить можно только товар со статусом PENDING_ISSUE");
        }

        BnplContract contract = getContract(item.getOrder());
        long itemShare = calculateItemShareKopecks(item, contract);

        item.setItemStatus(ItemStatus.CANCELLED);
        orderItemRepo.save(item);

        if (contract.getDepositedAmountKopecks() == 0L) {
            // Deposit ещё не был — можно сделать частичный reverse.
            try {
                gateway.reverse(contract.getAlfaPreAuthOrderId(), itemShare);
                log.info("ACTION=BNPL_ITEM_CANCELLED_REVERSE orderId={} itemId={} kopecks={}",
                        orderId, itemId, itemShare);
            } catch (Exception e) {
                log.warn("Reverse failed for itemId={}: {}", itemId, e.getMessage());
            }
        }

        // Если все товары отменены — отменяем контракт целиком.
        checkAndCancelContractIfAllCancelled(contract);
    }

    /** Возврат товара (после выдачи). Refund на долю товара. */
    @Transactional
    public void returnItem(Long orderId, Long itemId) {
        OrderItem item = getOwnedItem(orderId, itemId);
        if (item.getItemStatus() != ItemStatus.ISSUED) {
            throw new IllegalStateException("Вернуть можно только выданный товар");
        }

        BnplContract contract = getContract(item.getOrder());
        long itemShare = calculateItemShareKopecks(item, contract);

        item.setItemStatus(ItemStatus.RETURNED);
        orderItemRepo.save(item);

        try {
            gateway.refund(contract.getAlfaPreAuthOrderId(), itemShare);
            log.info("ACTION=BNPL_ITEM_RETURNED_REFUND orderId={} itemId={} kopecks={}",
                    orderId, itemId, itemShare);
        } catch (Exception e) {
            log.warn("Refund failed for itemId={}: {}", itemId, e.getMessage());
        }
    }

    // ─── Перенос взноса ──────────────────────────────────────────────────────

    /**
     * Переносит dueDate ближайшего PENDING взноса контракта на указанное кол-во дней.
     * Комиссия = amountKopecks × 0.0005 × days, прибавляется к сумме взноса.
     * Суммарный перенос не может превысить 14 дней.
     */
    @Transactional
    public BnplInstallmentResponse postponeInstallment(Long contractId, int days, User user) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        if (!contract.getOrder().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к контракту #" + contractId);
        }
        if (contract.getStatus() != BnplContractStatus.ACTIVE) {
            throw new IllegalStateException("Перенос недоступен: контракт не активен");
        }

        // Находим ближайший PENDING взнос
        BnplInstallment inst = contract.getInstallments().stream()
                .filter(i -> i.getStatus() == BnplInstallmentStatus.PENDING)
                .min(java.util.Comparator.comparing(BnplInstallment::getDueDate))
                .orElseThrow(() -> new IllegalStateException("Нет активных взносов для переноса"));

        int alreadyUsed = inst.getDaysPostponed() == null ? 0 : inst.getDaysPostponed();
        if (alreadyUsed + days > 14) {
            throw new IllegalStateException(
                    "Превышен лимит переноса. Использовано: " + alreadyUsed + " дней, доступно: " + (14 - alreadyUsed));
        }

        // Комиссия: 0.05% от суммы взноса за каждый день переноса
        long feeKopecks = Math.round(inst.getAmountKopecks() * 0.0005 * days);

        inst.setDueDate(inst.getDueDate().plusDays(days));
        inst.setDaysPostponed(alreadyUsed + days);
        inst.setAmountKopecks(inst.getAmountKopecks() + feeKopecks);
        inst.setPostponeFeePaidKopecks(inst.getPostponeFeePaidKopecks() + feeKopecks);
        installmentRepo.save(inst);

        log.info("ACTION=BNPL_INSTALLMENT_POSTPONED contractId={} installmentId={} days={} feeKopecks={}",
                contractId, inst.getId(), days, feeKopecks);

        return toInstallmentResponse(inst);
    }

    // ─── Досрочная оплата ─────────────────────────────────────────────────────

    /**
     * Тихое списание взносов по привязанной карте (MIT, без участия клиента).
     * amountKopecks == null → оплатить ближайший PENDING взнос.
     * amountKopecks > 0    → покрыть столько взносов, сколько вмещает сумма (жадно от ближайшего).
     *
     * Списание идёт через paymentOrderBinding.do с tii=U (Merchant Initiated Transaction):
     * связка списывается без CVC и без 3DS — мандат дан клиентом при привязке карты.
     */
    @Transactional
    public List<BnplInstallmentResponse> payInstallmentsNow(Long contractId, Long amountKopecks, User user) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        if (!contract.getOrder().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к контракту #" + contractId);
        }
        if (contract.getStatus() != BnplContractStatus.ACTIVE) {
            throw new IllegalStateException("Оплата недоступна: контракт не активен");
        }

        CardBinding card = cardService.getDefault(user)
                .orElseThrow(() -> new IllegalStateException("Нет привязанной карты. Добавьте карту через оплату заказа."));

        List<BnplInstallment> pending = contract.getInstallments().stream()
                .filter(i -> i.getStatus() == BnplInstallmentStatus.PENDING)
                .sorted(java.util.Comparator.comparing(BnplInstallment::getDueDate))
                .toList();

        if (pending.isEmpty()) {
            throw new IllegalStateException("Нет взносов для оплаты");
        }
        // Минимальная сумма 50 ₽ (5000 коп.)
        if (amountKopecks != null && amountKopecks < 5000) {
            throw new IllegalStateException("Минимальная сумма оплаты — 50 ₽");
        }

        // Какие взносы покрывает сумма (частичный платёж < одного взноса допустим).
        List<BnplInstallment> toPay;
        if (amountKopecks == null) {
            toPay = List.of(pending.get(0));
        } else {
            long remaining = amountKopecks;
            toPay = new java.util.ArrayList<>();
            for (BnplInstallment inst : pending) {
                if (remaining >= inst.getAmountKopecks()) {
                    toPay.add(inst);
                    remaining -= inst.getAmountKopecks();
                } else {
                    break;
                }
            }
        }
        long totalKopecks = toPay.isEmpty()
                ? (amountKopecks != null ? amountKopecks : pending.get(0).getAmountKopecks())
                : toPay.stream().mapToLong(BnplInstallment::getAmountKopecks).sum();

        String clientId = "user-" + user.getId();

        // Реальный bindingId на стороне банка. Синтетический "CARDAUTH-" не списывается —
        // в этом случае берём настоящую связку клиента через getBindings.do.
        String bindingId = resolveRecurrentBindingId(card, clientId);
        if (bindingId == null) {
            throw new IllegalStateException("Не найдена связка карты для списания. Привяжите карту заново.");
        }

        String orderNumber = "INST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        // Шаг 1: register.do с clientId → mdOrder
        JsonNode registered = gateway.registerOrderForBinding(
                orderNumber, totalKopecks, props.getReturnUrl(), props.getFailUrl(), clientId);
        String mdOrder = registered.path("orderId").asText(null);
        if (mdOrder == null || mdOrder.isBlank()) {
            throw new IllegalStateException("Не удалось зарегистрировать заказ в шлюзе");
        }

        // Шаг 2: тихое списание по связке (tii=U внутри gateway → без CVC/3DS)
        JsonNode result = gateway.paymentOrderBinding(mdOrder, totalKopecks, bindingId);
        String acsUrl = result.path("acsUrl").asText(null);
        if (acsUrl != null && !acsUrl.isBlank()) {
            // tii=U должен исключать 3DS. Если банк всё же запросил подтверждение —
            // значит связка не настроена на автоплатежи; не помечаем взносы оплаченными.
            throw new IllegalStateException("Банк запросил подтверждение 3DS — тихое списание недоступно для этой карты");
        }
        String alfaOrderId = result.path("orderId").asText(mdOrder);

        // Помечаем полностью покрытые взносы оплаченными.
        java.util.List<BnplInstallmentResponse> paid = new java.util.ArrayList<>();
        for (BnplInstallment inst : toPay) {
            inst.setStatus(BnplInstallmentStatus.PAID);
            inst.setPaidAt(LocalDateTime.now());
            inst.setAlfaOrderId(alfaOrderId);
            installmentRepo.save(inst);
            paid.add(toInstallmentResponse(inst));
        }

        // Обновляем общий депозит (в т.ч. для частичных платежей) и статус контракта.
        contract.setDepositedAmountKopecks(contract.getDepositedAmountKopecks() + totalKopecks);
        boolean allPaid = contract.getInstallments().stream()
                .allMatch(i -> i.getStatus() == BnplInstallmentStatus.PAID
                            || i.getStatus() == BnplInstallmentStatus.CANCELLED);
        if (allPaid) {
            contract.setStatus(BnplContractStatus.COMPLETED);
        }
        contractRepo.save(contract);

        log.info("ACTION=BNPL_MANUAL_PAY contractId={} paidInstallments={} totalKopecks={}",
                contractId, toPay.size(), totalKopecks);

        return paid.isEmpty()
                ? contract.getInstallments().stream().map(this::toInstallmentResponse).toList()
                : paid;
    }

    // Реальный bindingId для списания. Синтетический "CARDAUTH-" в шлюзе не существует —
    // в этом случае берём настоящую связку из getBindings.do (актуально для UAT).
    private String resolveRecurrentBindingId(CardBinding card, String clientId) {
        return resolveRecurrentBindingId(card.getBindingId(), clientId);
    }

    private String resolveRecurrentBindingId(String storedBindingId, String clientId) {
        if (storedBindingId != null && !storedBindingId.startsWith("CARDAUTH-")) {
            return storedBindingId;  // реальный bindingId из bindingInfo (production)
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

    // ─── BNPL-кабинет (чтение) ───────────────────────────────────────────────

    public List<BnplContractResponse> getContractsForUser(User user) {
        return contractRepo.findByUser(user).stream().map(this::toResponse).toList();
    }

    public BnplContractResponse getContractById(Long contractId, User user) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        if (!contract.getOrder().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к контракту #" + contractId);
        }
        return toResponse(contract);
    }

    public BnplContractResponse getContractByIdAdmin(Long contractId) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        return toResponse(contract);
    }

    // ─── Авто-списание взноса (вызывается планировщиком) ─────────────────────

    @Transactional
    public void processInstallment(BnplInstallment installment) {
        BnplContract contract = installment.getContract();
        String clientId = "user-" + contract.getOrder().getUser().getId();

        // Реальный bindingId: из контракта (production) либо из getBindings.do (UAT).
        String bindingId = resolveRecurrentBindingId(contract.getBindingId(), clientId);
        if (bindingId == null) {
            log.warn("No bindingId for contractId={}, skip installment {}", contract.getId(),
                    installment.getInstallmentNumber());
            return;
        }

        String orderNumber = "INST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        try {
            // Шаг 1: register.do с clientId → получаем mdOrder
            JsonNode registered = gateway.registerOrderForBinding(
                    orderNumber, installment.getAmountKopecks(),
                    props.getReturnUrl(), props.getFailUrl(), clientId);
            String mdOrder = registered.path("orderId").asText(null);
            if (mdOrder == null || mdOrder.isBlank()) {
                throw new IllegalStateException("Не удалось зарегистрировать заказ в шлюзе");
            }

            // Шаг 2: тихое списание по связке (tii=U → без CVC/3DS)
            JsonNode result = gateway.paymentOrderBinding(
                    mdOrder, installment.getAmountKopecks(), bindingId);
            if (!result.path("acsUrl").asText("").isBlank()) {
                throw new IllegalStateException("Банк запросил 3DS — тихое списание недоступно");
            }

            String alfaOrderId = result.path("orderId").asText(mdOrder);
            installment.setAlfaOrderId(alfaOrderId);
            installment.setStatus(BnplInstallmentStatus.PAID);
            installment.setPaidAt(LocalDateTime.now());
            installmentRepo.save(installment);

            log.info("ACTION=BNPL_INSTALLMENT_PAID contractId={} number={} kopecks={}",
                    contract.getId(), installment.getInstallmentNumber(), installment.getAmountKopecks());

            // Если все взносы оплачены — закрываем контракт.
            boolean allPaid = contract.getInstallments().stream()
                    .allMatch(i -> i.getStatus() == BnplInstallmentStatus.PAID
                               || i.getStatus() == BnplInstallmentStatus.CANCELLED);
            if (allPaid) {
                contract.setStatus(BnplContractStatus.COMPLETED);
                contractRepo.save(contract);
            }
        } catch (Exception e) {
            installment.setStatus(BnplInstallmentStatus.OVERDUE);
            installmentRepo.save(installment);
            log.error("ACTION=BNPL_INSTALLMENT_FAILED contractId={} number={} error={}",
                    contract.getId(), installment.getInstallmentNumber(), e.getMessage());
        }
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    private void depositFirstInstallment(BnplContract contract) {
        BnplInstallment first = installmentRepo
                .findByContractAndInstallmentNumber(contract, 1)
                .orElseThrow(() -> new IllegalStateException("Не найден 1-й взнос контракта"));

        JsonNode result = gateway.deposit(contract.getAlfaPreAuthOrderId(), first.getAmountKopecks());

        // Сохраняем bindingId для авто-списания следующих взносов.
        String bindingId = result.path("bindingInfo").path("bindingId").asText(null);
        if (bindingId == null) {
            // Альфа возвращает bindingId через getOrderStatusExtended после deposit
            JsonNode status = gateway.getOrderStatusExtended(contract.getAlfaPreAuthOrderId());
            bindingId = status.path("bindingInfo").path("bindingId").asText(null);
        }
        contract.setBindingId(bindingId);
        contract.setDepositedAmountKopecks(first.getAmountKopecks());
        contractRepo.save(contract);

        first.setStatus(BnplInstallmentStatus.PAID);
        first.setPaidAt(LocalDateTime.now());
        installmentRepo.save(first);

        // Помечаем счёт как оплаченный (первый взнос = оплата).
        Invoice invoice = invoiceRepo.findByOrder(contract.getOrder())
                .orElseThrow(() -> new IllegalStateException("Invoice не найден для контракта"));
        invoiceService.markAsPaid(invoice, "BNPL_CARD");

        // Сохраняем привязку карты пользователя для автосписаний.
        JsonNode statusForCard = gateway.getOrderStatusExtended(contract.getAlfaPreAuthOrderId());
        cardService.saveFromStatusResponse(contract.getOrder().getUser(), statusForCard);

        final String savedBindingId = bindingId;
        alfaBankOrderRepo.findByAlfaOrderId(contract.getAlfaPreAuthOrderId()).ifPresent(r -> {
            r.setStatus(AlfaBankOrderStatus.DEPOSITED);
            r.setBindingId(savedBindingId);
            alfaBankOrderRepo.save(r);
        });

        log.info("ACTION=BNPL_FIRST_DEPOSIT_DONE contractId={} kopecks={} bindingId={}",
                contract.getId(), first.getAmountKopecks(), bindingId);
    }

    private void buildInstallmentSchedule(BnplContract contract) {
        int    n       = contract.getInstallmentCount();
        long   total   = contract.getTotalAmountKopecks();
        long   base    = total / n;
        long   rem     = total % n;  // остаток — добавляем к последнему взносу
        LocalDate date = LocalDate.now();

        for (int i = 1; i <= n; i++) {
            BnplInstallment inst = new BnplInstallment();
            inst.setContract(contract);
            inst.setInstallmentNumber(i);
            inst.setAmountKopecks(i == n ? base + rem : base);
            inst.setDueDate(i == 1 ? date : date.plusDays((long) (i - 1) * contract.getProduct().intervalDays));
            inst.setStatus(BnplInstallmentStatus.PENDING);
            installmentRepo.save(inst);
        }
    }

    /**
     * Пропорциональная доля товара в BNPL-сумме (с комиссией).
     * itemTotal / orderTotal × bnplTotal
     */
    private long calculateItemShareKopecks(OrderItem item, BnplContract contract) {
        BigDecimal itemTotal  = item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity()));
        BigDecimal orderTotal = item.getOrder().getTotalAmount();
        if (orderTotal.compareTo(BigDecimal.ZERO) == 0) return 0L;
        BigDecimal share = itemTotal.divide(orderTotal, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(contract.getTotalAmountKopecks()));
        return share.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private void checkAndCancelContractIfAllCancelled(BnplContract contract) {
        boolean allCancelledOrReturned = contract.getOrder().getItems().stream()
                .allMatch(i -> i.getItemStatus() == ItemStatus.CANCELLED
                            || i.getItemStatus() == ItemStatus.RETURNED);
        if (allCancelledOrReturned) {
            contract.setStatus(BnplContractStatus.CANCELLED);
            contract.getInstallments().forEach(inst -> {
                if (inst.getStatus() == BnplInstallmentStatus.PENDING) {
                    inst.setStatus(BnplInstallmentStatus.CANCELLED);
                    installmentRepo.save(inst);
                }
            });
            contractRepo.save(contract);
            log.info("ACTION=BNPL_CONTRACT_CANCELLED contractId={}", contract.getId());
        }
    }

    private OrderItem getOwnedItem(Long orderId, Long itemId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Заказ не найден: " + orderId));
        return order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Позиция не найдена: " + itemId));
    }

    private BnplContract getContract(Order order) {
        return contractRepo.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("BNPL-контракт не найден для заказа #" + order.getId()));
    }

    public BnplInstallmentResponse toInstallmentResponse(BnplInstallment i) {
        int postponed = i.getDaysPostponed() == null ? 0 : i.getDaysPostponed();
        return new BnplInstallmentResponse(
                i.getId(), i.getInstallmentNumber(), i.getAmountKopecks(),
                i.getDueDate(), i.getStatus().name(),
                i.getPaidAt() != null ? i.getPaidAt().toString() : null,
                postponed, 14 - postponed);
    }

    public BnplContractResponse toResponse(BnplContract c) {
        List<BnplInstallmentResponse> instList = c.getInstallments().stream()
                .map(i -> new BnplInstallmentResponse(
                        i.getId(),
                        i.getInstallmentNumber(),
                        i.getAmountKopecks(),
                        i.getDueDate(),
                        i.getStatus().name(),
                        i.getPaidAt() != null ? i.getPaidAt().toString() : null,
                        i.getDaysPostponed(),
                        14 - i.getDaysPostponed()))
                .toList();
        return new BnplContractResponse(
                c.getId(),
                c.getOrder().getId(),
                c.getProduct().name(),
                c.getProduct().description,
                c.getTotalAmountKopecks(),
                c.getCommissionKopecks(),
                c.getInstallmentCount(),
                c.getStatus().name(),
                c.getDepositedAmountKopecks(),
                instList);
    }
}
