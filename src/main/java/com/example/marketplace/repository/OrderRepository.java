package com.example.marketplace.repository;

import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.BnplContractStatus;
import com.example.marketplace.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Репозиторий заказов.
 *
 * findByUser — стандартный запрос по имени метода (SQL: WHERE user_id = ?).
 * Перегружен дважды: со списком и с пагинацией — Spring сам разберётся.
 *
 * findBySellerId — кастомный JPQL-запрос (@Query).
 * JPQL работает с классами и полями Java, а не с таблицами и колонками БД.
 * Запрос: «найди все уникальные заказы, в которых хотя бы одна позиция принадлежит этому продавцу».
 * SELECT DISTINCT oi.order FROM OrderItem oi WHERE oi.product.seller.id = :sellerId
 * JPA сам переведёт это в нужный SQL с JOIN.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    // Тот же запрос, но с пагинацией — используется в OrderService.getOrdersByUserId().
    Page<Order> findByUser(User user, Pageable pageable);

    // @Query + @Param: находит заказы, содержащие товары данного продавца.
    @Query("SELECT DISTINCT oi.order FROM OrderItem oi WHERE oi.product.seller.id = :sellerId")
    List<Order> findBySellerId(@Param("sellerId") Long sellerId);

    /**
     * «Активные» заказы клиента — для вкладки «Мои заказы».
     *
     * Показываем заказ, если он либо ещё ждёт оплаты (status = CREATED),
     * либо к нему привязана активная рассрочка (контракт AWAITING_PAYMENT/ACTIVE —
     * клиент ещё гасит взносы). Прочие заказы — оплаченные (PAID без активной рассрочки),
     * отменённые (CANCELLED) и доставленные (DELIVERED) — считаются «финальными» и скрываются.
     *
     * countQuery задан явно: автоматический COUNT для запросов с EXISTS Spring Data
     * генерирует не всегда корректно — фиксируем его, чтобы пагинация была точной.
     */
    @Query(value = """
            SELECT o FROM Order o
            WHERE o.user = :user
              AND ( o.status = :createdStatus
                    OR EXISTS (SELECT 1 FROM BnplContract c
                               WHERE c.order = o AND c.status IN :activeBnplStatuses) )
            """,
           countQuery = """
            SELECT COUNT(o) FROM Order o
            WHERE o.user = :user
              AND ( o.status = :createdStatus
                    OR EXISTS (SELECT 1 FROM BnplContract c
                               WHERE c.order = o AND c.status IN :activeBnplStatuses) )
            """)
    Page<Order> findActiveForClient(@Param("user") User user,
                                    @Param("createdStatus") OrderStatus createdStatus,
                                    @Param("activeBnplStatuses") List<BnplContractStatus> activeBnplStatuses,
                                    Pageable pageable);
}
