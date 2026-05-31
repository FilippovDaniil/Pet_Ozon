package com.example.marketplace.dto.response;

/**
 * Ответ на оплату заказа администратором с карты клиента
 * (POST /api/admin/orders/{id}/pay).
 *
 * Два исхода (как и у клиентской оплаты взноса):
 *   1. Тихое списание по реальной связке удалось — {@code formUrl == null},
 *      {@code order} содержит обновлённый заказ.
 *   2. Реальной связки нет (UAT / карта не привязана на стороне банка) —
 *      {@code formUrl != null}: списать молча нельзя, нужно пройти форму банка.
 *      Фронт делает {@code window.location.href = formUrl}.
 */
public record AdminPayResponse(
        String formUrl,
        OrderResponse order
) {
    /** Тихое списание прошло — отдаём обновлённый заказ. */
    public static AdminPayResponse paid(OrderResponse order) {
        return new AdminPayResponse(null, order);
    }

    /** Связки нет — оплата через форму банка. */
    public static AdminPayResponse redirect(String formUrl) {
        return new AdminPayResponse(formUrl, null);
    }
}
