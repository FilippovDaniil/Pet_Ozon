package com.example.marketplace.dto.response;

import java.util.List;

/**
 * Ответ на запрос оплаты взноса BNPL (POST /api/bnpl/{id}/pay).
 *
 * Возможны два исхода:
 *   1. Тихое списание по привязанной карте (MIT) удалось —
 *      {@code formUrl == null}, {@code installments} содержит обновлённый график.
 *   2. Реальной связки для тихого списания нет (например, UAT-аккаунт или карта
 *      не привязана на стороне банка) — {@code formUrl != null}, клиента нужно
 *      перенаправить на форму банка, {@code installments == null}.
 *
 * Фронтенд: если пришёл {@code formUrl} — делает {@code window.location.href = formUrl};
 * иначе показывает успех и перечитывает контракт.
 */
public record BnplPayResponse(
        String formUrl,
        List<BnplInstallmentResponse> installments
) {
    /** Тихое списание прошло — отдаём обновлённый график. */
    public static BnplPayResponse charged(List<BnplInstallmentResponse> installments) {
        return new BnplPayResponse(null, installments);
    }

    /** Нет связки — клиент платит через форму банка. */
    public static BnplPayResponse redirect(String formUrl) {
        return new BnplPayResponse(formUrl, null);
    }
}
