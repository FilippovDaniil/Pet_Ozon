package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.ItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Одна позиция в заказе.
 *
 * Ключевое отличие от CartItem: здесь хранится priceAtOrder — цена товара
 * на момент оформления заказа. Это «снимок» цены.
 *
 * Зачем? Продавец может изменить цену товара позднее, но стоимость
 * уже созданных заказов должна оставаться прежней.
 *
 * Связи:
 *   ManyToOne с Order   — много позиций принадлежат одному заказу.
 *   ManyToOne с Product — каждая позиция ссылается на товар (для истории).
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Ссылка на товар. При удалении товара эта ссылка станет null (если не настроить cascade),
    // поэтому на production нужно добавить soft-delete или запрет удаления товаров с заказами.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;

    // Цена за единицу товара на момент оформления заказа — фиксируется в CartService.checkout().
    private BigDecimal priceAtOrder;

    // Актуально только для BNPL-заказов.
    // Null для заказов с полной оплатой.
    // Используется как маркер «позиция под управлением фулфилмента» (не null = BNPL),
    // а также как обобщённый бейдж статуса, пересчитываемый из счётчиков ниже.
    @Enumerated(EnumType.STRING)
    private ItemStatus itemStatus;

    // ── Поштучный учёт фулфилмента ──────────────────────────────────────────────
    // Каждая физическая единица позиции управляется отдельно: выдать/отменить/вернуть
    // можно по одной штуке. pending = quantity − issued − cancelled − returned.
    // columnDefinition с DEFAULT 0 — чтобы ddl-auto=update смог добавить колонку в таблицу
    // с уже существующими строками (NOT NULL без DEFAULT уронил бы старт — см. урок days_postponed).
    @Column(columnDefinition = "integer default 0")
    private Integer issuedCount = 0;     // выдано клиенту (и ещё не возвращено)

    @Column(columnDefinition = "integer default 0")
    private Integer cancelledCount = 0;  // отменено до выдачи (reverse на долю)

    @Column(columnDefinition = "integer default 0")
    private Integer returnedCount = 0;   // возвращено после выдачи (refund на долю)

    // Null-safe геттеры (Lombok их не перегенерирует — имена уже заняты).
    public int getIssuedCount()    { return issuedCount    == null ? 0 : issuedCount; }
    public int getCancelledCount() { return cancelledCount == null ? 0 : cancelledCount; }
    public int getReturnedCount()  { return returnedCount  == null ? 0 : returnedCount; }

    /** Сколько единиц ещё ожидает выдачи. */
    public int getPendingCount() {
        return quantity - getIssuedCount() - getCancelledCount() - getReturnedCount();
    }
}
