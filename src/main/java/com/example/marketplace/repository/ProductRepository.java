package com.example.marketplace.repository;

import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Репозиторий для работы с товарами.
 *
 * Расширяет два интерфейса:
 *   JpaRepository<Product, Long>         — стандартные CRUD-методы.
 *   JpaSpecificationExecutor<Product>    — поддержка динамических запросов через Specification.
 *
 * Specification — это паттерн для построения запросов «на лету».
 * Используется в ProductService.getAllProducts() для фильтрации по name, category, minPrice, maxPrice.
 * Без Specification пришлось бы писать отдельный метод репозитория для каждой комбинации фильтров.
 *
 * Pageable — объект пагинации и сортировки.
 * Передаётся из контроллера (аннотация @PageableDefault), позволяет клиенту запросить
 * страницы: /api/products?page=0&size=20&sort=price,asc
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Находит все товары конкретного продавца (используется в SellerService).
    List<Product> findBySeller(User seller);

    // Стандартный findAll(Pageable) уже есть в JpaRepository,
    // но здесь он переопределён явно для ясности.
    Page<Product> findAll(Pageable pageable);
}
