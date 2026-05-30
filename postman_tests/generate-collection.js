/*
 * Генератор Postman-коллекции Pet_Ozon Marketplace.
 *
 * Зачем генератор, а не ручной JSON:
 *   • гарантированно валидный JSON (через JSON.stringify);
 *   • DRY — повторяющиеся блоки (url, заголовки, тесты) собираются хелперами;
 *   • при изменении API достаточно поправить этот файл и перегенерировать.
 *
 * Запуск:
 *   node postman_tests/generate-collection.js
 * Результат:
 *   postman_tests/Pet_Ozon_Marketplace.postman_collection.json
 *
 * Источник истины — контроллеры в src/main/java/.../controller/ (сверено вручную).
 */

const fs = require('fs');
const path = require('path');

const BASE = '{{baseUrl}}';

// ---------- Хелперы сборки запросов ----------

/** Собирает объект url для Postman v2.1 из строки пути и (опционально) query-параметров. */
function makeUrl(pathStr, query) {
  const enabled = (query || []).filter(q => !q.disabled);
  const raw = BASE + '/' + pathStr +
    (enabled.length ? '?' + enabled.map(q => `${q.key}=${q.value == null ? '' : q.value}`).join('&') : '');
  const url = {
    raw,
    host: [BASE],
    path: pathStr.split('/').filter(Boolean),
  };
  if (query && query.length) url.query = query;
  return url;
}

/** Заголовок Authorization: Bearer <переменная-токена>. */
function bearer(tokenVar) {
  return { key: 'Authorization', value: `Bearer {{${tokenVar}}}` };
}

const JSON_HEADER = { key: 'Content-Type', value: 'application/json' };

/** Событие test со скриптом (массив строк). */
function testEvent(lines) {
  return [{ listen: 'test', script: { exec: lines, type: 'text/javascript' } }];
}

/**
 * Универсальный конструктор запроса (item в Postman).
 * opts:
 *   name      — имя запроса
 *   method    — HTTP-метод
 *   path      — путь без baseUrl, напр. "api/cart/items/{{cartItemId}}"
 *   query     — массив {key,value,disabled?,description?}
 *   token     — имя переменной токена ("clientToken" и т.п.) или null для публичных
 *   json      — тело-объект (сериализуется в raw JSON, добавляется Content-Type)
 *   jsonRaw   — готовая строка тела (если нужен невалидный/пустой JSON)
 *   formdata  — массив полей formdata (для загрузки файлов)
 *   tests     — массив строк тест-скрипта
 *   desc      — описание запроса
 */
function req(opts) {
  const headers = [];
  if (opts.token) headers.push(bearer(opts.token));

  const request = { method: opts.method, header: headers };

  if (opts.json !== undefined) {
    headers.push(JSON_HEADER);
    request.body = {
      mode: 'raw',
      raw: JSON.stringify(opts.json, null, 2),
      options: { raw: { language: 'json' } },
    };
  } else if (opts.jsonRaw !== undefined) {
    headers.push(JSON_HEADER);
    request.body = {
      mode: 'raw',
      raw: opts.jsonRaw,
      options: { raw: { language: 'json' } },
    };
  } else if (opts.formdata) {
    request.body = { mode: 'formdata', formdata: opts.formdata };
  }

  request.url = makeUrl(opts.path, opts.query);
  if (opts.desc) request.description = opts.desc;

  const item = { name: opts.name };
  if (opts.tests) item.event = testEvent(opts.tests);
  item.request = request;
  return item;
}

/** Папка (folder) коллекции. */
function folder(name, description, items) {
  return { name, description, item: items };
}

// Короткие тест-хелперы, чтобы не дублировать стандартные проверки.
const t = {
  status: (code) => `pm.test("Статус ${code}", () => pm.response.to.have.status(${code}));`,
  statusOneOf: (codes) =>
    `pm.test("Статус один из [${codes.join(', ')}]", () => pm.expect(pm.response.code).to.be.oneOf([${codes.join(', ')}]));`,
  isArray: () => `pm.test("Тело — массив", () => pm.expect(pm.response.json()).to.be.an('array'));`,
  isPage: () =>
    `pm.test("Тело — Page", () => { const j = pm.response.json(); pm.expect(j).to.have.property('content'); pm.expect(j).to.have.property('totalElements'); });`,
  hasProp: (p) => `pm.test("Есть поле ${p}", () => pm.expect(pm.response.json()).to.have.property('${p}'));`,
};

// ============================================================
// ЧАСТЬ 1 — Auth (вход разных ролей, регистрация, refresh, logout)
// ============================================================

const authFolder = folder(
  'Auth',
  'Аутентификация и регистрация. Эндпоинты публичные, токен не нужен.\n\n' +
  'Ответ login/register: token (access, 15 мин), refreshToken (7 дней), userId, email, role, fullName, shopName.\n' +
  'Тест входа клиента сохраняет оба токена: clientToken и clientRefreshToken.',
  [
    req({
      name: 'Вход (клиент)',
      method: 'POST',
      path: 'api/auth/login',
      json: { email: 'client@example.com', password: 'pass' },
      tests: [
        t.status(200),
        'const j = pm.response.json();',
        'pm.test("Есть access-токен", () => pm.expect(j.token).to.be.a("string").and.not.be.empty);',
        'pm.test("Есть refresh-токен", () => pm.expect(j.refreshToken).to.be.a("string").and.not.be.empty);',
        'pm.collectionVariables.set("clientToken", j.token);',
        'pm.collectionVariables.set("clientRefreshToken", j.refreshToken);',
      ],
      desc: 'Вход клиента. Сохраняет clientToken (access) и clientRefreshToken (refresh).',
    }),
    req({
      name: 'Вход (admin)',
      method: 'POST',
      path: 'api/auth/login',
      json: { email: 'admin@example.com', password: 'pass' },
      tests: [
        t.status(200),
        'if (pm.response.code === 200) pm.collectionVariables.set("adminToken", pm.response.json().token);',
      ],
      desc: 'Вход администратора. Токен сохраняется в adminToken.',
    }),
    req({
      name: 'Вход (продавец)',
      method: 'POST',
      path: 'api/auth/login',
      json: { email: 'seller1@example.com', password: 'pass' },
      tests: [
        t.status(200),
        'if (pm.response.code === 200) pm.collectionVariables.set("sellerToken", pm.response.json().token);',
      ],
      desc: 'Вход продавца (seller1@example.com — TechShop). Токен сохраняется в sellerToken.',
    }),
    req({
      name: 'Вход (бухгалтер)',
      method: 'POST',
      path: 'api/auth/login',
      json: { email: 'accountant@example.com', password: 'pass' },
      tests: [
        t.status(200),
        'const j = pm.response.json();',
        'pm.test("Роль ACCOUNTANT", () => pm.expect(j.role).to.eql("ACCOUNTANT"));',
        'pm.collectionVariables.set("accountantToken", j.token);',
      ],
      desc: 'Вход бухгалтера. Токен сохраняется в accountantToken.',
    }),
    req({
      name: 'Регистрация',
      method: 'POST',
      path: 'api/auth/register',
      json: { email: 'newuser@example.com', password: 'secret123', fullName: 'Тестовый Пользователь' },
      tests: [
        t.status(201),
        'const j = pm.response.json();',
        'pm.test("Роль CLIENT", () => pm.expect(j.role).to.eql("CLIENT"));',
        'pm.test("Есть токены", () => { pm.expect(j.token).to.be.a("string"); pm.expect(j.refreshToken).to.be.a("string"); });',
        'pm.collectionVariables.set("clientToken", j.token);',
        'pm.collectionVariables.set("clientRefreshToken", j.refreshToken);',
      ],
      desc: 'Регистрация нового клиента → 201 + пара токенов. Пароль ≥ 6 символов.',
    }),
    req({
      name: 'Обновить токены (refresh)',
      method: 'POST',
      path: 'api/auth/refresh',
      json: { refreshToken: '{{clientRefreshToken}}' },
      tests: [
        t.status(200),
        'const j = pm.response.json();',
        'pm.test("Новая пара токенов", () => { pm.expect(j.token).to.be.a("string"); pm.expect(j.refreshToken).to.be.a("string"); });',
        '// Ротация: старый refresh инвалидируется, сохраняем новые токены.',
        'pm.collectionVariables.set("clientToken", j.token);',
        'pm.collectionVariables.set("clientRefreshToken", j.refreshToken);',
      ],
      desc: 'Обновление access-токена по refresh-токену. Ротация: старый refresh удаляется, выдаётся новая пара. ' +
        'Требует предварительного входа (clientRefreshToken).',
    }),
    req({
      name: 'Выход (logout)',
      method: 'POST',
      path: 'api/auth/logout',
      json: { refreshToken: '{{clientRefreshToken}}' },
      tests: [t.status(204)],
      desc: 'Инвалидирует refresh-токен в БД → 204 No Content. После этого refresh повторно не сработает.',
    }),
  ]
);

// ============================================================
// Profile
// ============================================================

const profileFolder = folder(
  'Profile',
  'Профиль текущего пользователя. Требует JWT-токен.',
  [
    req({
      name: 'Мой профиль',
      method: 'GET',
      path: 'api/profile/me',
      token: 'clientToken',
      tests: [t.status(200), t.hasProp('email'), t.hasProp('role')],
      desc: 'Данные текущего пользователя: id, email, fullName, address, role, shopName, balance.',
    }),
    req({
      name: 'Обновить профиль',
      method: 'PATCH',
      path: 'api/profile/me',
      token: 'clientToken',
      json: { fullName: 'Иван Обновлённый', address: 'Москва, ул. Обновлённая, 1' },
      tests: [
        t.status(200),
        'pm.test("fullName обновлён", () => pm.expect(pm.response.json().fullName).to.eql("Иван Обновлённый"));',
      ],
      desc: 'Частичное обновление профиля (PATCH-семантика). Поля: fullName, address, shopName — любое подмножество.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 3 — Categories (новое)
// ============================================================

const categoriesFolder = folder(
  'Categories',
  'Категории товаров. GET — публичный (для фильтра каталога). POST/DELETE — только ADMIN.',
  [
    req({
      name: 'Список категорий',
      method: 'GET',
      path: 'api/categories',
      tests: [t.status(200), t.isArray()],
      desc: 'Публичный список всех категорий. Используется фронтом для дропдауна фильтра.',
    }),
    req({
      name: 'Создать категорию',
      method: 'POST',
      path: 'api/categories',
      token: 'adminToken',
      json: { name: 'Тестовая категория' },
      tests: [
        t.statusOneOf([201, 400]),
        'if (pm.response.code === 201) pm.test("Есть id", () => pm.expect(pm.response.json()).to.have.property("id"));',
      ],
      desc: 'Создаёт категорию (ADMIN). Тело: {"name":"..."}. Пустое имя → 400.',
    }),
    req({
      name: 'Удалить категорию',
      method: 'DELETE',
      path: 'api/categories/999',
      token: 'adminToken',
      tests: [t.statusOneOf([204, 404])],
      desc: 'Удаляет категорию по id (ADMIN) → 204. Замените 999 на реальный id.',
    }),
  ]
);

// ============================================================
// Products
// ============================================================

const productsFolder = folder(
  'Products',
  'Каталог товаров. GET-запросы публичные. Пагинация + фильтры. Ответ — Page (поле content).',
  [
    req({
      name: 'Список товаров',
      method: 'GET',
      path: 'api/products',
      query: [
        { key: 'page', value: '0', description: 'Номер страницы (с 0)' },
        { key: 'size', value: '20', description: 'Размер страницы' },
        { key: 'name', value: null, disabled: true, description: 'Фильтр по названию (частичное совпадение)' },
        { key: 'category', value: null, disabled: true, description: 'Фильтр по категории' },
        { key: 'minPrice', value: null, disabled: true, description: 'Минимальная цена' },
        { key: 'maxPrice', value: null, disabled: true, description: 'Максимальная цена' },
        { key: 'sort', value: null, disabled: true, description: 'Сортировка, напр. price,asc' },
      ],
      tests: [t.status(200), t.isPage()],
      desc: 'Страница товаров. Параметры: name, category, minPrice, maxPrice, page, size, sort.',
    }),
    req({
      name: 'Поиск по названию',
      method: 'GET',
      path: 'api/products',
      query: [{ key: 'name', value: 'ноутбук' }],
      tests: [t.status(200), `pm.test("content — массив", () => pm.expect(pm.response.json().content).to.be.an('array'));`],
      desc: 'Поиск по названию (LIKE, без учёта регистра).',
    }),
    req({
      name: 'Фильтр по цене',
      method: 'GET',
      path: 'api/products',
      query: [{ key: 'minPrice', value: '1000' }, { key: 'maxPrice', value: '50000' }],
      desc: 'Фильтрация по диапазону цен.',
    }),
    req({
      name: 'Фильтр по категории',
      method: 'GET',
      path: 'api/products',
      query: [{ key: 'category', value: 'Ноутбуки' }],
      desc: 'Фильтрация по категории.',
    }),
    req({
      name: 'Товар по ID',
      method: 'GET',
      path: 'api/products/1',
      tests: [
        t.status(200), t.hasProp('id'),
        'pm.collectionVariables.set("productId", pm.response.json().id);',
      ],
      desc: 'Один товар по id. Если не найден — 404. Сохраняет productId.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 3 — Search (OpenSearch, новое)
// ============================================================

const searchFolder = folder(
  'Search (OpenSearch)',
  'Полнотекстовый поиск по товарам через OpenSearch. Публичный (permitAll). ' +
  'При недоступности OpenSearch сервис деградирует штатно (возвращает пустую страницу).',
  [
    req({
      name: 'Полнотекстовый поиск',
      method: 'GET',
      path: 'api/search/products',
      query: [
        { key: 'q', value: 'ноутбук' },
        { key: 'page', value: '0' },
        { key: 'size', value: '10' },
      ],
      tests: [t.status(200), t.isPage()],
      desc: 'GET /api/search/products?q=... — поиск по названию/описанию. Ответ — Page<ProductDocument>.',
    }),
    req({
      name: 'Поиск с фильтрами',
      method: 'GET',
      path: 'api/search/products',
      query: [
        { key: 'q', value: 'ноутбук' },
        { key: 'category', value: 'Ноутбуки' },
        { key: 'minPrice', value: '50000' },
        { key: 'maxPrice', value: '200000' },
        { key: 'page', value: '0' },
        { key: 'size', value: '10' },
      ],
      tests: [t.status(200), t.isPage()],
      desc: 'Поиск с фильтрами по категории и цене.',
    }),
  ]
);

// ============================================================
// Reviews
// ============================================================

const reviewsFolder = folder(
  'Reviews',
  'Отзывы на товары. Просмотр — публичный. Добавление — JWT клиента. Один отзыв на товар от пользователя.',
  [
    req({
      name: 'Отзывы о товаре',
      method: 'GET',
      path: 'api/products/1/reviews',
      tests: [t.status(200), t.isArray()],
      desc: 'Список отзывов на товар. Поля: rating (1–5), comment, authorName, createdAt.',
    }),
    req({
      name: 'Добавить отзыв',
      method: 'POST',
      path: 'api/products/1/reviews',
      token: 'clientToken',
      json: { rating: 5, comment: 'Отличный товар, рекомендую!' },
      tests: [
        t.statusOneOf([200, 201, 400]),
        'if ([200,201].includes(pm.response.code)) pm.test("rating 1..5", () => pm.expect(pm.response.json().rating).to.be.within(1,5));',
      ],
      desc: 'Добавляет отзыв. rating: 1–5 (обязательно), comment — опционально. Повторный отзыв на тот же товар → 400.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 2 — Cart (исправленные пути: /items)
// ============================================================

const cartFolder = folder(
  'Cart',
  'Корзина. Требует JWT клиента. REST: POST/PUT/DELETE по /api/cart/items. Оформление заказа — в папке Orders.',
  [
    req({
      name: 'Посмотреть корзину',
      method: 'GET',
      path: 'api/cart',
      token: 'clientToken',
      tests: [t.status(200), t.hasProp('items')],
      desc: 'Текущая корзина: позиции и итоговая сумма.',
    }),
    req({
      name: 'Добавить товар',
      method: 'POST',
      path: 'api/cart/items',
      token: 'clientToken',
      json: { productId: 1, quantity: 2 },
      tests: [
        t.status(200),
        `pm.test("Корзина не пуста", () => pm.expect(pm.response.json().items.length).to.be.greaterThan(0));`,
      ],
      desc: 'POST /api/cart/items — добавить товар. Если уже есть — увеличивает количество.',
    }),
    req({
      name: 'Изменить количество позиции',
      method: 'PUT',
      path: 'api/cart/items/1',
      token: 'clientToken',
      json: { quantity: 5 },
      tests: [t.statusOneOf([200, 404])],
      desc: 'PUT /api/cart/items/{cartItemId} — изменить количество. В URL — cartItemId (не productId).',
    }),
    req({
      name: 'Удалить позицию',
      method: 'DELETE',
      path: 'api/cart/items/1',
      token: 'clientToken',
      tests: [t.statusOneOf([204, 404])],
      desc: 'DELETE /api/cart/items/{cartItemId} → 204 No Content.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 2 — Orders (checkout перенесён сюда: POST /api/orders)
// ============================================================

const ordersFolder = folder(
  'Orders',
  'Заказы. Требует JWT. Оформление заказа = POST /api/orders (201). ' +
  'GET /my возвращает только активные заказы (CREATED или с непогашенной рассрочкой).',
  [
    req({
      name: 'Оформить заказ (checkout)',
      method: 'POST',
      path: 'api/orders',
      token: 'clientToken',
      json: { shippingAddress: 'Москва, ул. Примерная, д. 1, кв. 42' },
      tests: [
        t.status(201),
        'const j = pm.response.json();',
        'pm.test("Статус CREATED", () => pm.expect(j.status).to.eql("CREATED"));',
        'pm.test("Есть invoiceId", () => pm.expect(j).to.have.property("invoiceId"));',
        'pm.collectionVariables.set("orderId", j.id);',
        'pm.collectionVariables.set("invoiceId", j.invoiceId);',
      ],
      desc: 'Превращает корзину в Order + Invoice (201). Корзина очищается. Сохраняет orderId и invoiceId.',
    }),
    req({
      name: 'Мои заказы',
      method: 'GET',
      path: 'api/orders/my',
      token: 'clientToken',
      query: [{ key: 'page', value: '0' }, { key: 'size', value: '20' }],
      tests: [t.status(200), t.isPage()],
      desc: 'Страница активных заказов клиента (PAID/CANCELLED/DELIVERED скрыты).',
    }),
    req({
      name: 'Заказ по ID',
      method: 'GET',
      path: 'api/orders/{{orderId}}',
      token: 'clientToken',
      tests: [t.status(200), t.hasProp('items'), t.hasProp('status')],
      desc: 'Детали заказа с позициями. Поля включают bnplContractId и bnplStatus (для рассрочки).',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 2 — Invoices & Payment (Альфа Банк)
// ============================================================

const invoicesFolder = folder(
  'Invoices & Payment',
  'Счета и инициация оплаты через Альфа Банк. Счёт создаётся при checkout.\n\n' +
  'Оплата теперь идёт через шлюз: POST .../payments или .../bnpl возвращает formUrl, ' +
  'клиента нужно перенаправить на него. Подтверждение приходит на /api/payment/callback.',
  [
    req({
      name: 'Получить счёт',
      method: 'GET',
      path: 'api/invoices/{{invoiceId}}',
      token: 'clientToken',
      tests: [t.status(200), t.hasProp('status')],
      desc: 'GET /api/invoices/{id}. Статусы: UNPAID / PAID / FAILED.',
    }),
    req({
      name: 'Инициировать полную оплату',
      method: 'POST',
      path: 'api/invoices/{{invoiceId}}/payments',
      token: 'clientToken',
      jsonRaw: '{}',
      tests: [
        t.statusOneOf([201, 400, 404]),
        'if (pm.response.code === 201) {',
        '  const j = pm.response.json();',
        '  pm.test("Есть formUrl", () => pm.expect(j.formUrl).to.be.a("string"));',
        '  if (j.alfaOrderId) pm.collectionVariables.set("alfaOrderId", j.alfaOrderId);',
        '}',
      ],
      desc: 'POST /api/invoices/{id}/payments — одностадийная полная оплата. ' +
        'Возвращает {formUrl, alfaOrderId, contractId:null} (201). Клиента перенаправить на formUrl.',
    }),
    req({
      name: 'Инициировать BNPL-рассрочку',
      method: 'POST',
      path: 'api/invoices/{{invoiceId}}/bnpl',
      token: 'clientToken',
      json: { bnplProduct: 'BIWEEKLY_4' },
      tests: [
        t.statusOneOf([201, 400, 404]),
        'if (pm.response.code === 201) {',
        '  const j = pm.response.json();',
        '  pm.test("Есть formUrl", () => pm.expect(j.formUrl).to.be.a("string"));',
        '  if (j.contractId) pm.collectionVariables.set("contractId", j.contractId);',
        '  if (j.alfaOrderId) pm.collectionVariables.set("alfaOrderId", j.alfaOrderId);',
        '}',
      ],
      desc: 'POST /api/invoices/{id}/bnpl — pre-auth первого взноса. ' +
        'bnplProduct: BIWEEKLY_4 (4×, 0%), MONTHLY_4 (4×, 10%), MONTHLY_6 (6×, 15%). ' +
        'Возвращает {formUrl, alfaOrderId, contractId}. Сохраняет contractId.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 4 — Payment Callbacks (Альфа Банк, browser-redirect)
// ============================================================

const paymentCallbacksFolder = folder(
  'Payment Callbacks (Альфа Банк)',
  'Callback-эндпоинты, на которые банк делает browser-redirect после формы оплаты. ' +
  'Публичные (без JWT), отдают HTML. В обычном сценарии вызываются банком автоматически — ' +
  'здесь приведены для ручной отладки (подставьте orderId из шлюза в {{alfaOrderId}}).',
  [
    req({
      name: 'Callback оплаты',
      method: 'GET',
      path: 'api/payment/callback',
      query: [{ key: 'orderId', value: '{{alfaOrderId}}' }],
      tests: [t.statusOneOf([200, 400])],
      desc: 'GET /api/payment/callback?orderId=... — проверяет статус заказа в шлюзе, ' +
        'при успехе подтверждает оплату/контракт и привязывает карту. Возвращает HTML.',
    }),
    req({
      name: 'Callback привязки карты',
      method: 'GET',
      path: 'api/payment/card-bind-callback',
      query: [{ key: 'orderId', value: '{{alfaOrderId}}' }],
      tests: [t.statusOneOf([200, 400])],
      desc: 'GET /api/payment/card-bind-callback?orderId=... — завершает привязку карты ' +
        '(deposit → bindingId → refund 1₽). Возвращает HTML.',
    }),
    req({
      name: 'Fail-редирект',
      method: 'GET',
      path: 'api/payment/fail',
      query: [{ key: 'orderId', value: '{{alfaOrderId}}' }],
      tests: [t.status(200)],
      desc: 'GET /api/payment/fail — страница неуспешной оплаты (HTML).',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 4 — BNPL (рассрочка)
// ============================================================

const bnplFolder = folder(
  'BNPL (рассрочка)',
  'Управление BNPL-рассрочкой. Требует JWT клиента. Контракт создаётся через Invoices/BNPL.\n\n' +
  'Продукты: BIWEEKLY_4 (4×, 0%, 14 дн), MONTHLY_4 (4×, 10%, 30 дн), MONTHLY_6 (6×, 15%, 30 дн).',
  [
    req({
      name: 'Мои контракты',
      method: 'GET',
      path: 'api/bnpl/my',
      token: 'clientToken',
      tests: [
        t.status(200), t.isArray(),
        'const a = pm.response.json(); if (a.length) pm.collectionVariables.set("contractId", a[0].id);',
      ],
      desc: 'Список BNPL-контрактов пользователя. Сохраняет contractId первого контракта.',
    }),
    req({
      name: 'Контракт по ID',
      method: 'GET',
      path: 'api/bnpl/{{contractId}}',
      token: 'clientToken',
      tests: [t.statusOneOf([200, 404]), 'if (pm.response.code===200) pm.expect(pm.response.json()).to.have.property("installments");'],
      desc: 'Детали контракта: график взносов (installments) и журнал платежей (payments).',
    }),
    req({
      name: 'Перенести взнос',
      method: 'POST',
      path: 'api/bnpl/{{contractId}}/postpone',
      token: 'clientToken',
      json: { days: 3 },
      tests: [t.statusOneOf([200, 400, 404])],
      desc: 'POST /api/bnpl/{id}/postpone — перенос ближайшего PENDING-взноса. ' +
        'days: 3–14 за раз, суммарно ≤ 14 дней. Комиссия 0,05%/день.',
    }),
    req({
      name: 'Досрочная оплата (с карты)',
      method: 'POST',
      path: 'api/bnpl/{{contractId}}/pay',
      token: 'clientToken',
      jsonRaw: '{}',
      tests: [t.statusOneOf([201, 400, 404])],
      desc: 'POST /api/bnpl/{id}/pay — тихое списание по привязанной карте. ' +
        'Тело опционально: {"amountKopecks": N} — произвольная сумма; пустое {} → ближайший взнос. (201)',
    }),
    req({
      name: 'Статус позиции заказа (клиент)',
      method: 'PATCH',
      path: 'api/orders/{{orderId}}/items/{{orderItemId}}',
      token: 'clientToken',
      json: { status: 'ISSUED' },
      tests: [t.statusOneOf([200, 400, 404])],
      desc: 'PATCH /api/orders/{id}/items/{itemId} — статус позиции BNPL-заказа. ' +
        'status: ISSUED (выдача → deposit), CANCELLED (reverse), RETURNED (refund). Подставьте orderItemId.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 3 — Cards (привязанные карты)
// ============================================================

const cardsFolder = folder(
  'Cards (привязанные карты)',
  'Привязанные карты пользователя. Требует JWT. Привязка идёт через шлюз Альфа Банка ' +
  '(pre-auth 1₽ → deposit → bindingId → refund).',
  [
    req({
      name: 'Мои карты',
      method: 'GET',
      path: 'api/cards',
      token: 'clientToken',
      tests: [
        t.status(200), t.isArray(),
        'const a = pm.response.json(); if (a.length) pm.collectionVariables.set("cardId", a[0].id);',
      ],
      desc: 'Список карт: id, maskedPan, expiry (MM/YYYY), isDefault. Сохраняет cardId первой карты.',
    }),
    req({
      name: 'Привязать карту',
      method: 'POST',
      path: 'api/cards/bind',
      token: 'clientToken',
      tests: [
        t.statusOneOf([201, 400]),
        'if (pm.response.code===201) { const j=pm.response.json(); pm.test("Есть formUrl", ()=>pm.expect(j.formUrl).to.be.a("string")); if (j.alfaOrderId) pm.collectionVariables.set("alfaOrderId", j.alfaOrderId); }',
      ],
      desc: 'POST /api/cards/bind — начинает привязку. Регистрирует платёж 1₽, возвращает formUrl. ' +
        'После формы банк редиректит на /api/payment/card-bind-callback, где карта сохраняется. Тело не нужно.',
    }),
    req({
      name: 'Сделать дефолтной',
      method: 'PATCH',
      path: 'api/cards/{{cardId}}/default',
      token: 'clientToken',
      tests: [t.statusOneOf([200, 404])],
      desc: 'PATCH /api/cards/{id}/default — назначить карту дефолтной (для тихих списаний).',
    }),
    req({
      name: 'Удалить карту',
      method: 'DELETE',
      path: 'api/cards/{{cardId}}',
      token: 'clientToken',
      tests: [t.statusOneOf([204, 404])],
      desc: 'DELETE /api/cards/{id} → 204 No Content.',
    }),
  ]
);

// ============================================================
// Seller
// ============================================================

const sellerFolder = folder(
  'Seller',
  'Кабинет продавца. Требует роль SELLER (sellerToken).',
  [
    req({
      name: 'Мои товары',
      method: 'GET',
      path: 'api/seller/products',
      token: 'sellerToken',
      query: [{ key: 'page', value: '0' }, { key: 'size', value: '20' }],
      tests: [t.status(200), t.isPage()],
      desc: 'Страница товаров текущего продавца.',
    }),
    req({
      name: 'Создать товар',
      method: 'POST',
      path: 'api/seller/products',
      token: 'sellerToken',
      json: {
        name: 'Беспроводные наушники Pro',
        description: 'Наушники с ANC и 30 часами работы',
        price: 8990.0,
        stockQuantity: 20,
        category: 'Аудиотехника',
      },
      tests: [t.status(201), t.hasProp('id')],
      desc: 'Создаёт товар от имени продавца (201). category — опционально.',
    }),
    req({
      name: 'Обновить товар',
      method: 'PUT',
      path: 'api/seller/products/1',
      token: 'sellerToken',
      json: { name: 'Обновлённый товар', price: 9990.0, stockQuantity: 15, category: 'Аудиотехника' },
      tests: [t.statusOneOf([200, 400, 404])],
      desc: 'Полная замена товара продавца. Чужой товар → 400.',
    }),
    req({
      name: 'Удалить товар',
      method: 'DELETE',
      path: 'api/seller/products/1',
      token: 'sellerToken',
      tests: [t.statusOneOf([204, 400, 404])],
      desc: 'Удаляет товар продавца (204). Чужой товар — нельзя.',
    }),
    req({
      name: 'Загрузить фото товара',
      method: 'POST',
      path: 'api/seller/products/1/image',
      token: 'sellerToken',
      formdata: [{ key: 'file', type: 'file', src: [], description: 'Изображение (JPEG/PNG). Макс. 2 МБ.' }],
      tests: [t.statusOneOf([200, 400, 413]), 'if (pm.response.code===200) pm.expect(pm.response.json()).to.have.property("imageData");'],
      desc: 'multipart/form-data, поле file. Хранится как Base64 в PostgreSQL. Только image/*, ≤ 2 МБ (иначе 413).',
    }),
    req({
      name: 'Удалить фото товара',
      method: 'DELETE',
      path: 'api/seller/products/1/image',
      token: 'sellerToken',
      tests: [t.statusOneOf([204, 404])],
      desc: 'Обнуляет imageData/imageContentType. 204.',
    }),
    req({
      name: 'Баланс',
      method: 'GET',
      path: 'api/seller/balance',
      token: 'sellerToken',
      tests: [t.status(200), t.hasProp('balance')],
      desc: 'Профиль продавца с балансом и названием магазина.',
    }),
    req({
      name: 'Мои продажи',
      method: 'GET',
      path: 'api/seller/sales',
      token: 'sellerToken',
      tests: [t.status(200), t.isArray()],
      desc: 'Заказы, содержащие товары этого продавца.',
    }),
  ]
);

// ============================================================
// Admin — Products
// ============================================================

const adminProductsFolder = folder(
  'Admin — Products',
  'Управление товарами. Требует роль ADMIN (adminToken).',
  [
    req({
      name: 'Список продавцов',
      method: 'GET',
      path: 'api/admin/sellers',
      token: 'adminToken',
      tests: [t.status(200), t.isArray()],
      desc: 'Все пользователи с ролью SELLER — для выбора в форме создания товара.',
    }),
    req({
      name: 'Создать товар',
      method: 'POST',
      path: 'api/admin/products',
      token: 'adminToken',
      json: {
        name: 'Планшет Samsung Galaxy Tab',
        description: '10-дюймовый планшет с AMOLED экраном',
        price: 29999.99,
        stockQuantity: 15,
        category: 'Планшеты',
        sellerId: 3,
      },
      tests: [t.statusOneOf([201, 400]), 'if (pm.response.code===201) pm.expect(pm.response.json()).to.have.property("id");'],
      desc: 'Создаёт товар (201). sellerId обязателен при создании администратором.',
    }),
    req({
      name: 'Обновить товар',
      method: 'PUT',
      path: 'api/admin/products/1',
      token: 'adminToken',
      json: {
        name: 'Ноутбук Pro (обновлённый)',
        description: 'Мощный ноутбук для разработчиков',
        price: 89999.99,
        stockQuantity: 8,
        category: 'Ноутбуки',
        sellerId: 3,
      },
      tests: [t.statusOneOf([200, 400, 404])],
      desc: 'Полная замена товара по id.',
    }),
    req({
      name: 'Удалить товар',
      method: 'DELETE',
      path: 'api/admin/products/999',
      token: 'adminToken',
      tests: [t.statusOneOf([204, 404])],
      desc: 'Удаляет товар по id (204). Подставьте реальный id.',
    }),
    req({
      name: 'Загрузить фото товара',
      method: 'POST',
      path: 'api/admin/products/1/image',
      token: 'adminToken',
      formdata: [{ key: 'file', type: 'file', src: [], description: 'Изображение (JPEG/PNG). Макс. 2 МБ.' }],
      tests: [t.statusOneOf([200, 400, 413])],
      desc: 'Загрузка фото для любого товара (без ограничения по владельцу). multipart/form-data.',
    }),
    req({
      name: 'Удалить фото товара',
      method: 'DELETE',
      path: 'api/admin/products/1/image',
      token: 'adminToken',
      tests: [t.statusOneOf([204, 404])],
      desc: 'Обнуляет imageData/imageContentType любого товара. 204.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 4 — Admin — Orders (PATCH-статус + новые: order-by-id, bnpl, pay, item-status)
// ============================================================

const adminOrdersFolder = folder(
  'Admin — Orders',
  'Управление заказами. Требует роль ADMIN. Список пагинирован (поле content).',
  [
    req({
      name: 'Все заказы',
      method: 'GET',
      path: 'api/admin/orders',
      token: 'adminToken',
      query: [{ key: 'page', value: '0' }, { key: 'size', value: '20' }],
      tests: [t.status(200), t.isPage()],
      desc: 'Страница всех заказов. Параметры page, size, sort.',
    }),
    req({
      name: 'Заказ по ID',
      method: 'GET',
      path: 'api/admin/orders/{{orderId}}',
      token: 'adminToken',
      tests: [t.statusOneOf([200, 404])],
      desc: 'GET /api/admin/orders/{id} — детали любого заказа (с данными клиента и BNPL).',
    }),
    req({
      name: 'Изменить статус заказа',
      method: 'PATCH',
      path: 'api/admin/orders/{{orderId}}',
      token: 'adminToken',
      json: { status: 'DELIVERED' },
      tests: [
        t.statusOneOf([200, 400, 404]),
        'if (pm.response.code===200) pm.test("Статус обновлён", () => pm.expect(pm.response.json().status).to.eql("DELIVERED"));',
      ],
      desc: 'PATCH /api/admin/orders/{id} — смена статуса. Значения: CREATED, PAID, CANCELLED, DELIVERED.',
    }),
    req({
      name: 'BNPL-контракт по ID',
      method: 'GET',
      path: 'api/admin/bnpl/{{contractId}}',
      token: 'adminToken',
      tests: [t.statusOneOf([200, 404])],
      desc: 'GET /api/admin/bnpl/{contractId} — контракт рассрочки глазами администратора.',
    }),
    req({
      name: 'Оплатить заказ с карты клиента',
      method: 'POST',
      path: 'api/admin/orders/{{orderId}}/pay',
      token: 'adminToken',
      jsonRaw: '{}',
      tests: [t.statusOneOf([200, 400, 404])],
      desc: 'POST /api/admin/orders/{id}/pay — тихое списание с дефолтной карты клиента. ' +
        'Тело опционально: {"amountKopecks": N}. BNPL: null → ближайший взнос, N → сумма; обычный заказ → полная сумма.',
    }),
    req({
      name: 'Статус позиции заказа (admin)',
      method: 'PATCH',
      path: 'api/admin/orders/{{orderId}}/items/{{orderItemId}}',
      token: 'adminToken',
      json: { status: 'ISSUED' },
      tests: [t.statusOneOf([200, 400, 404])],
      desc: 'PATCH /api/admin/orders/{id}/items/{itemId} — статус позиции (ISSUED/CANCELLED/RETURNED) от админа.',
    }),
  ]
);

// ============================================================
// Admin — Invoices
// ============================================================

const adminInvoicesFolder = folder(
  'Admin — Invoices',
  'Просмотр всех счетов. Требует роль ADMIN. Ответ пагинирован.',
  [
    req({
      name: 'Все счета',
      method: 'GET',
      path: 'api/admin/invoices',
      token: 'adminToken',
      query: [{ key: 'page', value: '0' }, { key: 'size', value: '20' }],
      tests: [t.status(200), t.isPage()],
      desc: 'Страница всех счетов. Параметры page, size, sort.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 2 — Chat (клиент↔продавец, + polling ?after)
// ============================================================

const chatFolder = folder(
  'Chat (клиент ↔ продавец)',
  'Чат между клиентом и продавцом. Требует JWT (CLIENT или SELLER).',
  [
    req({
      name: 'Список диалогов',
      method: 'GET',
      path: 'api/chat/conversations',
      token: 'clientToken',
      tests: [t.status(200), t.isArray()],
      desc: 'Клиент видит свои диалоги; продавец — диалоги с покупателями. Новые вверху.',
    }),
    req({
      name: 'Начать диалог с продавцом',
      method: 'POST',
      path: 'api/chat/conversations',
      token: 'clientToken',
      json: { sellerId: 3, message: 'Здравствуйте! Интересует ноутбук Dell.' },
      tests: [
        t.statusOneOf([200, 201]),
        'pm.test("Есть id", () => pm.expect(pm.response.json().id).to.be.a("number"));',
        'pm.collectionVariables.set("conversationId", pm.response.json().id);',
      ],
      desc: 'Создаёт/переиспользует диалог с продавцом (sellerId=3 — TechShop). 201. Сохраняет conversationId.',
    }),
    req({
      name: 'Сообщения диалога',
      method: 'GET',
      path: 'api/chat/conversations/{{conversationId}}/messages',
      token: 'clientToken',
      tests: [t.status(200), t.isArray()],
      desc: 'История сообщений. Без ?after — все сообщения + пометка входящих прочитанными.',
    }),
    req({
      name: 'Новые сообщения (polling)',
      method: 'GET',
      path: 'api/chat/conversations/{{conversationId}}/messages',
      token: 'clientToken',
      query: [{ key: 'after', value: '0', description: 'Вернуть только сообщения с id > after (без пометки прочитанными)' }],
      tests: [t.status(200), t.isArray()],
      desc: 'GET ...?after={id} — только новые сообщения (для polling, без пометки прочитанными).',
    }),
    req({
      name: 'Отправить сообщение',
      method: 'POST',
      path: 'api/chat/conversations/{{conversationId}}/messages',
      token: 'clientToken',
      json: { content: 'Есть ли в наличии XPS 15 с 32 ГБ RAM?' },
      tests: [t.status(201), `pm.test("Есть content", () => pm.expect(pm.response.json().content).to.be.a('string'));`],
      desc: 'Отправляет сообщение (201). Доступно обоим участникам диалога.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 4 — Support Chat (поддержка, новое)
// ============================================================

const supportFolder = folder(
  'Support Chat (поддержка)',
  'Чат с поддержкой. Клиент создаёт диалог, администратор видит все диалоги и отвечает.',
  [
    req({
      name: 'Начать диалог с поддержкой (клиент)',
      method: 'POST',
      path: 'api/support/conversations',
      token: 'clientToken',
      tests: [
        t.statusOneOf([200, 201]),
        'pm.test("Есть id", () => pm.expect(pm.response.json().id).to.be.a("number"));',
        'pm.collectionVariables.set("supportConversationId", pm.response.json().id);',
      ],
      desc: 'POST /api/support/conversations (только CLIENT) — создаёт/возвращает диалог с поддержкой (201). ' +
        'Тело не требуется. Сохраняет supportConversationId.',
    }),
    req({
      name: 'Все диалоги поддержки (admin)',
      method: 'GET',
      path: 'api/support/conversations',
      token: 'adminToken',
      tests: [t.status(200), t.isArray()],
      desc: 'GET /api/support/conversations (только ADMIN) — все обращения в поддержку.',
    }),
    req({
      name: 'Сообщения диалога',
      method: 'GET',
      path: 'api/support/conversations/{{supportConversationId}}/messages',
      token: 'clientToken',
      query: [{ key: 'after', value: null, disabled: true, description: 'Только сообщения с id > after (polling)' }],
      tests: [t.status(200), t.isArray()],
      desc: 'История сообщений диалога поддержки. Параметр after — для polling.',
    }),
    req({
      name: 'Отправить сообщение',
      method: 'POST',
      path: 'api/support/conversations/{{supportConversationId}}/messages',
      token: 'clientToken',
      json: { content: 'Здравствуйте! Не приходит чек на почту.' },
      tests: [t.status(201), `pm.test("Есть content", () => pm.expect(pm.response.json().content).to.be.a('string'));`],
      desc: 'Отправляет сообщение в диалог поддержки (201). Доступно клиенту и администратору.',
    }),
  ]
);

// ============================================================
// ЧАСТЬ 2 — Admin — Email (исправлено: /api/admin/emails)
// ============================================================

const adminEmailFolder = folder(
  'Admin — Email',
  'Отправка произвольного письма администратором. Требует роль ADMIN.',
  [
    req({
      name: 'Отправить письмо',
      method: 'POST',
      path: 'api/admin/emails',
      token: 'adminToken',
      json: {
        to: 'client@example.com',
        subject: 'Важное сообщение от маркетплейса',
        text: 'Уважаемый клиент! Спасибо за использование нашего сервиса.',
      },
      tests: [t.statusOneOf([200, 201, 400, 500])],
      desc: 'POST /api/admin/emails — письмо через Яндекс SMTP. Валидация: to (@Email), subject и text (@NotBlank). ' +
        'При ошибке SMTP — 500.',
    }),
  ]
);

// ============================================================
// Accountant
// ============================================================

const accountantFolder = folder(
  'Accountant',
  'Бухгалтерские отчёты. Все эндпоинты требуют роль ACCOUNTANT.',
  [
    req({
      name: 'Сводный дашборд',
      method: 'GET',
      path: 'api/accountant/summary',
      token: 'accountantToken',
      tests: [
        t.status(200),
        'pm.test("8 KPI-полей", () => pm.expect(pm.response.json()).to.have.all.keys("totalOrders","paidOrders","totalRevenue","totalClients","cartItemsCount","potentialRevenue","emailsSent","emailsSuccess"));',
      ],
      desc: '8 KPI: заказы, выручка, клиенты, корзины, письма.',
    }),
    req({
      name: 'Отчёт по заказам',
      method: 'GET',
      path: 'api/accountant/orders',
      token: 'accountantToken',
      tests: [t.status(200), t.isArray()],
      desc: 'Все заказы с деталями покупателей. Новые вверху.',
    }),
    req({
      name: 'Отчёт по корзинам',
      method: 'GET',
      path: 'api/accountant/carts',
      token: 'accountantToken',
      tests: [t.status(200), t.isArray()],
      desc: 'Содержимое всех корзин: товар, покупатель, количество, цена, сумма позиции.',
    }),
    req({
      name: 'Отчёт по клиентам',
      method: 'GET',
      path: 'api/accountant/customers',
      token: 'accountantToken',
      tests: [t.status(200), t.isArray()],
      desc: 'Покупатели с количеством заказов и суммой потраченного (только PAID).',
    }),
    req({
      name: 'История писем',
      method: 'GET',
      path: 'api/accountant/emails',
      token: 'accountantToken',
      tests: [t.status(200), t.isArray()],
      desc: 'История всех попыток отправки писем (успешные и неуспешные). Свежие вверху.',
    }),
    req({
      name: 'Доступ запрещён (для клиента)',
      method: 'GET',
      path: 'api/accountant/summary',
      token: 'clientToken',
      tests: [t.status(403)],
      desc: 'Проверка защиты: CLIENT не имеет доступа к /api/accountant/** → 403.',
    }),
  ]
);

// ============================================================
// Сборка коллекции
// ============================================================

const collection = {
  info: {
    name: 'Pet_Ozon Marketplace',
    description:
      'REST API маркетплейса с JWT-аутентификацией, оплатой через Альфа Банк, BNPL-рассрочкой, ' +
      'привязкой карт и полнотекстовым поиском (OpenSearch).\n\n' +
      '**Базовый URL:** http://localhost:8667 (переменная baseUrl).\n\n' +
      '**Переменные коллекции (часть заполняется автоматически тест-скриптами):**\n' +
      '- `clientToken` / `clientRefreshToken` — при Auth/Вход (клиент) или Регистрации\n' +
      '- `adminToken`, `sellerToken`, `accountantToken` — при соответствующем входе\n' +
      '- `orderId`, `invoiceId` — при Orders/Оформить заказ\n' +
      '- `contractId` — при Invoices/BNPL или BNPL/Мои контракты\n' +
      '- `cardId` — при Cards/Мои карты\n' +
      '- `alfaOrderId` — при инициации оплаты/привязки (для callback-эндпоинтов)\n' +
      '- `conversationId` / `supportConversationId` — при старте чата\n' +
      '- `orderItemId` — подставьте вручную (id позиции заказа для BNPL-операций)\n\n' +
      '**Токены:** login возвращает пару token (access, 15 мин) + refreshToken (7 дней). ' +
      'При 401 обновляйте access через Auth/Обновить токены (refresh).\n\n' +
      '**Сценарий клиента:** Auth/Вход → Products → Cart/Добавить → Orders/Оформить → ' +
      'Invoices/Оплата (formUrl → форма банка → callback).\n\n' +
      '**Сценарий BNPL:** Orders/Оформить → Invoices/BNPL (formUrl) → callback → ' +
      'BNPL/Контракт (график) → перенос/досрочная оплата.\n\n' +
      '**Важно про оплату:** эндпоинты .../payments, .../bnpl, /cards/bind возвращают formUrl — ' +
      'это страница шлюза Альфа Банка. Реальная оплата происходит в браузере; подтверждение приходит ' +
      'на callback. Тесты проверяют только корректную инициацию (наличие formUrl).',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  variable: [
    { key: 'baseUrl', value: 'http://localhost:8667', type: 'string' },
    { key: 'clientToken', value: '', type: 'string', description: 'JWT access клиента (Auth/Вход клиент).' },
    { key: 'clientRefreshToken', value: '', type: 'string', description: 'Refresh-токен клиента (для /api/auth/refresh).' },
    { key: 'adminToken', value: '', type: 'string', description: 'JWT администратора.' },
    { key: 'sellerToken', value: '', type: 'string', description: 'JWT продавца.' },
    { key: 'accountantToken', value: '', type: 'string', description: 'JWT бухгалтера.' },
    { key: 'orderId', value: '', type: 'string', description: 'ID заказа (Orders/Оформить).' },
    { key: 'invoiceId', value: '', type: 'string', description: 'ID счёта (Orders/Оформить).' },
    { key: 'contractId', value: '', type: 'string', description: 'ID BNPL-контракта.' },
    { key: 'cardId', value: '', type: 'string', description: 'ID привязанной карты (Cards/Мои карты).' },
    { key: 'alfaOrderId', value: '', type: 'string', description: 'orderId в шлюзе Альфа Банка (для callback).' },
    { key: 'conversationId', value: '', type: 'string', description: 'ID диалога клиент↔продавец.' },
    { key: 'supportConversationId', value: '', type: 'string', description: 'ID диалога с поддержкой.' },
    { key: 'productId', value: '', type: 'string', description: 'ID товара (Products/Товар по ID).' },
    { key: 'orderItemId', value: '', type: 'string', description: 'ID позиции заказа (подставить вручную).' },
  ],
  item: [
    authFolder,
    profileFolder,
    categoriesFolder,
    productsFolder,
    searchFolder,
    reviewsFolder,
    cartFolder,
    ordersFolder,
    invoicesFolder,
    paymentCallbacksFolder,
    bnplFolder,
    cardsFolder,
    sellerFolder,
    adminProductsFolder,
    adminOrdersFolder,
    adminInvoicesFolder,
    chatFolder,
    supportFolder,
    adminEmailFolder,
    accountantFolder,
  ],
};

const outPath = path.join(__dirname, 'Pet_Ozon_Marketplace.postman_collection.json');
fs.writeFileSync(outPath, JSON.stringify(collection, null, 2) + '\n', 'utf8');

// Краткая сводка покрытия для контроля.
let folders = collection.item.length;
let requests = collection.item.reduce((n, f) => n + f.item.length, 0);
console.log(`OK: ${outPath}`);
console.log(`Папок: ${folders}, запросов: ${requests}`);
collection.item.forEach(f => console.log(`  - ${f.name}: ${f.item.length}`));
