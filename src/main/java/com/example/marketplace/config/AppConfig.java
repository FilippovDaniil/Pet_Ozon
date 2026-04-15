package com.example.marketplace.config;

import com.example.marketplace.entity.Cart;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.repository.CartRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppConfig {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("=== Initialising test data ===");

            if (userRepository.count() == 0) {
                User client = new User();
                client.setEmail("client@example.com");
                client.setPassword("pass");
                client.setFullName("Test Client");
                client.setRole(Role.CLIENT);
                userRepository.save(client);

                User admin = new User();
                admin.setEmail("admin@example.com");
                admin.setPassword("pass");
                admin.setFullName("Admin User");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);

                createCartForUser(client);
                createCartForUser(admin);

                log.info("Created users: client@example.com (CLIENT), admin@example.com (ADMIN)");
            } else {
                // Make sure every user has a cart
                userRepository.findAll().forEach(user -> {
                    if (cartRepository.findByUser(user).isEmpty()) {
                        createCartForUser(user);
                        log.info("Created missing cart for user: {}", user.getEmail());
                    }
                });
            }

            if (productRepository.count() == 0) {
                addProduct("Ноутбук Dell XPS 15",
                        "Мощный ноутбук для работы и учёбы, Intel Core i7, 16 GB RAM, 512 GB SSD",
                        "89999.99", 10);
                addProduct("Беспроводная мышь Logitech MX Master 3",
                        "Эргономичная беспроводная мышь с 7 кнопками и зарядкой по USB-C",
                        "4999.99", 50);
                addProduct("Механическая клавиатура Keychron K2",
                        "Компактная беспроводная механическая клавиатура (Bluetooth + USB)",
                        "7999.99", 25);
                addProduct("Монитор Samsung 27\" 4K UHD",
                        "IPS-панель, HDR400, 60 Гц, порты USB-C / HDMI / DisplayPort",
                        "34999.99", 15);
                addProduct("Наушники Sony WH-1000XM5",
                        "Беспроводные наушники с активным шумоподавлением, 30 ч автономной работы",
                        "24999.99", 20);
                log.info("Created 5 test products");
            }

            log.info("=== Marketplace is ready! Try these endpoints: ===");
            log.info("  GET  http://localhost:8080/api/products");
            log.info("  POST http://localhost:8080/api/cart/add        (header X-User-Id: 1)");
            log.info("  GET  http://localhost:8080/api/cart            (header X-User-Id: 1)");
            log.info("  POST http://localhost:8080/api/cart/checkout   (header X-User-Id: 1)");
            log.info("  POST http://localhost:8080/api/invoice/1/pay");
        };
    }

    private void createCartForUser(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        cartRepository.save(cart);
    }

    private void addProduct(String name, String description, String price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        productRepository.save(p);
    }
}
