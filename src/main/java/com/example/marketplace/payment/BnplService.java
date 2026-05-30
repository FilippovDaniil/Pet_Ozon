package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.BnplContractResponse;
import com.example.marketplace.dto.response.BnplInstallmentResponse;
import com.example.marketplace.dto.response.BnplPaymentResponse;
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
    private final BnplPaymentRepository    paymentRepo;
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

        // Считаем итоговую сумму рассрочки с комиссией (для графика и прогресс-бара).
        BigDecimal orderTotal    = invoice.getAmount();
        BigDecimal commission    = orderTotal.multiply(product.commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bnplTotal     = orderTotal.add(commission);
        long       totalKopecks  = bnplTotal.multiply(BigDecimal.valueOf(100)).longValue();
        long       commKopecks   = commission.multiply(BigDecimal.valueOf(100)).longValue();

        // На форме банка холдируем и списываем ТОЛЬКО первый взнос.
        // Остальные взносы спишутся по графику с привязанной карты (планировщик / ручная оплата в ЛК).
        // Первый взнос = total / N — та же формула, что в buildInstallmentSchedule (base).
        long firstInstallmentKopecks = totalKopecks / product.installmentCount;

        String orderNumber = "BNPL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        // clientId обязателен: двухстадийный pre-auth + deposit гарантированно возвращает
        // bindingInfo → карта клиента привяжется к ЛК для авто-списания следующих взносов.
        String clientId = "user-" + order.getUser().getId();
        JsonNode response = gateway.registerPreAuthForBinding(
                orderNumber, firstInstallmentKopecks,
                props.getReturnUrl(), props.getFailUrl(), clientId
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
        alfaRecord.setAmountKopecks(firstInstallmentKopecks);  // фактически холдируемая/списываемая сумма
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

        log.info("ACTION=BNPL_INITIATE invoiceId={} product={} totalKopecks={} firstInstallmentKopecks={} alfaOrderId={}",
                invoiceId, product, totalKopecks, firstInstallmentKopecks, alfaOrderId);

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

        if (contract.getStatus() == BnplContractStatus.ACTIVE
                || contract.getStatus() == BnplContractStatus.COMPLETED) return "paid";   // идемпотентность
        if (contract.getStatus() == BnplContractStatus.CANCELLED)        return "failed";

        JsonNode statusNode = gateway.getOrderStatusExtended(alfaOrderId);
        int orderStatus = statusNode.path("orderStatus").asInt(-1);

        if (orderStatus == 1) {
            // APPROVED — первый взнос захолдирован. Списываем его (deposit), строим график,
            // активируем контракт, оплачиваем счёт и привязываем карту клиента.
            activateContractWithFirstInstallment(contract);
            log.info("ACTION=BNPL_PRE_AUTH_CONFIRMED contractId={}", contract.getId());
            return "paid";
        }

        if (orderStatus == 6) {
            contract.setStatus(BnplContractStatus.CANCELLED);
            contractRepo.save(contract);
            return "failed";
        }

        return "pending";
    }

    // ─── Управление товарами ───────────────────────────────────────────────────

    /** Выдача товара. Чисто статус фулфилмента — первый взнос уже списан при оформлении рассрочки. */
    @Transactional
    public void issueItem(Long orderId, Long itemId) {
        OrderItem item = getOwnedItem(orderId, itemId);
        if (item.getItemStatus() != ItemStatus.PENDING_ISSUE) {
            throw new IllegalStateException("Нельзя выдать товар со статусом: " + item.getItemStatus());
        }

        // Депозит первого взноса выполняется в confirmPreAuth() при оплате на форме банка,
        // поэтому здесь только меняем статус позиции.
        item.setItemStatus(ItemStatus.ISSUED);
        orderItemRepo.save(item);

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

        // Если после возврата все позиции отменены/возвращены — закрываем контракт и заказ.
        checkAndCancelContractIfAllCancelled(contract);
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

        long remainingBalance = contract.getTotalAmountKopecks() - contract.getDepositedAmountKopecks();
        if (remainingBalance <= 0) {
            throw new IllegalStateException("Рассрочка уже полностью оплачена");
        }

        // Сумма к списанию: либо явная (произвольный платёж), либо ближайший непогашенный взнос.
        long chargeKopecks;
        boolean manualAmount = amountKopecks != null;
        if (manualAmount) {
            if (amountKopecks < 5000) {   // минимум 50 ₽
                throw new IllegalStateException("Минимальная сумма оплаты — 50 ₽");
            }
            chargeKopecks = amountKopecks;
        } else {
            BnplInstallment next = contract.getInstallments().stream()
                    .filter(i -> i.getStatus() == BnplInstallmentStatus.PENDING)
                    .min(java.util.Comparator.comparing(BnplInstallment::getInstallmentNumber))
                    .orElseThrow(() -> new IllegalStateException("Нет взносов для оплаты"));
            chargeKopecks = next.getAmountKopecks();
        }
        // Никогда не списываем больше остатка по контракту (защита от переплаты).
        // Излишек сверх целого взноса не теряется — он идёт в депозит как предоплата
        // и засчитывается следующим взносам через syncInstallmentStatuses().
        chargeKopecks = Math.min(chargeKopecks, remainingBalance);

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
                orderNumber, chargeKopecks, props.getReturnUrl(), props.getFailUrl(), clientId);
        String mdOrder = registered.path("orderId").asText(null);
        if (mdOrder == null || mdOrder.isBlank()) {
            throw new IllegalStateException("Не удалось зарегистрировать заказ в шлюзе");
        }

        // Шаг 2: тихое списание по связке (tii=U внутри gateway → без CVC/3DS)
        JsonNode result = gateway.paymentOrderBinding(mdOrder, chargeKopecks, bindingId);
        String acsUrl = result.path("acsUrl").asText(null);
        if (acsUrl != null && !acsUrl.isBlank()) {
            // tii=U должен исключать 3DS. Если банк всё же запросил подтверждение —
            // значит связка не настроена на автоплатежи; платёж не засчитываем.
            throw new IllegalStateException("Банк запросил подтверждение 3DS — тихое списание недоступно для этой карты");
        }
        String alfaOrderId = result.path("orderId").asText(mdOrder);

        // Увеличиваем депозит, фиксируем платёж в журнале и пересчитываем статусы взносов.
        contract.setDepositedAmountKopecks(contract.getDepositedAmountKopecks() + chargeKopecks);
        recordPayment(contract, chargeKopecks, "MANUAL",
                manualAmount ? "Произвольный платёж" : "Оплата взноса", alfaOrderId);
        syncInstallmentStatuses(contract);
        contractRepo.save(contract);

        log.info("ACTION=BNPL_MANUAL_PAY contractId={} chargedKopecks={} depositedKopecks={}",
                contractId, chargeKopecks, contract.getDepositedAmountKopecks());

        return contract.getInstallments().stream()
                .map(this::toInstallmentResponse).toList();
    }

    /**
     * Списание взносов по дефолтной карте от имени администратора.
     * amountKopecks == null → ближайший взнос; > 0 → произвольная сумма.
     * Владелец берётся из контракта (проверка прав в payInstallmentsNow проходит автоматически).
     */
    @Transactional
    public List<BnplInstallmentResponse> payInstallmentsByAdmin(Long contractId, Long amountKopecks) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        return payInstallmentsNow(contractId, amountKopecks, contract.getOrder().getUser());
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

        // Уже оплачен (в т.ч. предоплатой) или отменён — списывать нечего.
        if (installment.getStatus() == BnplInstallmentStatus.PAID
                || installment.getStatus() == BnplInstallmentStatus.CANCELLED) {
            return;
        }

        // Не списываем больше остатка по контракту.
        long remaining = contract.getTotalAmountKopecks() - contract.getDepositedAmountKopecks();
        if (remaining <= 0) {
            syncInstallmentStatuses(contract);   // депозит уже покрывает контракт
            contractRepo.save(contract);
            return;
        }

        String clientId = "user-" + contract.getOrder().getUser().getId();
        // Реальный bindingId: из контракта (production) либо из getBindings.do (UAT).
        String bindingId = resolveRecurrentBindingId(contract.getBindingId(), clientId);
        if (bindingId == null) {
            log.warn("No bindingId for contractId={}, skip installment {}", contract.getId(),
                    installment.getInstallmentNumber());
            return;
        }

        long chargeKopecks = Math.min(installment.getAmountKopecks(), remaining);
        String orderNumber = "INST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        try {
            // Шаг 1: register.do с clientId → получаем mdOrder
            JsonNode registered = gateway.registerOrderForBinding(
                    orderNumber, chargeKopecks,
                    props.getReturnUrl(), props.getFailUrl(), clientId);
            String mdOrder = registered.path("orderId").asText(null);
            if (mdOrder == null || mdOrder.isBlank()) {
                throw new IllegalStateException("Не удалось зарегистрировать заказ в шлюзе");
            }

            // Шаг 2: тихое списание по связке (tii=U → без CVC/3DS)
            JsonNode result = gateway.paymentOrderBinding(mdOrder, chargeKopecks, bindingId);
            if (!result.path("acsUrl").asText("").isBlank()) {
                throw new IllegalStateException("Банк запросил 3DS — тихое списание недоступно");
            }

            String alfaOrderId = result.path("orderId").asText(mdOrder);
            installment.setAlfaOrderId(alfaOrderId);

            // Фиксируем платёж в журнале, увеличиваем депозит и пересчитываем статусы взносов.
            contract.setDepositedAmountKopecks(contract.getDepositedAmountKopecks() + chargeKopecks);
            recordPayment(contract, chargeKopecks, "SCHEDULED",
                    "Авто-списание взноса №" + installment.getInstallmentNumber(), alfaOrderId);
            syncInstallmentStatuses(contract);
            contractRepo.save(contract);

            log.info("ACTION=BNPL_INSTALLMENT_PAID contractId={} number={} kopecks={}",
                    contract.getId(), installment.getInstallmentNumber(), chargeKopecks);
        } catch (Exception e) {
            installment.setStatus(BnplInstallmentStatus.OVERDUE);
            installmentRepo.save(installment);
            log.error("ACTION=BNPL_INSTALLMENT_FAILED contractId={} number={} error={}",
                    contract.getId(), installment.getInstallmentNumber(), e.getMessage());
        }
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    /**
     * Активирует контракт после успешного pre-auth первого взноса:
     * строит график, списывает (deposit) захолдированный первый взнос, активирует контракт,
     * помечает первый взнос оплаченным, оплачивает счёт и привязывает карту клиента.
     */
    private void activateContractWithFirstInstallment(BnplContract contract) {
        // 1. Активируем контракт и строим график (первый взнос = total / N).
        contract.setStatus(BnplContractStatus.ACTIVE);
        buildInstallmentSchedule(contract);

        BnplInstallment first = installmentRepo
                .findByContractAndInstallmentNumber(contract, 1)
                .orElseThrow(() -> new IllegalStateException("Не найден 1-й взнос контракта"));

        // 2. Списываем захолдированный первый взнос.
        JsonNode depositResult = gateway.deposit(contract.getAlfaPreAuthOrderId(), first.getAmountKopecks());

        // 3. Читаем статус с bindingInfo — для привязки карты и авто-списания следующих взносов.
        JsonNode statusAfter = gateway.getOrderStatusExtended(contract.getAlfaPreAuthOrderId());
        String bindingId = depositResult.path("bindingInfo").path("bindingId").asText(null);
        if (bindingId == null || bindingId.isBlank()) {
            bindingId = statusAfter.path("bindingInfo").path("bindingId").asText(null);
        }
        contract.setBindingId(bindingId);
        contract.setDepositedAmountKopecks(first.getAmountKopecks());
        contractRepo.save(contract);

        // 4. Первый взнос оплачен.
        first.setStatus(BnplInstallmentStatus.PAID);
        first.setPaidAt(LocalDateTime.now());
        first.setAlfaOrderId(contract.getAlfaPreAuthOrderId());
        installmentRepo.save(first);

        // Фиксируем первый взнос в журнале платежей.
        recordPayment(contract, first.getAmountKopecks(), "FIRST", "Первый взнос",
                contract.getAlfaPreAuthOrderId());

        // 5. Счёт оплачен (первый взнос = подтверждение заказа) → заказ PAID, продавцы получают деньги.
        Invoice invoice = invoiceRepo.findByOrder(contract.getOrder())
                .orElseThrow(() -> new IllegalStateException("Invoice не найден для контракта"));
        invoiceService.markAsPaid(invoice, "BNPL_CARD");

        // 6. Привязываем карту клиента (для авто-списания взносов и оплаты в ЛК).
        //    Устойчиво: bindingInfo → cardAuthInfo → getBindings.
        cardService.saveAfterPayment(contract.getOrder().getUser(), contract.getAlfaPreAuthOrderId(), statusAfter);

        // 7. Обновляем запись Альфа Банка.
        final String savedBindingId = bindingId;
        alfaBankOrderRepo.findByAlfaOrderId(contract.getAlfaPreAuthOrderId()).ifPresent(r -> {
            r.setStatus(AlfaBankOrderStatus.DEPOSITED);
            r.setBindingId(savedBindingId);
            alfaBankOrderRepo.save(r);
        });

        log.info("ACTION=BNPL_FIRST_INSTALLMENT_PAID contractId={} kopecks={} bindingId={}",
                contract.getId(), first.getAmountKopecks(), bindingId);
    }

    /** Фиксирует фактический платёж в журнале BNPL-транзакций. */
    private BnplPayment recordPayment(BnplContract contract, long amountKopecks,
                                      String method, String description, String alfaOrderId) {
        BnplPayment p = new BnplPayment();
        p.setContract(contract);
        p.setAmountKopecks(amountKopecks);
        p.setMethod(method);
        p.setDescription(description);
        p.setAlfaOrderId(alfaOrderId);
        p.setPaidAt(LocalDateTime.now());
        return paymentRepo.save(p);
    }

    /**
     * Приводит статусы взносов в соответствие с фактически внесённой суммой (depositedAmountKopecks):
     * взнос k считается оплаченным, когда накопленная сумма взносов 1..k покрыта депозитом.
     * Так частичные/произвольные платежи учитываются корректно, а излишек переходит на следующие взносы.
     * Если депозит покрывает все взносы — контракт переводится в COMPLETED.
     */
    private void syncInstallmentStatuses(BnplContract contract) {
        long deposited  = contract.getDepositedAmountKopecks();
        long cumulative = 0;
        List<BnplInstallment> sorted = contract.getInstallments().stream()
                .filter(i -> i.getStatus() != BnplInstallmentStatus.CANCELLED)
                .sorted(java.util.Comparator.comparing(BnplInstallment::getInstallmentNumber))
                .toList();
        for (BnplInstallment inst : sorted) {
            cumulative += inst.getAmountKopecks();
            if (cumulative <= deposited && inst.getStatus() != BnplInstallmentStatus.PAID) {
                inst.setStatus(BnplInstallmentStatus.PAID);
                if (inst.getPaidAt() == null) inst.setPaidAt(LocalDateTime.now());
                installmentRepo.save(inst);
            }
        }
        boolean allDone = contract.getInstallments().stream()
                .allMatch(i -> i.getStatus() == BnplInstallmentStatus.PAID
                            || i.getStatus() == BnplInstallmentStatus.CANCELLED);
        if (allDone) {
            contract.setStatus(BnplContractStatus.COMPLETED);
        }
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
        Order order = contract.getOrder();
        boolean hasItems = !order.getItems().isEmpty();
        boolean allCancelledOrReturned = hasItems && order.getItems().stream()
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

            // Все позиции отменены/возвращены → заказ тоже переходит в финальный статус CANCELLED.
            // Иначе он завис бы в CREATED и остался видимым в списке «Мои заказы».
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepo.save(order);
            }
            log.info("ACTION=BNPL_CONTRACT_CANCELLED contractId={} orderId={}",
                    contract.getId(), order.getId());
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

        // История фактических платежей по контракту.
        List<BnplPaymentResponse> payList = paymentRepo.findByContractOrderByPaidAtAsc(c).stream()
                .map(p -> new BnplPaymentResponse(
                        p.getId(),
                        p.getAmountKopecks(),
                        p.getMethod(),
                        p.getDescription(),
                        p.getAlfaOrderId(),
                        p.getPaidAt() != null ? p.getPaidAt().toString() : null))
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
                instList,
                payList);
    }
}
