package com.example.marketplace.repository;

import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.Review;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий отзывов.
 *
 * findByProduct — все отзывы на товар (для отображения на странице товара).
 * findByProductAndUser — проверка: уже оставлял ли пользователь отзыв на этот товар?
 * getAverageRatingByProduct — средний рейтинг товара (кастомный JPQL-запрос).
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // SELECT * FROM reviews WHERE product_id = ?
    List<Review> findByProduct(Product product);

    // SELECT * FROM reviews WHERE product_id = ? AND user_id = ?
    // Optional — пользователь мог ещё не оставлять отзыв → пустой Optional.
    Optional<Review> findByProductAndUser(Product product, User user);

    /**
     * Средний рейтинг товара.
     *
     * @Query — кастомный JPQL: не Spring Data derivation, а явный запрос.
     * AVG(r.rating) — агрегатная функция, возвращает Double (или null если отзывов нет).
     * :product — именованный параметр, привязывается через @Param.
     *
     * Эквивалентный SQL: SELECT AVG(rating) FROM reviews WHERE product_id = ?
     */
    /**
     * Количество отзывов на товар.
     *
     * countByProduct — Spring Data генерирует SELECT COUNT(*) FROM reviews WHERE product_id = ?
     * Это эффективнее, чем findByProduct(product).size():
     * в первом случае БД возвращает одно число, а не все строки таблицы.
     */
    long countByProduct(Product product);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product = :product")
    Double getAverageRatingByProduct(@Param("product") Product product);
}
