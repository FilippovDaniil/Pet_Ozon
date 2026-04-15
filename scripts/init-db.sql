-- =============================================================
-- Скрипт инициализации базы данных для Pet_Ozon Marketplace
-- =============================================================
-- Способ 1 (Docker): запускается автоматически через docker-compose
-- Способ 2 (вручную): psql -U postgres -f scripts/init-db.sql
-- =============================================================

-- Таблица пользователей
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255),
    role        VARCHAR(50),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

-- Таблица товаров
CREATE TABLE IF NOT EXISTS products (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    price          NUMERIC(19, 2) NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    image_url      VARCHAR(255),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

-- Корзины (одна на пользователя)
CREATE TABLE IF NOT EXISTS carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users (id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Позиции в корзине
CREATE TABLE IF NOT EXISTS cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT REFERENCES carts (id),
    product_id BIGINT REFERENCES products (id),
    quantity   INTEGER NOT NULL DEFAULT 0
);

-- Заказы
CREATE TABLE IF NOT EXISTS orders (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT REFERENCES users (id),
    order_date       TIMESTAMP,
    status           VARCHAR(50),
    total_amount     NUMERIC(19, 2),
    shipping_address VARCHAR(255)
);

-- Позиции заказа
CREATE TABLE IF NOT EXISTS order_items (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT REFERENCES orders (id),
    product_id     BIGINT REFERENCES products (id),
    quantity       INTEGER NOT NULL DEFAULT 0,
    price_at_order NUMERIC(19, 2)
);

-- Счета на оплату
CREATE TABLE IF NOT EXISTS invoices (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT UNIQUE REFERENCES orders (id),
    amount     NUMERIC(19, 2),
    status     VARCHAR(50),
    created_at TIMESTAMP,
    paid_at    TIMESTAMP
);

-- Платежи
CREATE TABLE IF NOT EXISTS payments (
    id             BIGSERIAL PRIMARY KEY,
    invoice_id     BIGINT REFERENCES invoices (id),
    amount         NUMERIC(19, 2),
    payment_method VARCHAR(255),
    status         VARCHAR(50),
    timestamp      TIMESTAMP
);

-- =============================================================
-- Проверка: список созданных таблиц
-- =============================================================
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;
