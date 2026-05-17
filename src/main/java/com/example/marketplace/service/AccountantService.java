package com.example.marketplace.service;

import com.example.marketplace.dto.response.*;
import com.example.marketplace.entity.CartItem;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantService {

    private final OrderRepository     orderRepository;
    private final CartItemRepository  cartItemRepository;
    private final UserRepository      userRepository;
    private final EmailLogRepository  emailLogRepository;

    @Transactional(readOnly = true)
    public AccountantSummaryResponse getSummary() {
        List<Order> allOrders = orderRepository.findAll();
        long paidOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PAID)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalClients = userRepository.findByRole(Role.CLIENT).size();

        List<CartItem> cartItems = cartItemRepository.findAll();
        BigDecimal potentialRevenue = cartItems.stream()
                .map(ci -> ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long emailsSent    = emailLogRepository.count();
        long emailsSuccess = emailLogRepository.countBySuccess(true);

        return new AccountantSummaryResponse(
                allOrders.size(), paidOrders, totalRevenue,
                totalClients,
                cartItems.size(), potentialRevenue,
                emailsSent, emailsSuccess
        );
    }

    @Transactional(readOnly = true)
    public List<OrderReportDto> getOrdersReport() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate")).stream()
                .map(o -> {
                    User u = o.getUser();
                    String name = (u.getFullName() != null && !u.getFullName().isBlank())
                            ? u.getFullName() : u.getEmail();
                    return new OrderReportDto(
                            o.getId(), name, u.getEmail(),
                            o.getOrderDate(), o.getStatus().name(),
                            o.getTotalAmount(), o.getItems().size(),
                            o.getShippingAddress()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CartReportDto> getCartsReport() {
        return cartItemRepository.findAll().stream()
                .map(ci -> {
                    User u = ci.getCart().getUser();
                    String name = (u.getFullName() != null && !u.getFullName().isBlank())
                            ? u.getFullName() : u.getEmail();
                    BigDecimal sub = ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
                    return new CartReportDto(
                            name, u.getEmail(),
                            ci.getProduct().getName(),
                            ci.getProduct().getCategory(),
                            ci.getQuantity(),
                            ci.getProduct().getPrice(), sub
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerReportDto> getCustomersReport() {
        return userRepository.findByRole(Role.CLIENT).stream()
                .map(client -> {
                    List<Order> orders = orderRepository.findByUser(client);
                    BigDecimal totalSpent = orders.stream()
                            .filter(o -> o.getStatus() == OrderStatus.PAID)
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    String name = (client.getFullName() != null && !client.getFullName().isBlank())
                            ? client.getFullName() : client.getEmail();
                    return new CustomerReportDto(
                            client.getId(), name, client.getEmail(),
                            orders.size(), totalSpent, client.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmailLogDto> getEmailsReport() {
        return emailLogRepository.findAll(Sort.by(Sort.Direction.DESC, "sentAt")).stream()
                .map(el -> new EmailLogDto(
                        el.getId(), el.getRecipient(), el.getSubject(),
                        el.getSentAt(), el.isSuccess(), el.getErrorMessage()
                ))
                .toList();
    }
}
