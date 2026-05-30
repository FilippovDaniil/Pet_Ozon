package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.request.PayInstallmentRequest;
import com.example.marketplace.dto.request.UpdateOrderStatusRequest;
import com.example.marketplace.dto.response.BnplContractResponse;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerInfoResponse;
import com.example.marketplace.payment.BnplService;
import com.example.marketplace.payment.FullPaymentService;
import com.example.marketplace.service.InvoiceService;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.ProductService;
import com.example.marketplace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Административные эндпоинты — только для роли ADMIN.
 *
 * Доступ контролируется в SecurityConfig:
 *   .requestMatchers("/api/admin/**").hasRole("ADMIN")
 * Spring проверяет роль ПЕРЕД вызовом метода контроллера.
 * Если роль не совпадает — автоматически 403 Forbidden.
 *
 * Группы операций:
 *   /api/admin/products  — CRUD товаров (без привязки к продавцу)
 *   /api/admin/orders    — просмотр и изменение статуса любых заказов
 *   /api/admin/invoices  — просмотр всех счетов
 */
@RestController
@RequestMapping(value = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final InvoiceService invoiceService;
    private final UserService userService;
    private final BnplService bnplService;
    private final FullPaymentService fullPaymentService;

    // --- Список продавцов (для выпадающего списка при создании товара) ---

    /** Возвращает всех пользователей с ролью SELLER для выбора в форме создания товара. */
    @GetMapping("/sellers")
    public List<SellerInfoResponse> getAllSellers() {
        return userService.getAllSellers();
    }

    // --- Управление товарами ---

    // @ResponseStatus(CREATED) — Spring автоматически вернёт HTTP 201 вместо 200.
    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    // PUT — полная замена ресурса (все поля обновляются).
    @PutMapping(value = "/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    // NO_CONTENT (204) — стандартный ответ при успешном удалении (тело пустое).
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    /**
     * Загрузить изображение для любого товара.
     * Администратор не ограничен владельцем товара — может загружать фото для любого.
     *
     * consumes = MULTIPART_FORM_DATA_VALUE — принимает файл из multipart/form-data.
     * @RequestParam("file") — поле формы с именем "file".
     */
    @PostMapping(value = "/products/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse uploadProductImage(@PathVariable Long id,
                                               @RequestParam("file") MultipartFile file) {
        return productService.uploadProductImage(id, file);
    }

    /**
     * Удалить изображение товара.
     * После вызова поля imageData и imageContentType обнуляются в БД.
     */
    @DeleteMapping("/products/{id}/image")
    public ResponseEntity<Void> deleteProductImage(@PathVariable Long id) {
        productService.deleteProductImage(id);
        return ResponseEntity.noContent().build();
    }

    // --- Управление заказами ---

    @GetMapping("/orders")
    public Page<OrderResponse> getAllOrders(@PageableDefault(size = 100) Pageable pageable) {
        return orderService.getAllOrders(pageable);
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    /** PATCH /api/admin/orders/{id} — смена статуса заказа: CREATED → PAID, PAID → DELIVERED и т.д. */
    @PatchMapping(value = "/orders/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.getStatus());
    }

    @GetMapping("/bnpl/{contractId}")
    public BnplContractResponse getBnplContract(@PathVariable Long contractId) {
        return bnplService.getContractByIdAdmin(contractId);
    }

    /**
     * POST /api/admin/orders/{id}/pay — оплата заказа с дефолтной карты клиента (тихое списание).
     *
     * Тело (опционально): {"amountKopecks": N}.
     *   • BNPL-заказ (контракт ACTIVE): null → ближайший взнос; N → произвольная сумма.
     *   • Обычный заказ (не оплачен): списывается полная сумма счёта (amountKopecks игнорируется).
     *
     * Возвращает обновлённый заказ.
     */
    @PostMapping(value = "/orders/{orderId}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse payOrderByCard(@PathVariable Long orderId,
                                        @RequestBody(required = false) PayInstallmentRequest req) {
        Long amount = req != null ? req.amountKopecks() : null;
        OrderResponse o = orderService.getOrderById(orderId);

        if (o.getBnplContractId() != null && "ACTIVE".equals(o.getBnplStatus())) {
            bnplService.payInstallmentsByAdmin(o.getBnplContractId(), amount);
        } else if (o.getBnplContractId() == null && o.getInvoiceId() != null) {
            fullPaymentService.payByDefaultCard(o.getInvoiceId());
        } else {
            throw new IllegalStateException("По этому заказу нечего оплачивать с карты");
        }
        return orderService.getOrderById(orderId);
    }

    // --- Просмотр счетов ---

    /**
     * GET /api/admin/invoices — список всех счетов с пагинацией.
     *
     * Аналогично GET /api/admin/orders: возвращает Page<T> вместо List<T>.
     * Пример запроса: GET /api/admin/invoices?page=0&size=10&sort=createdAt,desc
     */
    @GetMapping("/invoices")
    public Page<InvoiceResponse> getAllInvoices(@PageableDefault(size = 20) Pageable pageable) {
        return invoiceService.getAllInvoices(pageable);
    }
}
