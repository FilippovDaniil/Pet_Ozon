package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.example.marketplace.dto.response.BnplContractResponse;
import com.example.marketplace.dto.response.BnplInstallmentResponse;
import com.example.marketplace.dto.response.BnplPayResponse;
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
 *   issueUnits()      → выдача N единиц позиции (статус фулфилмента; деньги уже списаны)
 *   cancelUnits()     → reverse.do на долю N единиц (если deposit ещё не был)
 *   returnUnits()     → refund.do на долю N единиц (после deposit)
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

    /**
     * Запускает оформление рассрочки по счёту.
     * Холдирует на форме банка ТОЛЬКО первый взнос (total / N), создаёт контракт
     * в статусе AWAITING_PAYMENT и возвращает formUrl для редиректа клиента.
     * График и активация — позже, в confirmPreAuth() после прохождения формы.
     */
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

    // ─── Управление товарами (поштучно) ─────────────────────────────────────────
    //
    // Каждая физическая единица позиции управляется отдельно. count — сколько единиц
    // обработать за вызов (фронт шлёт 1 на клик). Статусы хранятся счётчиками на OrderItem:
    // pending → issued (выдача), pending → cancelled (отмена), issued → returned (возврат).

    /** Выдача N единиц позиции. Чисто статус фулфилмента — первый взнос уже списан при оформлении. */
    @Transactional
    public void issueUnits(Long orderId, Long itemId, int count) {
        OrderItem item = getOwnedItem(orderId, itemId);
        requireManaged(item);
        int n = normalizeCount(count, item.getPendingCount(), "Нет единиц, ожидающих выдачи");

        // Депозит первого взноса выполняется в confirmPreAuth(), поэтому здесь только статус.
        item.setIssuedCount(item.getIssuedCount() + n);
        recomputeItemStatus(item);
        orderItemRepo.save(item);
        recalcOrderStatus(getContract(item.getOrder()));

        log.info("ACTION=BNPL_UNITS_ISSUED orderId={} itemId={} count={}", orderId, itemId, n);
    }

    /** Отмена N единиц (до выдачи). Частичный reverse на долю этих единиц (если deposit ещё не был). */
    @Transactional
    public void cancelUnits(Long orderId, Long itemId, int count) {
        OrderItem item = getOwnedItem(orderId, itemId);
        requireManaged(item);
        int n = normalizeCount(count, item.getPendingCount(),
                "Нет единиц для отмены (отменить можно только ожидающие выдачи)");

        BnplContract contract = getContract(item.getOrder());
        long share = calculateShareKopecks(item, contract, n);

        item.setCancelledCount(item.getCancelledCount() + n);
        recomputeItemStatus(item);
        orderItemRepo.save(item);

        if (contract.getDepositedAmountKopecks() == 0L) {
            // Deposit ещё не был — можно сделать частичный reverse на долю отменённых единиц.
            try {
                gateway.reverse(contract.getAlfaPreAuthOrderId(), share);
                log.info("ACTION=BNPL_UNITS_CANCELLED_REVERSE orderId={} itemId={} count={} kopecks={}",
                        orderId, itemId, n, share);
            } catch (Exception e) {
                log.warn("Reverse failed for itemId={}: {}", itemId, e.getMessage());
            }
        }

        // Если все единицы заказа отменены/возвращены — закрываем контракт; статус заказа пересчитываем.
        checkAndCancelContractIfAllCancelled(contract);
        recalcOrderStatus(contract);
    }

    /** Возврат N единиц (после выдачи). Refund на долю этих единиц. */
    @Transactional
    public void returnUnits(Long orderId, Long itemId, int count) {
        OrderItem item = getOwnedItem(orderId, itemId);
        requireManaged(item);
        int n = normalizeCount(count, item.getIssuedCount(), "Нет выданных единиц для возврата");

        BnplContract contract = getContract(item.getOrder());
        long share = calculateShareKopecks(item, contract, n);

        item.setIssuedCount(item.getIssuedCount() - n);
        item.setReturnedCount(item.getReturnedCount() + n);
        recomputeItemStatus(item);
        orderItemRepo.save(item);

        try {
            gateway.refund(contract.getAlfaPreAuthOrderId(), share);
            log.info("ACTION=BNPL_UNITS_RETURNED_REFUND orderId={} itemId={} count={} kopecks={}",
                    orderId, itemId, n, share);
        } catch (Exception e) {
            log.warn("Refund failed for itemId={}: {}", itemId, e.getMessage());
        }

        // Если после возврата все единицы отменены/возвращены — закрываем контракт; статус пересчитываем.
        checkAndCancelContractIfAllCancelled(contract);
        recalcOrderStatus(contract);
    }

    /** Позиция должна быть под управлением фулфилмента (BNPL): itemStatus != null. */
    private void requireManaged(OrderItem item) {
        if (item.getItemStatus() == null) {
            throw new IllegalStateException("Позиция не относится к заказу с фулфилментом (нет управления выдачей)");
        }
    }

    /** Нормализует запрошенное кол-во единиц: минимум 1, не больше доступного. */
    private int normalizeCount(int requested, int available, String emptyMessage) {
        int n = requested <= 0 ? 1 : requested;   // по умолчанию — одна единица
        if (available <= 0) throw new IllegalStateException(emptyMessage);
        if (n > available) {
            throw new IllegalStateException("Запрошено " + n + " ед., доступно только " + available);
        }
        return n;
    }

    /**
     * Пересчитывает обобщённый бейдж позиции из счётчиков (для отображения и как маркер управляемости).
     * Пока есть ожидающие — PENDING_ISSUE; иначе если есть выданные — ISSUED; иначе при наличии
     * возвратов — RETURNED; иначе всё отменено — CANCELLED.
     */
    private void recomputeItemStatus(OrderItem item) {
        if (item.getPendingCount() > 0)      item.setItemStatus(ItemStatus.PENDING_ISSUE);
        else if (item.getIssuedCount() > 0)  item.setItemStatus(ItemStatus.ISSUED);
        else if (item.getReturnedCount() > 0) item.setItemStatus(ItemStatus.RETURNED);
        else                                  item.setItemStatus(ItemStatus.CANCELLED);
    }

    // ─── Перенос взноса ──────────────────────────────────────────────────────

    /**
     * Перенос ближайшего PENDING-взноса на {@code days} дней.
     *
     * Перенос — это полноценная оплата комиссии ЗДЕСЬ И СЕЙЧАС (а не раздувание следующего взноса):
     *   • есть привязанная карта → тихое списание комиссии (MIT) → дата сдвигается сразу;
     *   • связки нет → возвращаем formUrl: клиент платит комиссию через форму банка,
     *     дата сдвигается в callback (confirmPostponeForm).
     *
     * Ограничения: контракт ACTIVE, нет просроченных взносов, суммарный перенос ≤ 14 дней,
     * за раз 3..14 дней (валидируется в DTO). Комиссия = взнос × 0.05% × дней, минимум 1 ₽.
     */
    @Transactional
    public BnplPayResponse postponeInstallment(Long contractId, int days, User user) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        if (!contract.getOrder().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к контракту #" + contractId);
        }
        if (contract.getStatus() != BnplContractStatus.ACTIVE) {
            throw new IllegalStateException("Перенос недоступен: контракт не активен");
        }
        // Просроченные взносы переносить нельзя — их нужно оплатить.
        boolean hasOverdue = contract.getInstallments().stream()
                .anyMatch(i -> i.getStatus() == BnplInstallmentStatus.OVERDUE);
        if (hasOverdue) {
            throw new IllegalStateException("Перенос недоступен: есть просроченный взнос — сначала оплатите его");
        }

        // Ближайший непогашенный взнос.
        BnplInstallment inst = contract.getInstallments().stream()
                .filter(i -> i.getStatus() == BnplInstallmentStatus.PENDING)
                .min(java.util.Comparator.comparing(BnplInstallment::getDueDate))
                .orElseThrow(() -> new IllegalStateException("Нет активных взносов для переноса"));

        int alreadyUsed = inst.getDaysPostponed() == null ? 0 : inst.getDaysPostponed();
        if (alreadyUsed + days > 14) {
            throw new IllegalStateException(
                    "Превышен лимит переноса. Использовано: " + alreadyUsed + " дней, доступно: " + (14 - alreadyUsed));
        }

        // Комиссия: 0.05% от суммы взноса за каждый день переноса, минимум 1 ₽ (валидная сумма для шлюза).
        long feeKopecks = Math.max(Math.round(inst.getAmountKopecks() * 0.0005 * days), 100L);

        String clientId = "user-" + user.getId();
        CardBinding card = cardService.getDefault(user).orElse(null);
        String bindingId = (card == null) ? null : resolveRecurrentBindingId(card, clientId);

        if (bindingId != null) {
            // Тихое списание комиссии по привязанной карте → сразу применяем перенос.
            String alfaOrderId = chargePostponeFeeRecurrent(feeKopecks, clientId, bindingId);
            applyPostpone(inst, days, feeKopecks);
            installmentRepo.save(inst);
            recordPayment(contract, feeKopecks, "POSTPONE",
                    "Комиссия за перенос взноса №" + inst.getInstallmentNumber() + " на " + days + " дн.", alfaOrderId);
            log.info("ACTION=BNPL_POSTPONE_CHARGED contractId={} installmentId={} days={} feeKopecks={}",
                    contractId, inst.getId(), days, feeKopecks);
            return BnplPayResponse.charged(contract.getInstallments().stream()
                    .map(this::toInstallmentResponse).toList());
        }

        // Связки нет → оплата комиссии через форму банка; перенос применится в callback.
        String formUrl = initiatePostponeForm(contract, inst, days, feeKopecks, clientId);
        log.info("ACTION=BNPL_POSTPONE_FORM contractId={} installmentId={} days={} feeKopecks={}",
                contractId, inst.getId(), days, feeKopecks);
        return BnplPayResponse.redirect(formUrl);
    }

    /**
     * Однократный пересчёт статуса всех BNPL-заказов (вызывается при старте из AppConfig).
     * Нужен для заказов, созданных ДО введения статусной модели: их order.status мог остаться
     * устаревшим (например, PAID при активной рассрочке). Идемпотентно — сохраняет только при изменении.
     */
    @Transactional
    public void recalcAllOrderStatuses() {
        int changed = 0;
        for (BnplContract c : contractRepo.findAll()) {
            OrderStatus before = c.getOrder().getStatus();
            recalcOrderStatus(c);
            if (c.getOrder().getStatus() != before) changed++;
        }
        if (changed > 0) log.info("Recalculated order status for {} BNPL orders on startup", changed);
    }

    /** Сдвигает дату взноса и фиксирует использованные дни/уплаченную комиссию. Сумму взноса НЕ меняет. */
    private void applyPostpone(BnplInstallment inst, int days, long feeKopecks) {
        int already  = inst.getDaysPostponed() == null ? 0 : inst.getDaysPostponed();
        long feePaid = inst.getPostponeFeePaidKopecks() == null ? 0L : inst.getPostponeFeePaidKopecks();
        inst.setDueDate(inst.getDueDate().plusDays(days));
        inst.setDaysPostponed(already + days);
        inst.setPostponeFeePaidKopecks(feePaid + feeKopecks);
    }

    /** Тихое списание комиссии переноса по связке (MIT, без 3DS). Возвращает alfaOrderId. */
    private String chargePostponeFeeRecurrent(long feeKopecks, String clientId, String bindingId) {
        String orderNumber = "PSTP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        JsonNode reg = gateway.registerOrderForBinding(
                orderNumber, feeKopecks, props.getReturnUrl(), props.getFailUrl(), clientId);
        String mdOrder = reg.path("orderId").asText(null);
        if (mdOrder == null || mdOrder.isBlank()) {
            throw new IllegalStateException("Не удалось зарегистрировать платёж комиссии в шлюзе");
        }
        JsonNode result = gateway.paymentOrderBinding(mdOrder, feeKopecks, bindingId);
        if (!result.path("acsUrl").asText("").isBlank()) {
            throw new IllegalStateException("Банк запросил 3DS — тихое списание комиссии недоступно");
        }
        return result.path("orderId").asText(mdOrder);
    }

    /** Регистрирует одностадийный заказ на сумму комиссии (PSTP-) и staging-запись с днями переноса. */
    private String initiatePostponeForm(BnplContract contract, BnplInstallment inst, int days,
                                        long feeKopecks, String clientId) {
        String orderNumber = "PSTP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        JsonNode reg = gateway.registerOrderForBinding(
                orderNumber, feeKopecks, props.getReturnUrl(), props.getFailUrl(), clientId);
        String alfaOrderId = reg.path("orderId").asText(null);
        String formUrl     = reg.path("formUrl").asText(null);
        if (alfaOrderId == null || alfaOrderId.isBlank() || formUrl == null || formUrl.isBlank()) {
            throw new IllegalStateException("Не удалось зарегистрировать платёж комиссии в шлюзе");
        }
        AlfaBankOrder rec = new AlfaBankOrder();
        rec.setOrderNumber(orderNumber);
        rec.setAlfaOrderId(alfaOrderId);
        rec.setBnplContract(contract);
        rec.setBnplInstallment(inst);
        rec.setPostponeDays(days);
        rec.setAmountKopecks(feeKopecks);
        rec.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        rec.setFormUrl(formUrl);
        alfaBankOrderRepo.save(rec);
        return formUrl;
    }

    /**
     * Подтверждение оплаты комиссии переноса через форму банка (PSTP-). Применяет перенос.
     * Бросает IllegalArgumentException если alfaOrderId не относится к переносу — controller пробует дальше.
     */
    @Transactional
    public String confirmPostponeForm(String alfaOrderId) {
        AlfaBankOrder rec = alfaBankOrderRepo.findByAlfaOrderId(alfaOrderId)
                .filter(r -> r.getOrderNumber() != null && r.getOrderNumber().startsWith("PSTP-")
                        && r.getBnplInstallment() != null && r.getPostponeDays() != null)
                .orElseThrow(() -> new IllegalArgumentException("Not a postpone form payment: " + alfaOrderId));

        if (rec.getStatus() == AlfaBankOrderStatus.DEPOSITED) return "paid";   // идемпотентность
        if (rec.getStatus() == AlfaBankOrderStatus.FAILED)    return "failed";

        JsonNode status = gateway.getOrderStatusExtended(alfaOrderId);
        int orderStatus = status.path("orderStatus").asInt(-1);

        if (orderStatus == 2) {  // комиссия списана
            BnplInstallment inst   = rec.getBnplInstallment();
            BnplContract    contract = rec.getBnplContract();
            applyPostpone(inst, rec.getPostponeDays(), rec.getAmountKopecks());
            installmentRepo.save(inst);
            recordPayment(contract, rec.getAmountKopecks(), "POSTPONE",
                    "Комиссия за перенос взноса №" + inst.getInstallmentNumber() + " на " + rec.getPostponeDays() + " дн.",
                    alfaOrderId);
            rec.setStatus(AlfaBankOrderStatus.DEPOSITED);
            alfaBankOrderRepo.save(rec);
            // Если клиент отметил «сохранить карту» — сохраним (только реальный bindingInfo).
            cardService.saveFromStatusResponse(contract.getOrder().getUser(), status);
            log.info("ACTION=BNPL_POSTPONE_FORM_PAID contractId={} installmentId={} days={} feeKopecks={}",
                    contract.getId(), inst.getId(), rec.getPostponeDays(), rec.getAmountKopecks());
            return "paid";
        }
        if (orderStatus == 6) {  // отклонено
            rec.setStatus(AlfaBankOrderStatus.FAILED);
            alfaBankOrderRepo.save(rec);
            return "failed";
        }
        return "pending";
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

        boolean manualAmount = amountKopecks != null;
        // Сумма к списанию: явная (произвольный платёж) либо ближайший взнос, с защитой от переплаты.
        long chargeKopecks = resolveChargeKopecks(contract, amountKopecks);

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
     * Клиентская оплата взноса. Сначала пытается тихо списать по привязанной карте (MIT);
     * если реальной связки нет (UAT-аккаунт без автоплатежей либо карта не привязана на
     * стороне банка) — возвращает formUrl: клиент платит взнос через форму банка
     * (с вводом карты и 3DS), как первый платёж.
     */
    @Transactional
    public BnplPayResponse payInstallmentByClient(Long contractId, Long amountKopecks, User user) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        if (!contract.getOrder().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Нет доступа к контракту #" + contractId);
        }
        if (contract.getStatus() != BnplContractStatus.ACTIVE) {
            throw new IllegalStateException("Оплата недоступна: контракт не активен");
        }

        String clientId = "user-" + user.getId();
        CardBinding card = cardService.getDefault(user).orElse(null);
        String bindingId = (card == null) ? null : resolveRecurrentBindingId(card, clientId);

        if (bindingId != null) {
            // Реальная связка есть → тихое списание (как раньше).
            return BnplPayResponse.charged(payInstallmentsNow(contractId, amountKopecks, user));
        }

        // Связки для тихого списания нет → оплата взноса через форму банка.
        long chargeKopecks = resolveChargeKopecks(contract, amountKopecks);
        String formUrl = initiateInstallmentForm(contract, chargeKopecks, clientId);
        return BnplPayResponse.redirect(formUrl);
    }

    /**
     * Подтверждение оплаты взноса, сделанной через форму банка (fallback без связки).
     * Вызывается из PaymentController.callback(). Узнаёт «свои» заказы по префиксу INSTF-.
     * Бросает IllegalArgumentException если alfaOrderId не относится к довзносу — controller пробует дальше.
     */
    @Transactional
    public String confirmInstallmentForm(String alfaOrderId) {
        AlfaBankOrder rec = alfaBankOrderRepo.findByAlfaOrderId(alfaOrderId)
                .filter(r -> r.getBnplContract() != null
                        && r.getOrderNumber() != null
                        && r.getOrderNumber().startsWith("INSTF-"))
                .orElseThrow(() -> new IllegalArgumentException("Not a BNPL installment form payment: " + alfaOrderId));

        if (rec.getStatus() == AlfaBankOrderStatus.DEPOSITED) return "paid";    // идемпотентность
        if (rec.getStatus() == AlfaBankOrderStatus.FAILED)    return "failed";

        JsonNode status = gateway.getOrderStatusExtended(alfaOrderId);
        int orderStatus = status.path("orderStatus").asInt(-1);

        if (orderStatus == 2) { // DEPOSITED — одностадийный платёж списан
            BnplContract contract = rec.getBnplContract();
            long remaining = contract.getTotalAmountKopecks() - contract.getDepositedAmountKopecks();
            long charge = Math.min(rec.getAmountKopecks(), Math.max(remaining, 0L));
            if (charge > 0) {
                contract.setDepositedAmountKopecks(contract.getDepositedAmountKopecks() + charge);
                recordPayment(contract, charge, "MANUAL", "Оплата взноса через форму банка", alfaOrderId);
                syncInstallmentStatuses(contract);
                contractRepo.save(contract);
            }
            rec.setStatus(AlfaBankOrderStatus.DEPOSITED);
            rec.setBindingId(status.path("bindingInfo").path("bindingId").asText(null));
            alfaBankOrderRepo.save(rec);

            // Сохраняем карту только при РЕАЛЬНОМ bindingInfo (production) — тогда следующие
            // взносы спишутся тихо. В UAT bindingInfo пуст → метод сам выходит, синтетический
            // дубликат не создаётся (иначе каждый платёж через форму плодил бы новую карту).
            cardService.saveFromStatusResponse(contract.getOrder().getUser(), status);

            log.info("ACTION=BNPL_INSTALLMENT_FORM_PAID contractId={} kopecks={} alfaOrderId={}",
                    contract.getId(), charge, alfaOrderId);
            return "paid";
        }
        if (orderStatus == 6) { // DECLINED
            rec.setStatus(AlfaBankOrderStatus.FAILED);
            alfaBankOrderRepo.save(rec);
            return "failed";
        }
        return "pending";
    }

    /**
     * Регистрирует одностадийный заказ на сумму взноса и сохраняет staging-запись
     * (AlfaBankOrder с префиксом INSTF- и ссылкой на контракт). Возвращает formUrl.
     */
    private String initiateInstallmentForm(BnplContract contract, long chargeKopecks, String clientId) {
        String orderNumber = "INSTF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        JsonNode reg = gateway.registerOrderForBinding(
                orderNumber, chargeKopecks, props.getReturnUrl(), props.getFailUrl(), clientId);
        String alfaOrderId = reg.path("orderId").asText(null);
        String formUrl     = reg.path("formUrl").asText(null);
        if (alfaOrderId == null || alfaOrderId.isBlank() || formUrl == null || formUrl.isBlank()) {
            throw new IllegalStateException("Не удалось зарегистрировать заказ в шлюзе");
        }

        AlfaBankOrder rec = new AlfaBankOrder();
        rec.setOrderNumber(orderNumber);
        rec.setAlfaOrderId(alfaOrderId);
        rec.setBnplContract(contract);
        rec.setAmountKopecks(chargeKopecks);
        rec.setStatus(AlfaBankOrderStatus.FORM_SHOWN);
        rec.setFormUrl(formUrl);
        alfaBankOrderRepo.save(rec);

        log.info("ACTION=BNPL_INSTALLMENT_FORM_INITIATED contractId={} kopecks={} alfaOrderId={}",
                contract.getId(), chargeKopecks, alfaOrderId);
        return formUrl;
    }

    /**
     * Сумма к списанию: явная (произвольный платёж, минимум 50 ₽) либо ближайший
     * непогашенный взнос; всегда ограничена остатком по контракту (защита от переплаты).
     */
    private long resolveChargeKopecks(BnplContract contract, Long amountKopecks) {
        long remainingBalance = contract.getTotalAmountKopecks() - contract.getDepositedAmountKopecks();
        if (remainingBalance <= 0) {
            throw new IllegalStateException("Рассрочка уже полностью оплачена");
        }
        long chargeKopecks;
        if (amountKopecks != null) {
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
        // Излишек сверх целого взноса не теряется — идёт в депозит как предоплата
        // и засчитывается следующим взносам через syncInstallmentStatuses().
        return Math.min(chargeKopecks, remainingBalance);
    }

    /**
     * Оплата взноса от имени администратора (кнопка «Оплата с карты клиента»).
     * Владелец берётся из контракта. Поведение как у клиента: есть реальная связка →
     * тихое списание; нет → возвращается formUrl (админ проходит форму банка).
     * amountKopecks == null → ближайший взнос; > 0 → произвольная сумма.
     */
    @Transactional
    public BnplPayResponse payInstallmentByAdmin(Long contractId, Long amountKopecks) {
        BnplContract contract = contractRepo.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Контракт не найден: " + contractId));
        return payInstallmentByClient(contractId, amountKopecks, contract.getOrder().getUser());
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

    /**
     * Тихое авто-списание одного взноса с привязанной карты (вызывает BnplSchedulerService).
     * Списывает min(сумма взноса, остаток контракта) через MIT (tii=U) без 3DS,
     * пишет платёж в журнал и пересчитывает статусы. При сбое — взнос становится OVERDUE.
     */
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

        // markAsPaid() выставил заказу PAID, но рассрочка ещё активна и товары не выданы —
        // пересчитываем реальный статус (станет CREATED: всё ожидает выдачи).
        recalcOrderStatus(contract);

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
        // Пересчитываем статус заказа: при COMPLETED + всё выдано → PAID (финал, скрыт в ЛК).
        recalcOrderStatus(contract);
    }

    /**
     * Строит график из N равных взносов. Сумма делится поровну (base),
     * остаток от деления (rem) добавляется к последнему взносу, чтобы Σ взносов = total.
     * Первый взнос — сегодня, остальные — с шагом intervalDays продукта.
     */
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
            inst.setAmountKopecks(i == n ? base + rem : base);   // последнему взносу — остаток
            // Взнос №1 — сразу, остальные сдвигаются на (i-1) интервалов вперёд.
            inst.setDueDate(i == 1 ? date : date.plusDays((long) (i - 1) * contract.getProduct().intervalDays));
            inst.setStatus(BnplInstallmentStatus.PENDING);
            installmentRepo.save(inst);
        }
    }

    /**
     * Пропорциональная доля {@code units} единиц позиции в BNPL-сумме (с комиссией).
     * (priceAtOrder × units) / orderTotal × bnplTotal
     */
    private long calculateShareKopecks(OrderItem item, BnplContract contract, int units) {
        BigDecimal itemTotal  = item.getPriceAtOrder().multiply(BigDecimal.valueOf(units));
        BigDecimal orderTotal = item.getOrder().getTotalAmount();
        if (orderTotal.compareTo(BigDecimal.ZERO) == 0) return 0L;
        BigDecimal share = itemTotal.divide(orderTotal, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(contract.getTotalAmountKopecks()));
        return share.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private void checkAndCancelContractIfAllCancelled(BnplContract contract) {
        Order order = contract.getOrder();
        boolean hasItems = !order.getItems().isEmpty();
        // Все единицы всех позиций разрешены не в пользу выдачи: нет ни ожидающих, ни выданных
        // (т.е. каждая единица либо отменена, либо возвращена).
        boolean allCancelledOrReturned = hasItems && order.getItems().stream()
                .allMatch(i -> i.getPendingCount() == 0 && i.getIssuedCount() == 0);
        if (allCancelledOrReturned) {
            contract.setStatus(BnplContractStatus.CANCELLED);
            contract.getInstallments().forEach(inst -> {
                if (inst.getStatus() == BnplInstallmentStatus.PENDING) {
                    inst.setStatus(BnplInstallmentStatus.CANCELLED);
                    installmentRepo.save(inst);
                }
            });
            contractRepo.save(contract);
            // Финальный статус заказа (CANCELLED при полной отмене / RETURNED при возвратах)
            // проставит recalcOrderStatus у вызывающего метода.
            log.info("ACTION=BNPL_CONTRACT_CANCELLED contractId={} orderId={}",
                    contract.getId(), order.getId());
        }
    }

    /**
     * Пересчитывает статус заказа из поштучных счётчиков всех позиций и статуса контракта.
     * Только для управляемых (BNPL) заказов. Финальный DELIVERED (ручная отметка админа) не трогаем.
     *
     * Приоритет (сверху вниз, первое совпадение):
     *   1. все отменены                                   → CANCELLED (финал)
     *   2. нет активных, был возврат                      → RETURNED  (финал)
     *   3. всё выдано и рассрочка погашена (COMPLETED)    → PAID      (финал)
     *   4. есть возвраты, но остались активные единицы    → PARTIALLY_RETURNED
     *   5. всё выдано, но ещё не доплачено                → ISSUED
     *   6. часть выдана, часть ожидает                    → PARTIALLY_ISSUED
     *   7. что-то отменено, выданных нет                  → PARTIALLY_CANCELLED
     *   8. иначе (всё ожидает / ждёт оплаты формы)        → CREATED
     */
    private void recalcOrderStatus(BnplContract contract) {
        Order order = contract.getOrder();
        if (order.getStatus() == OrderStatus.DELIVERED) return; // legacy ручной финал — не перетираем
        if (order.getItems() == null) return;

        int total = 0, issued = 0, cancelled = 0, returned = 0;
        boolean managed = false;
        for (OrderItem i : order.getItems()) {
            if (i.getItemStatus() == null) continue;  // позиция без фулфилмента
            managed = true;
            total     += i.getQuantity();
            issued    += i.getIssuedCount();
            cancelled += i.getCancelledCount();
            returned  += i.getReturnedCount();
        }
        if (!managed || total == 0) return;
        int pending = total - issued - cancelled - returned;
        boolean completed = contract.getStatus() == BnplContractStatus.COMPLETED;

        OrderStatus s;
        if (cancelled == total)                               s = OrderStatus.CANCELLED;
        else if (issued == 0 && pending == 0 && returned > 0) s = OrderStatus.RETURNED;
        else if (pending == 0 && issued > 0 && completed)     s = OrderStatus.PAID;
        else if (returned > 0)                                s = OrderStatus.PARTIALLY_RETURNED;
        else if (pending == 0 && issued > 0)                  s = OrderStatus.ISSUED;
        else if (issued > 0)                                  s = OrderStatus.PARTIALLY_ISSUED;
        else if (cancelled > 0)                               s = OrderStatus.PARTIALLY_CANCELLED;
        else                                                  s = OrderStatus.CREATED;

        if (order.getStatus() != s) {
            order.setStatus(s);
            orderRepo.save(order);
            log.info("ACTION=ORDER_STATUS_RECALC orderId={} status={}", order.getId(), s);
        }
    }

    /** Находит позицию заказа по id и проверяет, что она принадлежит этому заказу. */
    private OrderItem getOwnedItem(Long orderId, Long itemId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Заказ не найден: " + orderId));
        return order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Позиция не найдена: " + itemId));
    }

    /** Возвращает BNPL-контракт заказа или бросает, если рассрочка не оформлена. */
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
