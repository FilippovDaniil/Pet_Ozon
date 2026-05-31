package com.example.marketplace.controller;

import com.example.marketplace.dto.request.PayInstallmentRequest;
import com.example.marketplace.dto.request.PostponeInstallmentRequest;
import com.example.marketplace.dto.request.UpdateItemStatusRequest;
import com.example.marketplace.dto.response.BnplContractResponse;
import com.example.marketplace.dto.response.BnplInstallmentResponse;
import com.example.marketplace.dto.response.BnplPayResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.payment.BnplService;
import com.example.marketplace.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер для BNPL-рассрочки.
 *
 *   GET    /api/bnpl/my                             — мои контракты
 *   GET    /api/bnpl/{id}                           — детали контракта
 *   POST   /api/bnpl/{id}/postpone                  — перенести ближайший взнос (тело: {days})
 *   POST   /api/bnpl/{id}/pay                       — оплатить взносы по дефолтной карте
 *   PATCH  /api/orders/{id}/items/{itemId}          — статус позиции (ISSUED/CANCELLED/RETURNED)
 *   PATCH  /api/admin/orders/{id}/items/{itemId}    — то же для администратора
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BnplController {

    private final BnplService    bnplService;
    private final UserRepository userRepository;

    @GetMapping("/api/bnpl/my")
    public List<BnplContractResponse> myContracts(@AuthenticationPrincipal UserDetails ud) {
        return bnplService.getContractsForUser(resolveUser(ud));
    }

    @GetMapping("/api/bnpl/{contractId}")
    public BnplContractResponse getContract(@PathVariable Long contractId,
                                            @AuthenticationPrincipal UserDetails ud) {
        return bnplService.getContractById(contractId, resolveUser(ud));
    }

    /** Перенос ближайшего PENDING-взноса. Возвращает обновлённый взнос. */
    @PostMapping(value = "/api/bnpl/{contractId}/postpone", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BnplInstallmentResponse postpone(@PathVariable Long contractId,
                                            @Valid @RequestBody PostponeInstallmentRequest req,
                                            @AuthenticationPrincipal UserDetails ud) {
        return bnplService.postponeInstallment(contractId, req.days(), resolveUser(ud));
    }

    /**
     * Оплата взноса. Если есть реальная связка — тихое списание (возвращает график в
     * {@code installments}). Если связки нет — возвращает {@code formUrl}: клиент платит
     * взнос через форму банка.
     */
    @PostMapping(value = "/api/bnpl/{contractId}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BnplPayResponse payNow(@PathVariable Long contractId,
                                  @RequestBody(required = false) PayInstallmentRequest req,
                                  @AuthenticationPrincipal UserDetails ud) {
        Long amount = req != null ? req.amountKopecks() : null;
        return bnplService.payInstallmentByClient(contractId, amount, resolveUser(ud));
    }

    /** Изменить статус позиции BNPL-заказа (клиент). */
    @PatchMapping("/api/orders/{orderId}/items/{itemId}")
    public void updateItemStatus(@PathVariable Long orderId,
                                 @PathVariable Long itemId,
                                 @Valid @RequestBody UpdateItemStatusRequest request) {
        applyItemStatus(orderId, itemId, request.status());
    }

    /** То же — для администратора. */
    @PatchMapping("/api/admin/orders/{orderId}/items/{itemId}")
    public void adminUpdateItemStatus(@PathVariable Long orderId,
                                      @PathVariable Long itemId,
                                      @Valid @RequestBody UpdateItemStatusRequest request) {
        applyItemStatus(orderId, itemId, request.status());
    }

    /** Диспетчер статуса позиции: направляет в нужный метод BnplService (deposit/reverse/refund). */
    private void applyItemStatus(Long orderId, Long itemId, String status) {
        switch (status.toUpperCase()) {
            case "ISSUED"    -> bnplService.issueItem(orderId, itemId);
            case "CANCELLED" -> bnplService.cancelItem(orderId, itemId);
            case "RETURNED"  -> bnplService.returnItem(orderId, itemId);
            default -> throw new IllegalArgumentException(
                    "Неизвестный статус: " + status + ". Допустимые: ISSUED, CANCELLED, RETURNED");
        }
    }

    /** Достаёт сущность User по email из JWT-принципала. */
    private User resolveUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}
