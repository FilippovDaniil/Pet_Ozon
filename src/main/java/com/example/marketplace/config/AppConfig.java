package com.example.marketplace.config;

import com.example.marketplace.entity.Cart;
import com.example.marketplace.entity.Category;
import com.example.marketplace.entity.PickupPoint;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.repository.CartRepository;
import com.example.marketplace.repository.PickupPointRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.service.CategoryService;
import com.example.marketplace.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppConfig {

    private final UserRepository       userRepository;
    private final ProductRepository    productRepository;
    private final CartRepository       cartRepository;
    private final CategoryService      categoryService;
    private final ProductSearchService productSearchService;
    private final PasswordEncoder      passwordEncoder;
    private final JdbcTemplate         jdbc;
    private final PickupPointRepository pickupPointRepository;

    /**
     * Сидинг тестовых данных при старте приложения (CommandLineRunner запускается после поднятия контекста).
     *
     * Порядок строго важен:
     *   1. fixRoleConstraint()      — обновить CHECK-ограничение role (иначе INSERT ACCOUNTANT упадёт);
     *   2. ensureUser()×5 + корзины — тестовые пользователи всех ролей;
     *   3. сидинг товаров порогами  — count==0 → базовые 20; <250 → расширенный каталог; <340 → тонкие категории;
     *   4. fixProductCategories()   — идемпотентно исправить категории;
     *   5. reindexAll()             — синхронизировать товары в OpenSearch (graceful degradation);
     *   6. createOpenSearchIndexPattern() — создать index pattern в Dashboards (только в K8s).
     */
    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("=== Initialising test data ===");
            fixRoleConstraint();

            User client     = ensureUser("client@example.com",     "pass", "Иван Клиентов",    Role.CLIENT,     null);
            User admin      = ensureUser("admin@example.com",      "pass", "Администратор",    Role.ADMIN,      null);
            User seller1    = ensureUser("seller1@example.com",    "pass", "Алексей Технов",   Role.SELLER,     "TechShop");
            User seller2    = ensureUser("seller2@example.com",    "pass", "Мария Звукова",    Role.SELLER,     "AudioWorld");
                             ensureUser("accountant@example.com", "pass", "Елена Бухгалтер", Role.ACCOUNTANT, null);

            for (User u : new User[]{client, admin, seller1, seller2}) {
                if (cartRepository.findByUser(u).isEmpty()) {
                    Cart cart = new Cart();
                    cart.setUser(u);
                    cartRepository.save(cart);
                }
            }

            if (productRepository.count() == 0) {
                // ── TechShop — базовые товары ──────────────────────────────────────────────
                addProduct("Ноутбук Dell XPS 15", "Intel Core i7, 16 ГБ RAM, 512 ГБ SSD, OLED 15.6\" 4K", "89999.99", 10, seller1, "Ноутбуки");
                addProduct("Ноутбук ASUS ROG Zephyrus G14", "AMD Ryzen 9, RTX 4070, 32 ГБ RAM, 1 ТБ SSD, 165 Гц", "119999.99", 5, seller1, "Ноутбуки");
                addProduct("Беспроводная мышь Logitech MX Master 3", "Эргономичная, 7 кнопок, зарядка USB-C, 70 дней от батареи", "4999.99", 50, seller1, "Периферия");
                addProduct("Механическая клавиатура Keychron K2", "Компактная (75%), Bluetooth + USB, подсветка RGB", "7999.99", 25, seller1, "Периферия");
                addProduct("Монитор Samsung 27\" 4K UHD", "IPS, HDR400, 60 Гц, USB-C 65 Вт, HDMI 2.0, DisplayPort", "34999.99", 15, seller1, "Мониторы");
                addProduct("Игровая мышь Razer DeathAdder V3", "30 000 DPI, оптический сенсор Focus Pro, 90 г", "5999.99", 30, seller1, "Периферия");
                addProduct("USB-хаб Anker 7-in-1", "4K HDMI, 100 Вт PD, USB-A 3.0 × 3, SD/microSD", "2999.99", 40, seller1, "Периферия");
                addProduct("Веб-камера Logitech C920 Pro", "Full HD 1080p/30fps, автофокус, стереомикрофон", "6999.99", 20, seller1, "Периферия");
                addProduct("SSD Samsung 970 EVO Plus 1 ТБ", "M.2 NVMe, 3500 МБ/с чтение, 3300 МБ/с запись", "8999.99", 35, seller1, "Накопители");
                addProduct("Игровой монитор ASUS TUF VG27AQL1A", "27\", IPS, 2560×1440, 170 Гц, HDR400, G-Sync совм.", "28999.99", 12, seller1, "Мониторы");
                // ── AudioWorld — базовые товары ────────────────────────────────────────────
                addProduct("Наушники Sony WH-1000XM5", "Беспроводные, ANC, 30 ч работы, Multipoint, LDAC", "24999.99", 20, seller2, "Аудио");
                addProduct("TWS Apple AirPods Pro 2", "ANC, Transparency, Spatial Audio, MagSafe, H2 чип", "19999.99", 25, seller2, "Аудио");
                addProduct("Колонка JBL Charge 5", "40 Вт, IP67, 20 ч работы, PartyBoost, USB-A зарядка", "12999.99", 18, seller2, "Аудио");
                addProduct("Саундбар Samsung HW-Q600C", "3.1.2 Ch, 360 Вт, Dolby Atmos, DTS:X, HDMI eARC", "39999.99", 8, seller2, "Аудио");
                addProduct("Микрофон Blue Yeti", "USB-конденсаторный, 4 режима, 48 кГц / 16 бит", "11999.99", 15, seller2, "Аудио");
                addProduct("Наушники Sennheiser HD 560S", "Открытые, 38 Ом, 6–38 000 Гц, вес 240 г", "14999.99", 22, seller2, "Аудио");
                addProduct("TWS Samsung Galaxy Buds2 Pro", "ANC, 3D Audio, IPX7, 8 ч + 22 ч кейс, Bluetooth 5.3", "11999.99", 30, seller2, "Аудио");
                addProduct("Аудиоинтерфейс Focusrite Scarlett Solo", "USB, 2 вх / 2 вых, 192 кГц / 24 бит, Air режим", "9999.99", 12, seller2, "Аудио");
                addProduct("Виниловый проигрыватель Audio-Technica AT-LP120X", "USB, прямой привод, 33/45/78 об/мин, встроенный фонокорректор", "22999.99", 7, seller2, "Аудио");
                addProduct("Наушники Bose QuietComfort 45", "ANC, 24 ч работы, Aware Mode, Bluetooth 5.1, USB-C", "21999.99", 16, seller2, "Аудио");
                log.info("Created 20 base products");
            }

            if (productRepository.count() < 250) {
                // ── TechShop — смартфоны ───────────────────────────────────────────────────
                addProduct("iPhone 15 Pro Max", "A17 Pro, 256 ГБ, titanium корпус, тройная камера 48 Мп", "134999.99", 10, seller1, "Смартфоны");
                addProduct("Samsung Galaxy S24 Ultra", "Snapdragon 8 Gen 3, 12/512 ГБ, S Pen, 200 Мп камера", "119999.99", 15, seller1, "Смартфоны");
                addProduct("Google Pixel 8 Pro", "Google Tensor G3, 12/256 ГБ, 120 Гц LTPO OLED, AI камера", "79999.99", 20, seller1, "Смартфоны");
                addProduct("OnePlus 12", "Snapdragon 8 Gen 3, 16/512 ГБ, Hasselblad камера, 100 Вт зарядка", "74999.99", 18, seller1, "Смартфоны");
                addProduct("Xiaomi 14 Pro", "Snapdragon 8 Gen 3, Leica камера, 120 Вт HyperCharge", "69999.99", 25, seller1, "Смартфоны");
                addProduct("iPhone 15", "A16 Bionic, 128 ГБ, Dynamic Island, USB-C, 48 Мп", "89999.99", 30, seller1, "Смартфоны");
                addProduct("Samsung Galaxy S24+", "Snapdragon 8 Gen 3, 12/256 ГБ, 120 Гц LTPO3 AMOLED", "79999.99", 20, seller1, "Смартфоны");
                addProduct("Sony Xperia 1 VI", "Snapdragon 8 Gen 3, 4K OLED экран, тройная камера Zeiss", "89999.99", 12, seller1, "Смартфоны");
                addProduct("Samsung Galaxy Z Fold 6", "Snapdragon 8 Gen 3, складной 7.6\" экран, 12/256 ГБ", "149999.99", 8, seller1, "Смартфоны");
                addProduct("Samsung Galaxy Z Flip 6", "Snapdragon 8 Gen 3, 12/512 ГБ, FlexCam, IPX8", "89999.99", 12, seller1, "Смартфоны");
                addProduct("Nothing Phone 2a Plus", "MediaTek Dimensity 7350, 12/256 ГБ, Glyph Interface", "34999.99", 18, seller1, "Смартфоны");
                addProduct("Asus Zenfone 11 Ultra", "Snapdragon 8 Gen 3, 5.5\" AMOLED, 5000 мАч, 65 Вт", "64999.99", 15, seller1, "Смартфоны");
                addProduct("Redmi Note 13 Pro+", "Dimensity 7200 Ultra, 200 Мп камера, 120 Вт HyperCharge", "27999.99", 40, seller1, "Смартфоны");
                addProduct("Samsung Galaxy A55", "Exynos 1480, 8/256 ГБ, 50 Мп, IP67, 5000 мАч", "29999.99", 35, seller1, "Смартфоны");
                addProduct("Motorola Edge 50 Pro", "Snapdragon 7 Gen 3, pOLED 144 Гц, 125 Вт TurboPower", "44999.99", 22, seller1, "Смартфоны");
                // ── TechShop — ноутбуки ────────────────────────────────────────────────────
                addProduct("MacBook Pro 14\" M3 Pro", "M3 Pro 11-core CPU, 18 ГБ ОЗУ, 512 ГБ SSD, Liquid Retina XDR", "179999.99", 8, seller1, "Ноутбуки");
                addProduct("MacBook Air 15\" M3", "M3 8-core CPU, 16 ГБ ОЗУ, 512 ГБ SSD, 15.3\" IPS", "139999.99", 12, seller1, "Ноутбуки");
                addProduct("ThinkPad X1 Carbon Gen 12", "Intel Core Ultra 7, 32 ГБ LPDDR5, 1 ТБ SSD, 14\" IPS", "129999.99", 7, seller1, "Ноутбуки");
                addProduct("HP Spectre x360 14", "Intel Core Ultra 7, 32 ГБ, 2 ТБ SSD, OLED 2.8K тач", "99999.99", 10, seller1, "Ноутбуки");
                addProduct("ASUS ZenBook 14 OLED", "Intel Core Ultra 5, 16 ГБ, 512 ГБ, OLED 120 Гц", "74999.99", 15, seller1, "Ноутбуки");
                addProduct("Lenovo ThinkBook 16p Gen 5", "AMD Ryzen 9 8945H, RTX 4060, 32 ГБ, 1 ТБ SSD", "119999.99", 8, seller1, "Ноутбуки");
                addProduct("MSI Stealth 16 AI Studio", "Intel Core Ultra 9, RTX 4090, 64 ГБ, 2 ТБ SSD, 4K OLED", "259999.99", 4, seller1, "Ноутбуки");
                addProduct("Razer Blade 15 2024", "Intel Core i9-14900HX, RTX 4080, 32 ГБ, 1 ТБ, QHD 240 Гц", "189999.99", 5, seller1, "Ноутбуки");
                addProduct("Acer Swift X 14 AI", "AMD Ryzen AI 9 HX, RTX 4070, 32 ГБ, 1 ТБ, 2.8K OLED", "99999.99", 10, seller1, "Ноутбуки");
                addProduct("Huawei MateBook X Pro 2024", "Intel Core Ultra 5, 32 ГБ, 1 ТБ SSD, 14.2\" 3.1K OLED", "119999.99", 8, seller1, "Ноутбуки");
                // ── TechShop — мониторы ────────────────────────────────────────────────────
                addProduct("LG UltraWide 34\" QHD 160 Гц", "IPS, 3440×1440, HDR10, FreeSync, USB-C 90 Вт", "49999.99", 10, seller1, "Мониторы");
                addProduct("BenQ EX3210U 32\" 4K 144 Гц", "IPS, HDR600, USB-C 90 Вт, FreeSync Premium Pro", "54999.99", 8, seller1, "Мониторы");
                addProduct("AOC U27G3X 27\" 4K 160 Гц", "IPS, HDR400, 1 мс GTG, DisplayPort 1.4, HDMI 2.1", "39999.99", 12, seller1, "Мониторы");
                addProduct("ASUS ProArt PA32UCG 4K 120 Гц", "Mini-LED, 1600 нит, 99% DCI-P3, Thunderbolt 3", "129999.99", 5, seller1, "Мониторы");
                addProduct("MSI MPG 321URX 4K 240 Гц", "QD-OLED, 0.03 мс GTG, HDR1000, USB-C 90 Вт", "79999.99", 7, seller1, "Мониторы");
                addProduct("ViewSonic VP2768a-4K ProArt", "27\", 4K IPS, 99% DCI-P3, аппаратная калибровка", "44999.99", 10, seller1, "Мониторы");
                addProduct("Samsung Odyssey G7 32\" QHD", "VA, 240 Гц, 1000R изгиб, G-Sync Compatible", "34999.99", 15, seller1, "Мониторы");
                addProduct("LG 27GP950-B 4K 160 Гц", "Nano IPS, HDR600, 1 мс GTG, HDMI 2.1, G-Sync Compatible", "44999.99", 8, seller1, "Мониторы");
                addProduct("Dell S3222DGM 32\" QHD 165 Гц", "VA, 1800R, 1 мс MPRT, AMD FreeSync Premium", "24999.99", 20, seller1, "Мониторы");
                addProduct("Gigabyte M28U 28\" 4K 144 Гц", "IPS, HDR400, USB-C 90 Вт, KVM-переключатель", "29999.99", 15, seller1, "Мониторы");
                // ── TechShop — периферия дополнительно ────────────────────────────────────
                addProduct("Logitech MX Keys S", "Беспроводная, Logi Options+, бесшумная, подсветка", "7999.99", 30, seller1, "Периферия");
                addProduct("Razer BlackWidow V4 Pro", "Механическая, Razer Yellow, RGB, макрос, USB хаб", "14999.99", 20, seller1, "Периферия");
                addProduct("Corsair K100 RGB", "Cherry MX Speed, командный ролик, iCUE, USB 3.2", "16999.99", 12, seller1, "Периферия");
                addProduct("Steelseries Apex Pro TKL", "Omnipoint 2.0 регулируемый ход, OLED дисплей", "14999.99", 18, seller1, "Периферия");
                addProduct("Keychron Q1 Pro QMK", "Беспроводная, алюминий, горячая замена, RGB", "11999.99", 15, seller1, "Периферия");
                addProduct("HHKB Professional Hybrid Type-S", "Topre 45g, бесшумный, Bluetooth, PFU", "24999.99", 8, seller1, "Периферия");
                addProduct("Logitech G Pro X Superlight 2", "63 г, HERO 25K сенсор, 70 ч работы, белая/чёрная", "8999.99", 25, seller1, "Периферия");
                addProduct("Razer Viper V3 HyperSpeed", "Speedflex кабель, Focus Pro 35K, 280 ч работы", "7999.99", 30, seller1, "Периферия");
                addProduct("Zowie EC2-C Divina", "3360 сенсор, дуговой дизайн, без RGB, plug&play", "5999.99", 25, seller1, "Периферия");
                addProduct("Logitech G840 XL Mousepad", "900×400 мм, ткань Performance, резина, 3 мм", "3999.99", 40, seller1, "Периферия");
                addProduct("Elgato Stream Deck MK.2", "15 LCD кнопок, настраиваемые действия, USB", "12999.99", 15, seller1, "Периферия");
                addProduct("Wacom Intuos Pro Medium", "8192 уровней давления, мультитач, наклон, беспроводной", "19999.99", 10, seller1, "Периферия");
                addProduct("Elgato Facecam Pro", "4K 60fps, Sony STARVIS 2, фиксированный фокус", "19999.99", 12, seller1, "Периферия");
                addProduct("Logitech Brio 4K Pro", "4K 30fps, HDR, RightLight 3, двойной стереомикрофон", "16999.99", 15, seller1, "Периферия");
                addProduct("Microsoft Arc Mouse", "Bluetooth, складная, 3 кнопки, 6 мес от батарей", "3999.99", 30, seller1, "Периферия");
                // ── TechShop — накопители дополнительно ───────────────────────────────────
                addProduct("SSD WD Black SN850X 2 ТБ", "M.2 NVMe PCIe 4.0, 7300/6600 МБ/с, DRAM кэш", "15999.99", 20, seller1, "Накопители");
                addProduct("SSD Samsung 990 Pro 2 ТБ", "M.2 NVMe PCIe 4.0, 7450/6900 МБ/с", "14999.99", 25, seller1, "Накопители");
                addProduct("SSD Kingston KC3000 2 ТБ", "M.2 NVMe PCIe 4.0, 7000/7000 МБ/с, 256-bit AES", "11999.99", 20, seller1, "Накопители");
                addProduct("SSD Seagate FireCuda 530 1 ТБ", "M.2 NVMe PCIe 4.0, 7300/6900 МБ/с, гарантия 5 лет", "9999.99", 25, seller1, "Накопители");
                addProduct("Внешний SSD Samsung T7 Shield 2 ТБ", "USB 3.2 Gen 2, 1050 МБ/с, IP65, 3-метровое падение", "12999.99", 20, seller1, "Накопители");
                addProduct("Внешний HDD Seagate Backup Plus 5 ТБ", "USB 3.0, 120 МБ/с, автобэкап, 2 года гарантия", "7999.99", 25, seller1, "Накопители");
                addProduct("USB Flash SanDisk Ultra Luxe 256 ГБ", "USB 3.2 Gen 1, 150 МБ/с, металлический корпус", "1999.99", 50, seller1, "Накопители");
                addProduct("MicroSD Samsung Pro Ultimate 256 ГБ", "280/180 МБ/с, V60, водо/ударо/рентгенозащита", "3499.99", 40, seller1, "Накопители");
                addProduct("NAS Synology DS923+", "AMD Ryzen R1600, 4 отсека, 4 ГБ ECC RAM, PCIe NVMe кэш", "44999.99", 5, seller1, "Накопители");
                addProduct("HDD Seagate BarraCuda 4 ТБ", "SATA III 6 Гбит/с, 5400 об/мин, 256 МБ кэш, 3.5\"", "5999.99", 30, seller1, "Накопители");
                // ── TechShop — комплектующие ────────────────────────────────────────────────
                addProduct("Процессор Intel Core i9-14900K", "24 ядра (8P+16E), 6.0 ГГц Boost, LGA1700, TDP 125 Вт", "44999.99", 8, seller1, "Комплектующие");
                addProduct("Процессор AMD Ryzen 9 7950X3D", "16 ядер, 5.7 ГГц, AM5, 144 МБ L3, 3D V-Cache", "59999.99", 7, seller1, "Комплектующие");
                addProduct("Процессор Intel Core i5-14600K", "14 ядер (6P+8E), 5.3 ГГц Boost, без кулера", "24999.99", 15, seller1, "Комплектующие");
                addProduct("Процессор AMD Ryzen 5 7600X", "6 ядер, 5.3 ГГц, AM5, PCIe 5.0, без кулера", "19999.99", 20, seller1, "Комплектующие");
                addProduct("Видеокарта NVIDIA RTX 4090 24 ГБ", "16384 CUDA, DLSS 3, AV1, 450 Вт TDP, 3-вентилятора", "149999.99", 5, seller1, "Комплектующие");
                addProduct("Видеокарта NVIDIA RTX 4080 Super 16 ГБ", "10240 CUDA, DLSS 3, AV1, 320 Вт TDP", "99999.99", 7, seller1, "Комплектующие");
                addProduct("Видеокарта AMD RX 7900 XTX 24 ГБ", "6144 CU, 355 Вт TDP, DisplayPort 2.1, HDMI 2.1", "89999.99", 8, seller1, "Комплектующие");
                addProduct("Видеокарта NVIDIA RTX 4070 Ti Super 16 ГБ", "8448 CUDA, DLSS 3.5, 285 Вт TDP", "79999.99", 10, seller1, "Комплектующие");
                addProduct("ОЗУ Corsair Vengeance 64 ГБ DDR5-6000", "2×32 ГБ, XMP 3.0, CL30, алюминий Heatspreader", "14999.99", 15, seller1, "Комплектующие");
                addProduct("ОЗУ G.Skill Trident Z5 RGB 32 ГБ DDR5-7200", "2×16 ГБ, XMP 3.0, CL34, Intel XMP Certified", "11999.99", 20, seller1, "Комплектующие");
                addProduct("Материнская плата ASUS ROG Maximus Z790 Apex", "LGA1700, DDR5, PCIe 5.0, Thunderbolt 4, WiFi 6E", "49999.99", 5, seller1, "Комплектующие");
                addProduct("Материнская плата MSI MAG B650M Mortar WiFi", "AM5, DDR5, PCIe 5.0, WiFi 6E, 2.5G LAN, mATX", "16999.99", 12, seller1, "Комплектующие");
                addProduct("БП Corsair HX1200i 1200 Вт", "80+ Platinum, модульный, ATX 3.0, PCIe 5.0 кабель", "19999.99", 10, seller1, "Комплектующие");
                addProduct("Корпус Fractal Design Torrent RGB", "Full Tower, 3×140 мм вентилятора, боковое стекло", "14999.99", 8, seller1, "Комплектующие");
                addProduct("СЖО NZXT Kraken Elite 360 RGB", "360 мм радиатор, 3×120 мм HALOS, ARGB экран", "16999.99", 10, seller1, "Комплектующие");
                // ── TechShop — сетевое оборудование ────────────────────────────────────────
                addProduct("Wi-Fi роутер ASUS RT-BE96U BE19000", "Wi-Fi 7, 2.5G WAN, 10G LAN, 4×мощные антенны", "34999.99", 8, seller1, "Сетевое");
                addProduct("Mesh-система TP-Link Deco BE85 (3 шт.)", "Wi-Fi 7, BE19000, 2.5G порты, до 700 кв.м.", "44999.99", 6, seller1, "Сетевое");
                addProduct("Wi-Fi роутер Netgear Orbi 960 RBK962S", "Wi-Fi 6E, трёхдиапазонный, 2.5 Гбит LAN, 4 порта", "39999.99", 7, seller1, "Сетевое");
                addProduct("Коммутатор TP-Link TL-SG108E 8-port Managed", "8×1G, веб-интерфейс, VLAN 802.1Q, QoS", "3499.99", 25, seller1, "Сетевое");
                addProduct("Wi-Fi точка доступа Ubiquiti UniFi U7 Pro", "Wi-Fi 7, 6 ГГц поддержка, PoE++, 9.3 Гбит", "19999.99", 10, seller1, "Сетевое");
                addProduct("Powerline TP-Link TL-PA9020P KIT", "AV2000, 2×Gigabit LAN, пассивная сквозная розетка", "4999.99", 20, seller1, "Сетевое");
                addProduct("Ethernet адаптер UGREEN USB-C 2.5G", "USB 3.1, 2500 Мбит/с, ASIX AX88179B, без драйверов", "2499.99", 35, seller1, "Сетевое");
                // ── TechShop — умный дом ────────────────────────────────────────────────────
                addProduct("Умная колонка Яндекс Станция 2", "Алиса, 30 Вт, Dolby Atmos, Zigbee хаб, LED панель", "14999.99", 20, seller1, "Умный дом");
                addProduct("Робот-пылесос Xiaomi Robot Vacuum X20 Pro", "LiDAR, автоопустошение, мытьё пола, 9000 Па всасывание", "54999.99", 8, seller1, "Умный дом");
                addProduct("Умный пылесос iRobot Roomba j9+", "CleanBase, AutoFill, PrecisionVision, сопряжение Braava", "59999.99", 6, seller1, "Умный дом");
                addProduct("Умный замок Aqara A100 Zigbee", "отпечаток пальца, код, карта, умный дом интеграция", "12999.99", 15, seller1, "Умный дом");
                addProduct("Умный дисплей Google Nest Hub Max", "10\" экран, камера, Google Assistant, Chromecast", "19999.99", 10, seller1, "Умный дом");
                addProduct("Хаб умного дома Aqara Hub M3", "Zigbee 3.0, Matter, HomeKit, Z-Wave, 64 устройства", "8999.99", 18, seller1, "Умный дом");
                addProduct("Умная LED-лента Govee RGBIC Pro 10 м", "Matter, 16 млн цветов, сегментное управление, IP65", "4999.99", 30, seller1, "Умный дом");
                addProduct("Умный термостат Ecobee SmartThermostat Premium", "встроенная Alexa, датчик присутствия, Apple HomeKit", "19999.99", 12, seller1, "Умный дом");
                // ── TechShop — планшеты ─────────────────────────────────────────────────────
                addProduct("Apple iPad Pro 13\" M4", "M4, 256 ГБ, Ultra Retina XDR OLED, Wi-Fi 6E, USB 4", "109999.99", 8, seller1, "Планшеты");
                addProduct("Apple iPad Air 13\" M2", "M2, 128 ГБ, 13\" Liquid Retina, Wi-Fi 6E, USB-C", "79999.99", 12, seller1, "Планшеты");
                addProduct("Samsung Galaxy Tab S9 Ultra", "Snapdragon 8 Gen 2, 14.6\" AMOLED, 12/256, S Pen", "84999.99", 10, seller1, "Планшеты");
                addProduct("Microsoft Surface Pro 10", "Intel Core Ultra 7, 16 ГБ, 256 ГБ SSD, 13\" IPS", "99999.99", 8, seller1, "Планшеты");
                addProduct("Lenovo Tab P12 Pro", "Snapdragon 870, 12.6\" AMOLED 2K, 8/256, Dolby Vision", "44999.99", 12, seller1, "Планшеты");
                addProduct("Xiaomi Pad 6 Pro", "Snapdragon 8+ Gen 1, 11\" 144 Гц, 12/256, Dolby Atmos", "39999.99", 18, seller1, "Планшеты");
                addProduct("Huawei MatePad Pro 13.2\" OLED", "Kirin 9000S, 8/256 ГБ, 144 Гц OLED, Wi-Fi 6E", "64999.99", 10, seller1, "Планшеты");
                // ── TechShop — игровые устройства ──────────────────────────────────────────
                addProduct("Meta Quest 3 512 ГБ", "Snapdragon XR2 Gen 2, смешанная реальность, Touch Plus", "54999.99", 10, seller1, "Игровые");
                addProduct("ASUS ROG Ally X", "AMD Z1 Extreme, 24 ГБ RAM, 1 ТБ SSD, 7\" FHD 120 Гц", "69999.99", 8, seller1, "Игровые");
                addProduct("Steam Deck OLED 1 ТБ", "7.4\" OLED HDR, AMD APU, 1 ТБ NVMe, 3–12 ч игры", "69999.99", 8, seller1, "Игровые");
                addProduct("Геймпад PlayStation DualSense Edge", "настраиваемые стики и кнопки, сменные профили", "9999.99", 20, seller1, "Игровые");
                addProduct("Геймпад Xbox Elite Series 2 Core", "регулируемые стики и курки, лепестки, съёмный кабель", "11999.99", 15, seller1, "Игровые");
                addProduct("Руль Logitech G923 TrueForce", "TRUEFORCE обратная связь, педали, 900° поворот", "24999.99", 8, seller1, "Игровые");
                addProduct("Игровая гарнитура SteelSeries Arctis Nova Pro", "модульный DSP, SwapTech аккумулятор, ANC, 2.4 ГГц", "19999.99", 12, seller1, "Игровые");
                addProduct("Игровой стол IKEA UPPSPEL", "Регулировка высоты 70–120 см, 180×80 см, кабельменеджмент", "34999.99", 6, seller1, "Игровые");
                // ── TechShop — камеры и экшн ───────────────────────────────────────────────
                addProduct("GoPro Hero 13 Black", "5.3K60, HyperSmooth 7.0, GP2 процессор, Wi-Fi 6", "34999.99", 10, seller1, "Фото/Видео");
                addProduct("DJI Osmo Action 4", "4K 120fps, 10-бит, горизонт-стабилизация, IPX68, -20°C", "29999.99", 12, seller1, "Фото/Видео");
                addProduct("Sony ZV-E10 II", "26 Мп, 4K 60fps, поворотный экран, логарифмический S-Cinetone", "59999.99", 8, seller1, "Фото/Видео");
                addProduct("DJI Mini 4K Drone", "4K HDR, QuickShots, ActiveTrack, 51 мин полёта", "39999.99", 7, seller1, "Фото/Видео");
                addProduct("Стабилизатор DJI RS 3 Mini", "до 2 кг камера, 3-осевой, автофокус, Bluetooth", "14999.99", 12, seller1, "Фото/Видео");
                // ── TechShop — разное ───────────────────────────────────────────────────────
                addProduct("Электронная книга Kindle Scribe", "10.2\" e-ink 300 ppi, Premium Pen, 32 ГБ, подсветка", "24999.99", 12, seller1, "Другое");
                addProduct("Принтер Canon PIXMA G3420 МФУ", "СНПЧ, Wi-Fi, цветной, сканер A4, факс, 1800 стр/картридж", "12999.99", 10, seller1, "Другое");
                addProduct("Портативный проектор XGIMI Halo+", "1080p, 900 ANSI люмен, 15Вт Harman Kardon, Android TV", "59999.99", 6, seller1, "Другое");
                addProduct("Power Bank Baseus 65 Вт 30000 мАч", "USB-C PD 65 Вт, 2×USB-A, быстрая зарядка, дисплей", "4999.99", 30, seller1, "Другое");
                addProduct("Трекер Apple AirTag (4-pack)", "U1 чип, UWB, находить в iOS, IP67, год от батарейки", "7999.99", 25, seller1, "Другое");
                // ── AudioWorld — наушники дополнительно ────────────────────────────────────
                addProduct("Beyerdynamic DT 990 Pro 250 Ом", "Открытые, 250 Ом, студийный звук, мягкие амбушюры", "9999.99", 20, seller2, "Аудио");
                addProduct("Audio-Technica ATH-M70x", "Закрытые, 48 Ом, 5–40 000 Гц, 3 съёмных кабеля", "14999.99", 15, seller2, "Аудио");
                addProduct("Shure SRH1840", "Открытые, 65 Ом, эталонный мониторинг, съёмный кабель", "24999.99", 10, seller2, "Аудио");
                addProduct("AKG K702", "Открытые, 62 Ом, плоская АЧХ, заменяемый кабель 3 м", "11999.99", 18, seller2, "Аудио");
                addProduct("HiFiMan HE400se", "Планарные, 25 Ом, 8–50 000 Гц, Stealth Magnet", "12999.99", 15, seller2, "Аудио");
                addProduct("Sony IER-M9 TWS Studio Monitor", "5 арматур, шумоизоляция -26 дБ, MMCX кабель", "39999.99", 6, seller2, "Аудио");
                addProduct("Jabra Evolve2 85 ANC Business", "ANC, 37 ч работы, Busylight, Teams/UC, Bluetooth 5.0", "19999.99", 15, seller2, "Аудио");
                addProduct("Sony LinkBuds S", "ANC + Ambient Sound, 6 мм драйвер, IPX4, 20 ч с кейсом", "9999.99", 25, seller2, "Аудио");
                addProduct("JBL Tour Pro 2", "ANC, Smart Charging Case с экраном, 10 ч + 30 ч кейс", "14999.99", 20, seller2, "Аудио");
                addProduct("Anker Soundcore Space Q45 ANC", "До 65 ч, сильный ANC, LDAC, прозрачность", "6999.99", 30, seller2, "Аудио");
                // ── AudioWorld — колонки домашние ────────────────────────────────────────────
                addProduct("Sonos Era 300 Dolby Atmos", "Wi-Fi, Bluetooth, Trueplay адаптация, голосовые ассистенты", "44999.99", 8, seller2, "Аудио");
                addProduct("KEF LSX II LT", "Магнитная планарная пищалка, Wi-Fi, Roon Ready, 100 Вт", "74999.99", 5, seller2, "Аудио");
                addProduct("Harman Kardon Citation 500", "Smart Speaker, 50 Вт, Google Assistant, AirPlay 2", "24999.99", 10, seller2, "Аудио");
                addProduct("JBL Boombox 3", "60 Вт, IP67, 24 ч работы, PowerBank, PartyBoost", "29999.99", 12, seller2, "Аудио");
                addProduct("Sony SRS-XG300", "30 Вт, IP67, 25 ч работы, мульти-подключение, X-Balanced", "19999.99", 15, seller2, "Аудио");
                addProduct("Саундбар Sony HT-A7000", "7.1.2 ch, 500 Вт, Dolby Atmos, DTS:X, 360 Reality Audio", "79999.99", 5, seller2, "Аудио");
                // ── AudioWorld — студийное оборудование ─────────────────────────────────────
                addProduct("Аудиоинтерфейс Universal Audio Volt 476P", "4 вх/4 вых, 76 Вт, аналоговое сжатие, USB-C", "29999.99", 8, seller2, "Аудио");
                addProduct("Аудиоинтерфейс Native Instruments Komplete Audio 6 MK2", "6 вх/6 вых, XLR/Jack, MIDI I/O, 24 бит/192 кГц", "19999.99", 10, seller2, "Аудио");
                addProduct("Студийный монитор Yamaha HS8 (пара)", "8\", 2-полосный, активный, 75+45 Вт, XLR/TRS вход", "36999.99", 8, seller2, "Аудио");
                addProduct("Студийный монитор Adam Audio T7V (пара)", "7\", A-ART пищалка, 50 Вт, XLR/RCA/TRS", "24999.99", 10, seller2, "Аудио");
                addProduct("Студийный микрофон Neumann TLM 102", "Конденсаторный, кардиоида, -64 дБА шум, 20 Гц–20 кГц", "54999.99", 5, seller2, "Аудио");
                addProduct("MIDI-контроллер Arturia KeyLab Essential 88", "88 клавиш, velocity sensitive, 16 пэдов, 9 фейдеров", "24999.99", 8, seller2, "Аудио");
                addProduct("Рекордер Zoom H6 Essential", "6 дорожек, 32-bit float, MIDI I/O, USB-C, XLR/Jack вх.", "19999.99", 10, seller2, "Аудио");
                // ── AudioWorld — DJ оборудование ─────────────────────────────────────────────
                addProduct("DJ-контроллер Pioneer DDJ-FLX6-GT", "4 деки, 6\" джогвилы, Serato/rekordbox, USB-C", "49999.99", 6, seller2, "Аудио");
                addProduct("DJ-система Pioneer XDJ-RX3", "Автономная, 10.1\" экран, rekordbox, 2 деки, USB", "119999.99", 3, seller2, "Аудио");
                addProduct("DJ-сэмплер Akai MPC One+", "Bluetooth, 7\" сенсорный экран, 16 пэдов, USB хост", "34999.99", 8, seller2, "Аудио");
                // ── AudioWorld — синтезаторы ─────────────────────────────────────────────────
                addProduct("Синтезатор Korg Minilogue XD", "4-голосный полифонический, 37 клавиш, мультиэнджин", "44999.99", 7, seller2, "Аудио");
                addProduct("Синтезатор Roland JUNO-X", "8 голосов, FM + VA + PCM, 61 клавиша, JUNO наследие", "89999.99", 5, seller2, "Аудио");
                addProduct("Синтезатор Arturia MiniFreak V2", "Гибридный VA/FM/WaveTable, 6 голосов, 37 клавиш", "29999.99", 10, seller2, "Аудио");
                // ── AudioWorld — микрофоны дополнительно ─────────────────────────────────────
                addProduct("Микрофон Shure SM7dB", "Динамический, встроенный предусилитель +18/28 дБ, XLR", "29999.99", 8, seller2, "Аудио");
                addProduct("Микрофон Røde NT1 5th Gen", "Конденсаторный, кардиоида, USB + XLR, 32-bit float", "19999.99", 12, seller2, "Аудио");
                addProduct("Микрофон HyperX QuadCast S", "USB, конденсаторный, 4 режима, ARGB, амортизатор", "7999.99", 20, seller2, "Аудио");
                addProduct("Микрофон Elgato Wave 3", "USB-C, конденсаторный, кардиоида, Clipguard, 96 кГц", "8999.99", 18, seller2, "Аудио");
                addProduct("Петличный микрофон Røde Wireless GO II", "2.4 ГГц беспроводной, двойной передатчик, 32-bit float", "22999.99", 10, seller2, "Аудио");
                addProduct("Микрофон Lewitt LCT 440 Pure", "Конденсаторный, кардиоида, -87 дБА шум, 20–20 000 Гц", "17999.99", 10, seller2, "Аудио");

                log.info("Created extended product catalog: {} total products", productRepository.count());
            }

            if (productRepository.count() < 340) {
                // ── TechShop — Фото/Видео расширение ─────────────────────────────────────
                addProduct("Беззеркальная камера Sony Alpha A7 IV", "33 Мп full-frame CMOS, 4K 60fps, 693 точки PDAF, двойной слот SD", "184999.99", 6, seller1, "Фото/Видео");
                addProduct("Беззеркальная камера Canon EOS R6 Mark II", "24.2 Мп full-frame, 4K 60fps 10-бит, IBIS 8 ступеней, Eye AF", "154999.99", 7, seller1, "Фото/Видео");
                addProduct("Беззеркальная камера Fujifilm X-T5", "40.2 Мп APS-C, 6.2K RAW, IBIS 7 ступеней, 2 слота UHS-II", "129999.99", 8, seller1, "Фото/Видео");
                addProduct("Объектив Sony FE 50mm f/1.2 GM", "G Master, 11 лепестков диафрагмы, ZEISS T* покрытие, 778 г", "89999.99", 5, seller1, "Фото/Видео");
                addProduct("Объектив Sigma 24-70mm f/2.8 DG DN Art", "Беззеркальный, L-mount/Sony E, XD линейный мотор, 830 г", "69999.99", 7, seller1, "Фото/Видео");
                addProduct("Штатив Joby GorillaPod 5K Kit", "Гибкие ноги, нагрузка 5 кг, шаровая голова с ручкой быстросъёма", "8999.99", 15, seller1, "Фото/Видео");
                addProduct("Видеосвет Elgato Key Light Air", "1400 люмен, 2900–7000K, Wi-Fi управление, 45 Вт LED панель", "12999.99", 12, seller1, "Фото/Видео");
                addProduct("Светодиодный осветитель Godox SL60IID", "60 Вт, 2800–6500K, рефлектор 150°, Bowens, DMX управление", "14999.99", 10, seller1, "Фото/Видео");
                addProduct("Карта памяти Sony SF-G TOUGH 256 ГБ SDXC", "V90, 300 МБ/с чтение, 299 МБ/с запись, IP57, двойная защита", "9999.99", 20, seller1, "Фото/Видео");
                addProduct("Рюкзак Lowepro Flipside 400 AW III", "Для зеркалки + 3 объектива, доступ сбоку, чехол от дождя, 19 л", "11999.99", 10, seller1, "Фото/Видео");
                // ── TechShop — Сетевое расширение ────────────────────────────────────────
                addProduct("Wi-Fi роутер Keenetic Giga KN-1011", "Wi-Fi 6 AX1800, 4×Gigabit LAN, USB 3.0, KeeneticOS, до 700 Мбит", "8999.99", 18, seller1, "Сетевое");
                addProduct("4G-роутер Keenetic Hero 4G+ KN-2311", "LTE CAT12, Wi-Fi 5 AC1200, Dual-SIM, RJ45 WAN/LAN, батарея", "9999.99", 12, seller1, "Сетевое");
                addProduct("Коммутатор NETGEAR GS308E 8-port Managed", "8×1G, веб-интерфейс, VLAN, QoS, кабельный тест, пластик", "2999.99", 25, seller1, "Сетевое");
                addProduct("Wi-Fi адаптер TP-Link Archer T3U Plus USB", "AC1300, USB 3.0, внешняя антенна, MU-MIMO, WPA3", "1499.99", 40, seller1, "Сетевое");
                addProduct("Патч-корды Cat8 2м UGREEN (5-pack)", "40 Гбит, 2000 МГц, медь OFC, SFTP экранирование, RJ45", "2499.99", 30, seller1, "Сетевое");
                // ── TechShop — Планшеты расширение ───────────────────────────────────────
                addProduct("Apple iPad 10-го поколения 64 ГБ", "A14 Bionic, 10.9\" Liquid Retina, Wi-Fi 6, USB-C, TouchID сбоку", "44999.99", 15, seller1, "Планшеты");
                addProduct("Apple iPad mini 7 256 ГБ", "A17 Pro, 8.3\" Liquid Retina, Wi-Fi 6E, USB-C, Apple Pencil Pro", "74999.99", 10, seller1, "Планшеты");
                addProduct("Samsung Galaxy Tab A9+", "Snapdragon 695, 11\" 90 Гц TFT, 8/128 ГБ, 7040 мАч, 45 Вт", "24999.99", 20, seller1, "Планшеты");
                addProduct("Wacom Cintiq 16 Full HD", "Перо 8192 давления, 15.6\" IPS FHD, Pro Pen 2, без батареи", "44999.99", 8, seller1, "Планшеты");
                addProduct("XP-PEN Artist 12 Pro 2-го поколения", "11.9\" IPS, 8192 давления, наклон ±60°, 8 клавиш+ролик, USB-C", "19999.99", 12, seller1, "Планшеты");
                // ── TechShop — Умный дом расширение ──────────────────────────────────────
                addProduct("Умная камера Xiaomi Smart Camera C300 2K", "2K 1296p, 360°, ИК ночное видение, двусторонняя связь, Wi-Fi", "3999.99", 25, seller1, "Умный дом");
                addProduct("Умная розетка TP-Link Tapo P110", "Wi-Fi, мониторинг потребления, 2300 Вт, расписание, Tapo App", "1499.99", 40, seller1, "Умный дом");
                addProduct("Умная лампа Philips Hue White&Color E27 4-pack", "806 лм, 16 млн цветов, ZigBee, Matter, 10 000 ч ресурс", "12999.99", 15, seller1, "Умный дом");
                addProduct("Умный пульт Broadlink RM4 Mini Pro", "ИК+RF 433/315 МГц, Wi-Fi, управление TV/кондиционером, BroadLink", "2499.99", 30, seller1, "Умный дом");
                addProduct("Датчик протечки Aqara Water Leak Sensor T1", "Zigbee, сигнал 80 дБ, IP67, 2 лет от батареи, HomeKit/Home", "2499.99", 35, seller1, "Умный дом");
                // ── TechShop — Игровые расширение ────────────────────────────────────────
                addProduct("Nintendo Switch OLED белая", "7\" OLED экран, Joy-Con, 64 ГБ хранилище, 4–9 ч работы", "34999.99", 15, seller1, "Игровые");
                addProduct("Sony PlayStation 5 Slim Disk Edition", "AMD Zen 2, RDNA 2, 825 ГБ SSD, 4K 120fps, DualSense", "49999.99", 10, seller1, "Игровые");
                addProduct("Xbox Series S 512 ГБ", "AMD Zen 2 + RDNA 2, 512 ГБ NVMe, 1440p 120fps, Game Pass", "29999.99", 12, seller1, "Игровые");
                addProduct("Игровое кресло DXRacer Formula F11", "Экокожа + ткань, поясничная/шейная подушки, 4D подлокотники", "24999.99", 10, seller1, "Игровые");
                addProduct("Гарнитура Razer Kraken V3 HyperSense", "USB, HyperSense тактильная обратная связь, THX, 50 мм драйвер", "9999.99", 18, seller1, "Игровые");

                log.info("Created thin-category products: {} total products", productRepository.count());
            }

            // Исправляем ошибочно назначенные категории (идемпотентно).
            fixProductCategories();

            // Fallback: задаём категорию тем товарам, у которых она не указана.
            productRepository.findAll().stream()
                    .filter(p -> p.getCategory() == null)
                    .forEach(p -> {
                        p.setCategory(categoryService.findOrCreate(categorizeByName(p.getName())));
                        productRepository.save(p);
                    });

            // Точки самовывоза (20 точек по Москве) — сидим один раз, если таблица пуста.
            if (pickupPointRepository.count() == 0) {
                seedPickupPoints();
            }

            // Синхронизируем все товары из PostgreSQL в OpenSearch.
            // Если OpenSearch недоступен — productSearchService логирует warning и продолжает.
            productSearchService.reindexAll(productRepository.findAllForReindex());

            // Создаём index pattern в OpenSearch Dashboards (если Dashboards доступен).
            // Без этого Discover показывает "No index pattern" при первом входе.
            createOpenSearchIndexPattern();

            log.info("=== Marketplace ready ===");
            log.info("  client@example.com   / pass → покупатель");
            log.info("  seller1@example.com  / pass → TechShop");
            log.info("  seller2@example.com  / pass → AudioWorld");
            log.info("  admin@example.com    / pass → администратор");
        };
    }

    /**
     * Идемпотентно создаёт пользователя или возвращает существующего.
     * Если у найденного пользователя пароль хранится в открытом виде (не начинается с BCrypt-префикса
     * "$2a$") — перехэшировывает его. Так старые сид-данные доводятся до корректного состояния.
     */
    private User ensureUser(String email, String password, String fullName, Role role, String shopName) {
        return userRepository.findByEmail(email).map(u -> {
            if (!u.getPassword().startsWith("$2a$")) {
                u.setPassword(passwordEncoder.encode(password));
                return userRepository.save(u);
            }
            return u;
        }).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode(password));
            u.setFullName(fullName);
            u.setRole(role);
            u.setShopName(shopName);
            u.setBalance(new BigDecimal("0"));
            User saved = userRepository.save(u);
            log.info("Created user: {} ({})", email, role);
            return saved;
        });
    }

    /** Сидинг 20 точек самовывоза по Москве (ближе к центру). */
    private void seedPickupPoints() {
        addPickupPoint("ТЦ «Афимолл Сити»",      "Пресненская наб., 2",        "Выставочная");
        addPickupPoint("ЦУМ",                    "ул. Петровка, 2",            "Театральная");
        addPickupPoint("ГУМ",                    "Красная площадь, 3",         "Охотный Ряд");
        addPickupPoint("ТЦ «Атриум»",            "ул. Земляной Вал, 33",       "Курская");
        addPickupPoint("ТЦ «Охотный Ряд»",       "Манежная площадь, 1",        "Площадь Революции");
        addPickupPoint("ТЦ «Европейский»",       "пл. Киевского Вокзала, 2",   "Киевская");
        addPickupPoint("ТРЦ «Авиапарк»",         "Ходынский бульвар, 4",       "ЦСКА");
        addPickupPoint("ТЦ «Цветной»",           "Цветной бульвар, 15, стр. 1","Цветной бульвар");
        addPickupPoint("Петровский пассаж",      "ул. Петровка, 10",           "Кузнецкий Мост");
        addPickupPoint("Смоленский Пассаж",      "Смоленская площадь, 3",      "Смоленская");
        addPickupPoint("ТЦ «Новинский»",         "Новинский бульвар, 31",      "Баррикадная");
        addPickupPoint("ТРЦ «Белая Площадь»",    "ул. Лесная, 5",              "Белорусская");
        addPickupPoint("ТЦ «Тишинка»",           "Тишинская площадь, 1",       "Маяковская");
        addPickupPoint("ТРК «Город Лефортово»",  "шоссе Энтузиастов, 12, к. 2","Авиамоторная");
        addPickupPoint("ТЦ «Гагаринский»",       "ул. Вавилова, 3",            "Ленинский проспект");
        addPickupPoint("ТРЦ «Ривьера»",          "Автозаводская ул., 18",      "Автозаводская");
        addPickupPoint("ТЦ «Метрополис»",        "Ленинградское ш., 16А, стр. 4","Войковская");
        addPickupPoint("ТЦ «Щука»",              "ул. Щукинская, 42",          "Щукинская");
        addPickupPoint("ТРЦ «Океания»",          "Кутузовский проспект, 57",   "Славянский бульвар");
        addPickupPoint("ТЦ «Капитолий»",         "проспект Вернадского, 6",    "Университет");
        log.info("Created {} pickup points", pickupPointRepository.count());
    }

    private void addPickupPoint(String name, String address, String metro) {
        PickupPoint p = new PickupPoint();
        p.setName(name);
        p.setAddress(address);
        p.setMetro(metro);
        p.setActive(true);
        pickupPointRepository.save(p);
    }

    private void addProduct(String name, String description, String price, int stock, User seller, String categoryName) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setSeller(seller);
        // Находим или создаём Category по имени.
        p.setCategory(categoryService.findOrCreate(categoryName));
        productRepository.save(p);
    }

    /**
     * Создаёт index pattern "marketplace-logs-*" в OpenSearch Dashboards через Saved Objects API.
     * Вызывается при старте — idempotent: если pattern уже есть, 409 Conflict игнорируется.
     *
     * Адрес Dashboards читается из env OPENSEARCH_DASHBOARDS_URL (K8s ConfigMap).
     * В локальной разработке env не задан → метод молча пропускает.
     */
    private void createOpenSearchIndexPattern() {
        String dashUrl = System.getenv("OPENSEARCH_DASHBOARDS_URL");
        if (dashUrl == null || dashUrl.isBlank()) return; // не K8s-среда

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        try {
            // Создаём index pattern для логов
            String logsBody = """
                    {"attributes":{"title":"marketplace-logs-*","timeFieldName":"@timestamp"}}
                    """.strip();
            sendDashboardsRequest(client, dashUrl + "/api/saved_objects/index-pattern", logsBody);

            // Создаём index pattern для товаров (OpenSearch-индекс из ProductSearchService)
            String productsBody = """
                    {"attributes":{"title":"products","timeFieldName":null}}
                    """.strip();
            sendDashboardsRequest(client, dashUrl + "/api/saved_objects/index-pattern", productsBody);

            log.info("OpenSearch Dashboards index patterns created (or already exist)");
        } catch (Exception e) {
            log.warn("Could not create OpenSearch Dashboards index patterns: {}", e.getMessage());
        }
    }

    private void sendDashboardsRequest(HttpClient client, String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("osd-xsrf", "true")    // обязательный заголовок OpenSearch Dashboards API
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        // 200/201 = создан, 409 = уже существует — оба варианта нормальные
        if (res.statusCode() >= 400 && res.statusCode() != 409) {
            log.warn("Dashboards API {} → {}", url, res.statusCode());
        }
    }

    /**
     * Пересоздаёт CHECK-ограничение на колонку role в таблице users.
     * При ddl-auto=update Hibernate не обновляет существующие CHECK constraints.
     */
    private void fixRoleConstraint() {
        try {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            jdbc.execute("""
                ALTER TABLE users ADD CONSTRAINT users_role_check
                    CHECK (role IN ('CLIENT','SELLER','ADMIN','ACCOUNTANT'))
                """);
            log.info("users_role_check constraint updated");
        } catch (Exception e) {
            log.warn("Could not update users_role_check: {}", e.getMessage());
        }
    }

    /**
     * Исправляет неправильно назначенные категории товаров (идемпотентно).
     * Запускается при каждом старте — безопасно, т.к. просто устанавливает верное значение.
     */
    private void fixProductCategories() {
        long kompId  = categoryService.findOrCreate("Комплектующие").getId();
        long fotoId  = categoryService.findOrCreate("Фото/Видео").getId();
        long umId    = categoryService.findOrCreate("Умный дом").getId();
        long setId   = categoryService.findOrCreate("Сетевое").getId();
        long igId    = categoryService.findOrCreate("Игровые").getId();
        long drugId  = categoryService.findOrCreate("Другое").getId();

        // Комплектующие: процессоры, видеокарты, ОЗУ, материнские платы, БП, корпуса, СЖО
        jdbc.update("""
            UPDATE products SET category_id = ?
            WHERE LOWER(name) SIMILAR TO
                '%(процессор|видеокарта|озу |материнская плата|бп corsair|корпус fractal|сжо nzxt|g\\.skill)%'
            """, kompId);

        // Фото/Видео: экшн-камеры, дроны, стабилизаторы
        jdbc.update("""
            UPDATE products SET category_id = ?
            WHERE (LOWER(name) LIKE '%gopro%'
               OR LOWER(name) LIKE '%dji%'
               OR LOWER(name) LIKE '%стабилизатор%'
               OR LOWER(name) LIKE '%sony zv-%'
               OR (LOWER(name) LIKE '%камера%' AND LOWER(name) LIKE '%беззеркальн%')
               OR (LOWER(name) LIKE '%объектив%')
               OR LOWER(name) LIKE '%штатив%'
               OR LOWER(name) LIKE '%видеосвет%'
               OR LOWER(name) LIKE '%осветитель%'
               OR LOWER(name) LIKE '%lowepro%')
            """, fotoId);

        // Умный дом
        jdbc.update("""
            UPDATE products SET category_id = ?
            WHERE (LOWER(name) LIKE '%яндекс станция%'
               OR LOWER(name) LIKE '%робот-пылесос%'
               OR LOWER(name) LIKE '%roomba%'
               OR LOWER(name) LIKE '%умный замок%'
               OR LOWER(name) LIKE '%google nest%'
               OR LOWER(name) LIKE '%aqara hub%'
               OR LOWER(name) LIKE '%govee rgbic%'
               OR LOWER(name) LIKE '%ecobee%'
               OR LOWER(name) LIKE '%умная камера%'
               OR LOWER(name) LIKE '%умная розетка%'
               OR LOWER(name) LIKE '%умная лампа%'
               OR LOWER(name) LIKE '%умный пульт%'
               OR LOWER(name) LIKE '%датчик протечки%')
            """, umId);

        // Сетевое
        jdbc.update("""
            UPDATE products SET category_id = ?
            WHERE (LOWER(name) LIKE '%asus rt-be96u%'
               OR LOWER(name) LIKE '%tp-link deco be85%'
               OR LOWER(name) LIKE '%netgear orbi%'
               OR LOWER(name) LIKE '%tp-link tl-sg108e%'
               OR LOWER(name) LIKE '%ubiquiti%'
               OR LOWER(name) LIKE '%powerline tp-link%'
               OR (LOWER(name) LIKE '%ethernet адаптер%' AND LOWER(name) LIKE '%ugreen%')
               OR LOWER(name) LIKE '%keenetic giga%'
               OR LOWER(name) LIKE '%keenetic hero%'
               OR LOWER(name) LIKE '%netgear gs308e%'
               OR LOWER(name) LIKE '%archer t3u%'
               OR LOWER(name) LIKE '%патч-корды cat8%')
            """, setId);

        // Игровые
        jdbc.update("""
            UPDATE products SET category_id = ?
            WHERE (LOWER(name) LIKE '%meta quest%'
               OR LOWER(name) LIKE '%rog ally%'
               OR LOWER(name) LIKE '%steam deck%'
               OR LOWER(name) LIKE '%dualsense edge%'
               OR LOWER(name) LIKE '%xbox elite%'
               OR LOWER(name) LIKE '%logitech g923%'
               OR LOWER(name) LIKE '%arctis nova pro%'
               OR LOWER(name) LIKE '%uppspel%'
               OR LOWER(name) LIKE '%nintendo switch%'
               OR LOWER(name) LIKE '%playstation 5%'
               OR LOWER(name) LIKE '%xbox series s%'
               OR LOWER(name) LIKE '%dxracer%'
               OR (LOWER(name) LIKE '%razer kraken%' AND LOWER(name) LIKE '%hypersense%'))
            """, igId);

        // Другое
        jdbc.update("""
            UPDATE products SET category_id = ?
            WHERE (LOWER(name) LIKE '%kindle%'
               OR LOWER(name) LIKE '%canon pixma%'
               OR LOWER(name) LIKE '%xgimi%'
               OR LOWER(name) LIKE '%power bank baseus%'
               OR LOWER(name) LIKE '%airtag%')
            """, drugId);

        log.info("Product categories verified/fixed");
    }

    /**
     * Подбирает категорию товара по ключевым словам в названии (fallback для товаров без категории).
     * Должен покрывать все категории — иначе товар свалится в дефолт «Периферия» (см. последний return).
     */
    private String categorizeByName(String name) {
        if (name == null) return "Другое";
        String n = name.toLowerCase();
        if (n.contains("ноутбук") || n.contains("macbook") || n.contains("thinkbook") || n.contains("zenbook") || n.contains("spectre") || n.contains("razer blade") || n.contains("matebook")) return "Ноутбуки";
        if (n.contains("монитор")) return "Мониторы";
        if (n.contains("ssd") || n.contains("hdd") || n.contains("накопитель") || n.contains("flash") || n.contains("nas") || n.contains("microsd")) return "Накопители";
        if (n.contains("смартфон") || n.contains("iphone") || n.contains("galaxy") || n.contains("pixel") || n.contains("oneplus") || n.contains("xiaomi 14") || n.contains("nothing phone") || n.contains("zenfone") || n.contains("redmi") || n.contains("motorola edge")) return "Смартфоны";
        if (n.contains("планшет") || n.contains("ipad") || n.contains("surface") || n.contains("galaxy tab") || n.contains("cintiq") || n.contains("xp-pen")) return "Планшеты";
        if (n.contains("процессор") || n.contains("видеокарта") || n.contains("материнская") || n.contains("озу") || n.startsWith("оперативная") || n.contains("nzxt") || n.contains("fractal design") || n.contains("g.skill") || n.contains("corsair vengeance") || n.contains("asus rog maximus") || n.contains("msi mag")) return "Комплектующие";
        if (n.contains("gopro") || n.contains("dji") || n.contains("стабилизатор") || n.contains("беззеркальн") || n.contains("объектив") || n.contains("штатив") || n.contains("видеосвет") || n.contains("осветитель") || n.contains("lowepro")) return "Фото/Видео";
        if (n.contains("роутер") || n.contains("mesh") || n.contains("коммутатор") || n.contains("точка доступа") || n.contains("keenetic") || n.contains("powerline") || n.contains("netgear") || n.contains("ubiquiti") || n.contains("патч-корд") || n.contains("ethernet адаптер") || n.contains("archer t3u")) return "Сетевое";
        if (n.contains("умн") || n.contains("робот-пылесос") || n.contains("roomba") || n.contains("aqara") || n.contains("google nest") || n.contains("govee") || n.contains("ecobee") || n.contains("датчик протечки") || n.contains("philips hue") || n.contains("broadlink") || n.contains("яндекс станция")) return "Умный дом";
        if (n.contains("quest") || n.contains("rog ally") || n.contains("steam deck") || n.contains("геймпад") || n.contains("dualsen") || n.contains("xbox elite") || n.contains("игровой стол") || n.contains("игровое кресло") || n.contains("nintendo switch") || n.contains("playstation") || n.contains("xbox series") || n.contains("dxracer") || n.contains("logitech g923")) return "Игровые";
        if (n.contains("наушники") || n.contains("tws") || n.contains("airpods") || n.contains("колонка")
                || n.contains("саундбар") || n.contains("микрофон") || n.contains("аудиоинтерфейс")
                || n.contains("синтезатор") || n.contains("рекордер") || n.contains("виниловый") || n.contains("студийный монитор") || n.contains("midi")) return "Аудио";
        if (n.contains("kindle") || n.contains("принтер") || n.contains("проектор") || n.contains("power bank") || n.contains("airtag")) return "Другое";
        return "Периферия";
    }
}
