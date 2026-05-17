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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppConfig {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

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

            productRepository.findAll().stream()
                    .filter(p -> p.getCategory() == null)
                    .forEach(p -> {
                        p.setCategory(categorizeByName(p.getName()));
                        productRepository.save(p);
                    });

            log.info("=== Marketplace ready ===");
            log.info("  client@example.com   / pass → покупатель");
            log.info("  seller1@example.com  / pass → TechShop");
            log.info("  seller2@example.com  / pass → AudioWorld");
            log.info("  admin@example.com    / pass → администратор");
        };
    }

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

    private void addProduct(String name, String description, String price, int stock, User seller, String category) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setSeller(seller);
        p.setCategory(category);
        productRepository.save(p);
    }

    /**
     * Пересоздаёт CHECK-ограничение на колонку role в таблице users.
     *
     * При ddl-auto=update Hibernate не обновляет существующие CHECK constraints —
     * он создаёт их только при первом CREATE TABLE. Если БД была создана с более
     * ранней версией enum (без ACCOUNTANT), constraint содержит только старые значения
     * и INSERT нового пользователя падает с ConstraintViolationException.
     *
     * Этот метод запускается при каждом старте: он безопасно дропает constraint
     * (IF EXISTS — не бросает ошибку если его нет) и создаёт актуальный.
     * Идемпотентен: работает и на чистой БД, и на существующей.
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

    private String categorizeByName(String name) {
        if (name == null) return "Другое";
        String n = name.toLowerCase();
        if (n.contains("ноутбук") || n.contains("macbook")) return "Ноутбуки";
        if (n.contains("монитор")) return "Мониторы";
        if (n.contains("ssd") || n.contains("hdd") || n.contains("накопитель") || n.contains("flash") || n.contains("nas")) return "Накопители";
        if (n.contains("смартфон") || n.contains("iphone") || n.contains("galaxy") || n.contains("pixel")) return "Смартфоны";
        if (n.contains("планшет") || n.contains("ipad") || n.contains("surface") || n.contains("tab")) return "Планшеты";
        if (n.contains("наушники") || n.contains("tws") || n.contains("airpods") || n.contains("колонка")
                || n.contains("саундбар") || n.contains("микрофон") || n.contains("аудиоинтерфейс")
                || n.contains("синтезатор") || n.contains("рекордер") || n.contains("dj")) return "Аудио";
        return "Периферия";
    }
}
