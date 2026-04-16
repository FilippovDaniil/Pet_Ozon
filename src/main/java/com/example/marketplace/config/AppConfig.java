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

            User client  = ensureUser("client@example.com",  "pass", "Иван Клиентов",    Role.CLIENT,  null);
            User admin   = ensureUser("admin@example.com",   "pass", "Администратор",    Role.ADMIN,   null);
            User seller1 = ensureUser("seller1@example.com", "pass", "Алексей Технов",   Role.SELLER,  "TechShop");
            User seller2 = ensureUser("seller2@example.com", "pass", "Мария Звукова",    Role.SELLER,  "AudioWorld");

            for (User u : new User[]{client, admin, seller1, seller2}) {
                if (cartRepository.findByUser(u).isEmpty()) {
                    Cart cart = new Cart();
                    cart.setUser(u);
                    cartRepository.save(cart);
                }
            }

            if (productRepository.count() == 0) {
                // TechShop — компьютерная техника
                addProduct("Ноутбук Dell XPS 15",
                        "Intel Core i7, 16 ГБ RAM, 512 ГБ SSD, OLED 15.6\" 4K",
                        "89999.99", 10, seller1);
                addProduct("Ноутбук ASUS ROG Zephyrus G14",
                        "AMD Ryzen 9, RTX 4070, 32 ГБ RAM, 1 ТБ SSD, 165 Гц",
                        "119999.99", 5, seller1);
                addProduct("Беспроводная мышь Logitech MX Master 3",
                        "Эргономичная, 7 кнопок, зарядка USB-C, 70 дней от батареи",
                        "4999.99", 50, seller1);
                addProduct("Механическая клавиатура Keychron K2",
                        "Компактная (75%), Bluetooth + USB, подсветка RGB",
                        "7999.99", 25, seller1);
                addProduct("Монитор Samsung 27\" 4K UHD",
                        "IPS, HDR400, 60 Гц, USB-C 65 Вт, HDMI 2.0, DisplayPort",
                        "34999.99", 15, seller1);
                addProduct("Игровая мышь Razer DeathAdder V3",
                        "30 000 DPI, оптический сенсор Focus Pro, 90 г",
                        "5999.99", 30, seller1);
                addProduct("USB-хаб Anker 7-in-1",
                        "4K HDMI, 100 Вт PD, USB-A 3.0 × 3, SD/microSD",
                        "2999.99", 40, seller1);
                addProduct("Веб-камера Logitech C920 Pro",
                        "Full HD 1080p/30fps, автофокус, стереомикрофон",
                        "6999.99", 20, seller1);
                addProduct("SSD Samsung 970 EVO Plus 1 ТБ",
                        "M.2 NVMe, 3500 МБ/с чтение, 3300 МБ/с запись",
                        "8999.99", 35, seller1);
                addProduct("Игровой монитор ASUS TUF VG27AQL1A",
                        "27\", IPS, 2560×1440, 170 Гц, HDR400, G-Sync совм.",
                        "28999.99", 12, seller1);

                // AudioWorld — аудиотехника
                addProduct("Наушники Sony WH-1000XM5",
                        "Беспроводные, ANC, 30 ч работы, Multipoint, LDAC",
                        "24999.99", 20, seller2);
                addProduct("TWS Apple AirPods Pro 2",
                        "ANC, Transparency, Spatial Audio, MagSafe, H2 чип",
                        "19999.99", 25, seller2);
                addProduct("Колонка JBL Charge 5",
                        "40 Вт, IP67, 20 ч работы, PartyBoost, USB-A зарядка",
                        "12999.99", 18, seller2);
                addProduct("Саундбар Samsung HW-Q600C",
                        "3.1.2 Ch, 360 Вт, Dolby Atmos, DTS:X, HDMI eARC",
                        "39999.99", 8, seller2);
                addProduct("Микрофон Blue Yeti",
                        "USB-конденсаторный, 4 режима, 48 кГц / 16 бит",
                        "11999.99", 15, seller2);
                addProduct("Наушники Sennheiser HD 560S",
                        "Открытые, 38 Ом, 6–38 000 Гц, вес 240 г",
                        "14999.99", 22, seller2);
                addProduct("TWS Samsung Galaxy Buds2 Pro",
                        "ANC, 3D Audio, IPX7, 8 ч + 22 ч кейс, Bluetooth 5.3",
                        "11999.99", 30, seller2);
                addProduct("Аудиоинтерфейс Focusrite Scarlett Solo",
                        "USB, 2 вх / 2 вых, 192 кГц / 24 бит, Air режим",
                        "9999.99", 12, seller2);
                addProduct("Виниловый проигрыватель Audio-Technica AT-LP120X",
                        "USB, прямой привод, 33/45/78 об/мин, встроенный фонокорректор",
                        "22999.99", 7, seller2);
                addProduct("Наушники Bose QuietComfort 45",
                        "ANC, 24 ч работы, Aware Mode, Bluetooth 5.1, USB-C",
                        "21999.99", 16, seller2);

                log.info("Created 20 test products (10 TechShop + 10 AudioWorld)");
            }

            log.info("=== Marketplace ready ===");
            log.info("  client1@example.com  / pass → покупатель");
            log.info("  seller1@example.com  / pass → TechShop");
            log.info("  seller2@example.com  / pass → AudioWorld");
            log.info("  admin@example.com    / pass → администратор");
        };
    }

    private User ensureUser(String email, String password, String fullName, Role role, String shopName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setPassword(password);
            u.setFullName(fullName);
            u.setRole(role);
            u.setShopName(shopName);
            u.setBalance(BigDecimal.ZERO);
            User saved = userRepository.save(u);
            log.info("Created user: {} ({})", email, role);
            return saved;
        });
    }

    private void addProduct(String name, String description, String price, int stock, User seller) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setSeller(seller);
        productRepository.save(p);
    }
}
