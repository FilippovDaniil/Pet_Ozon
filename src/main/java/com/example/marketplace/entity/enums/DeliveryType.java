package com.example.marketplace.entity.enums;

/**
 * Способ получения заказа, выбираемый клиентом при оформлении.
 *
 * DELIVERY — доставка на дом: клиент вводит адрес.
 * PICKUP   — самовывоз: клиент выбирает точку из справочника PickupPoint.
 */
public enum DeliveryType {
    DELIVERY,
    PICKUP
}
