package com.example.marketplace.controller;

import com.example.marketplace.dto.response.CardBindingResponse;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер привязанных карт.
 *
 *   GET    /api/cards          — список карт текущего пользователя
 *   PATCH  /api/cards/{id}/default — сделать карту дефолтной
 *   DELETE /api/cards/{id}     — удалить привязку карты
 */
@RestController
@RequestMapping(value = "/api/cards", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CardController {

    private final CardService    cardService;
    private final UserRepository userRepository;

    @GetMapping
    public List<CardBindingResponse> getCards(@AuthenticationPrincipal UserDetails ud) {
        return cardService.getCards(resolveUser(ud));
    }

    @PatchMapping("/{id}/default")
    public void setDefault(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails ud) {
        cardService.setDefault(id, resolveUser(ud));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetails ud) {
        cardService.delete(id, resolveUser(ud));
    }

    /**
     * POST /api/cards/bind — начать привязку новой карты.
     * Регистрирует платёж 1₽ в Альфа Банке, возвращает formUrl для редиректа.
     * После прохождения формы банк редиректит на /api/payment/card-bind-callback,
     * где платёж автоматически отменяется и карта сохраняется.
     */
    @PostMapping("/bind")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentInitResponse bind(@AuthenticationPrincipal UserDetails ud) {
        return cardService.initiateBinding(resolveUser(ud));
    }

    /** Достаёт сущность User по email из JWT-принципала. */
    private User resolveUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}
