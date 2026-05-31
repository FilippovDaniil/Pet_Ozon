// api.js — обёртка над fetch для всех эндпоинтов бэкенда
// API_BASE определён в auth.js, который загружается раньше

function buildHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    return headers;
}

async function apiFetch(path, { method = 'GET', body } = {}, _isRetry = false) {
    const res = await fetch(API_BASE + path, {
        method,
        headers: buildHeaders(),
        body,
    });

    // При 401 пробуем обновить access-токен через refresh (один раз).
    // _isRetry предотвращает бесконечный цикл, если refresh тоже вернёт 401.
    if (res.status === 401 && !_isRetry) {
        const refreshed = await tryRefreshToken();
        if (refreshed) {
            // Повторяем оригинальный запрос с новым токеном.
            return apiFetch(path, { method, body }, true);
        }
        logout();
        return null;
    }

    if (res.status === 403) {
        logout();
        return null;
    }

    if (res.status === 204) return null;
    const text = await res.text();
    if (!text || !text.trim()) return null;
    let json;
    try { json = JSON.parse(text); } catch { return null; }
    if (!res.ok) throw new Error(json?.message || `Ошибка сервера: HTTP ${res.status}`);
    return json;
}

const api = {
    // ── Категории ─────────────────────────────────────────────────────────
    getCategories: () => apiFetch('/api/categories'),
    createCategory: (name) =>
        apiFetch('/api/categories', { method: 'POST', body: JSON.stringify({ name }) }),
    deleteCategory: (id) =>
        apiFetch(`/api/categories/${id}`, { method: 'DELETE' }),

    // ── Товары ────────────────────────────────────────────────────────────
    getProducts: (params = {}) => {
        const qs = new URLSearchParams();
        if (params.name)     qs.set('name', params.name);
        if (params.category) qs.set('category', params.category);
        if (params.minPrice !== undefined && params.minPrice !== '') qs.set('minPrice', params.minPrice);
        if (params.maxPrice !== undefined && params.maxPrice !== '') qs.set('maxPrice', params.maxPrice);
        qs.set('page', params.page ?? 0);
        qs.set('size', params.size ?? 20);
        return apiFetch('/api/products?' + qs.toString());
    },

    // ── Корзина ───────────────────────────────────────────────────────────
    getCart: () =>
        apiFetch('/api/cart'),
    addToCart: (productId, quantity) =>
        apiFetch('/api/cart/items', { method: 'POST', body: JSON.stringify({ productId, quantity }) }),
    removeFromCart: (cartItemId) =>
        apiFetch(`/api/cart/items/${cartItemId}`, { method: 'DELETE' }),
    updateCartItem: (cartItemId, quantity) =>
        apiFetch(`/api/cart/items/${cartItemId}`, { method: 'PUT', body: JSON.stringify({ quantity }) }),
    // payload: { deliveryType: 'DELIVERY'|'PICKUP', shippingAddress?, pickupPointId? }
    checkout: (payload) =>
        apiFetch('/api/orders', { method: 'POST', body: JSON.stringify(payload) }),

    // ── Точки самовывоза ───────────────────────────────────────────────────
    getPickupPoints: () =>
        apiFetch('/api/pickup-points'),

    // ── Заказы (клиент) ───────────────────────────────────────────────────
    getMyOrders: async () => {
        const page = await apiFetch('/api/orders/my');
        return page ? page.content : [];
    },

    // ── Счета ─────────────────────────────────────────────────────────────
    getInvoice: (id) =>
        apiFetch(`/api/invoices/${id}`),

    // Одностадийная полная оплата → возвращает { formUrl }
    initiateFullPayment: (invoiceId) =>
        apiFetch(`/api/invoices/${invoiceId}/payments`, { method: 'POST', body: '{}' }),

    // BNPL-рассрочка → возвращает { formUrl, contractId }
    initiateBnpl: (invoiceId, bnplProduct) =>
        apiFetch(`/api/invoices/${invoiceId}/bnpl`, { method: 'POST', body: JSON.stringify({ bnplProduct }) }),

    // ── BNPL ──────────────────────────────────────────────────────────────
    getBnplContracts: () =>
        apiFetch('/api/bnpl/my'),
    getBnplContract: (contractId) =>
        apiFetch(`/api/bnpl/${contractId}`),

    // Изменить статус позиции BNPL-заказа (ISSUED / CANCELLED / RETURNED)
    updateItemStatus: (orderId, itemId, status) =>
        apiFetch(`/api/orders/${orderId}/items/${itemId}`, { method: 'PATCH', body: JSON.stringify({ status }) }),

    // Перенести ближайший взнос (days: 3-14)
    postponeInstallment: (contractId, days) =>
        apiFetch(`/api/bnpl/${contractId}/postpone`, { method: 'POST', body: JSON.stringify({ days }) }),

    // Оплатить взнос(ы) по привязанной карте — тихое списание (MIT), без CVC/3DS.
    payInstallmentNow: (contractId, amountKopecks) =>
        apiFetch(`/api/bnpl/${contractId}/pay`, {
            method: 'POST',
            body: amountKopecks ? JSON.stringify({ amountKopecks }) : '{}'
        }),

    // ── Привязанные карты ─────────────────────────────────────────────────────
    getCards: () =>
        apiFetch('/api/cards'),
    bindCard: () =>
        apiFetch('/api/cards/bind', { method: 'POST', body: '{}' }),
    setDefaultCard: (cardId) =>
        apiFetch(`/api/cards/${cardId}/default`, { method: 'PATCH', body: '{}' }),
    deleteCard: (cardId) =>
        apiFetch(`/api/cards/${cardId}`, { method: 'DELETE' }),

    // ── Продавец ──────────────────────────────────────────────────────────
    getSellerProducts: async () => {
        const page = await apiFetch('/api/seller/products?size=100');
        return page ? page.content : [];
    },
    createSellerProduct: (data) =>
        apiFetch('/api/seller/products', { method: 'POST', body: JSON.stringify(data) }),
    updateSellerProduct: (id, data) =>
        apiFetch(`/api/seller/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteSellerProduct: (id) =>
        apiFetch(`/api/seller/products/${id}`, { method: 'DELETE' }),
    getSellerBalance: () =>
        apiFetch('/api/seller/balance'),
    getSellerSales: () =>
        apiFetch('/api/seller/sales'),

    // Загрузка изображения товара (multipart/form-data).
    // Нельзя использовать apiFetch — он принудительно ставит Content-Type: application/json.
    // При использовании FormData браузер сам выставляет Content-Type: multipart/form-data
    // с уникальным boundary (разделитель частей формы), поэтому заголовок НЕ нужно указывать.
    uploadProductImage: async (productId, file) => {
        const doUpload = async () => {
            const formData = new FormData();
            formData.append('file', file);
            const headers = {};
            const token = getToken();
            if (token) headers['Authorization'] = 'Bearer ' + token;
            return fetch(API_BASE + `/api/seller/products/${productId}/image`, {
                method: 'POST', headers, body: formData,
            });
        };
        let res = await doUpload();
        if (res.status === 401) {
            const refreshed = await tryRefreshToken();
            if (refreshed) res = await doUpload();
            else { logout(); return null; }
        }
        if (res.status === 403) { logout(); return null; }
        const text = await res.text();
        if (!text || !text.trim()) return null;
        let json;
        try { json = JSON.parse(text); } catch { return null; }
        if (!res.ok) throw new Error(json?.message || `Ошибка загрузки фото: HTTP ${res.status}`);
        return json;
    },
    deleteProductImage: (productId) =>
        apiFetch(`/api/seller/products/${productId}/image`, { method: 'DELETE' }),

    // ── Админ: точки самовывоза ───────────────────────────────────────────
    getAdminPickupPoints: () =>
        apiFetch('/api/admin/pickup-points'),
    createPickupPoint: (data) =>
        apiFetch('/api/admin/pickup-points', { method: 'POST', body: JSON.stringify(data) }),
    updatePickupPoint: (id, data) =>
        apiFetch(`/api/admin/pickup-points/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deletePickupPoint: (id) =>
        apiFetch(`/api/admin/pickup-points/${id}`, { method: 'DELETE' }),

    // ── Админ: товары ─────────────────────────────────────────────────────
    createProduct: (data) =>
        apiFetch('/api/admin/products', { method: 'POST', body: JSON.stringify(data) }),
    updateProduct: (id, data) =>
        apiFetch(`/api/admin/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteProduct: (id) =>
        apiFetch(`/api/admin/products/${id}`, { method: 'DELETE' }),

    // Загрузка/удаление изображения товара администратором.
    // Аналогично uploadProductImage у продавца — FormData без явного Content-Type.
    uploadAdminProductImage: async (productId, file) => {
        const doUpload = async () => {
            const formData = new FormData();
            formData.append('file', file);
            const headers = {};
            const token = getToken();
            if (token) headers['Authorization'] = 'Bearer ' + token;
            return fetch(API_BASE + `/api/admin/products/${productId}/image`, {
                method: 'POST', headers, body: formData,
            });
        };
        let res = await doUpload();
        if (res.status === 401) {
            const refreshed = await tryRefreshToken();
            if (refreshed) res = await doUpload();
            else { logout(); return null; }
        }
        if (res.status === 403) { logout(); return null; }
        const text = await res.text();
        if (!text || !text.trim()) return null;
        let json;
        try { json = JSON.parse(text); } catch { return null; }
        if (!res.ok) throw new Error(json?.message || `Ошибка загрузки фото: HTTP ${res.status}`);
        return json;
    },
    deleteAdminProductImage: (productId) =>
        apiFetch(`/api/admin/products/${productId}/image`, { method: 'DELETE' }),

    // ── Админ: заказы ─────────────────────────────────────────────────────
    getAllOrders: async () => {
        const page = await apiFetch('/api/admin/orders');
        return page ? page.content : [];
    },
    updateOrderStatus: (id, status) =>
        apiFetch(`/api/admin/orders/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),

    // ── Отзывы ────────────────────────────────────────────────────────────
    getProductReviews: (productId) =>
        apiFetch(`/api/products/${productId}/reviews`),
    addReview: (productId, rating, comment) =>
        apiFetch(`/api/products/${productId}/reviews`, {
            method: 'POST',
            body: JSON.stringify({ rating, comment: comment || null }),
        }),

    // ── Админ: заказы ─────────────────────────────────────────────────────
    getAdminOrder: (id) =>
        apiFetch(`/api/admin/orders/${id}`),

    getAdminBnplContract: (contractId) =>
        apiFetch(`/api/admin/bnpl/${contractId}`),

    adminUpdateItemStatus: (orderId, itemId, status) =>
        apiFetch(`/api/admin/orders/${orderId}/items/${itemId}`, {
            method: 'PATCH',
            body: JSON.stringify({ status }),
        }),

    // Оплата заказа с дефолтной карты клиента (тихое списание).
    // amountKopecks: null → ближайший взнос (BNPL) / полная сумма (обычный заказ); число → произвольная сумма.
    adminPayOrder: (orderId, amountKopecks) =>
        apiFetch(`/api/admin/orders/${orderId}/pay`, {
            method: 'POST',
            body: JSON.stringify({ amountKopecks }),
        }),

    // ── Админ: счета ──────────────────────────────────────────────────────
    getAllInvoices: async () => {
        const page = await apiFetch('/api/admin/invoices?size=100');
        return page ? page.content : [];
    },

    // ── Профиль ───────────────────────────────────────────────────────────
    getProfile: () =>
        apiFetch('/api/profile/me'),
    updateProfile: (data) =>
        apiFetch('/api/profile/me', { method: 'PATCH', body: JSON.stringify(data) }),

    // ── Список продавцов (для выпадающего списка у администратора) ────────────
    getSellers: () =>
        apiFetch('/api/admin/sellers'),

    // ── Чат: диалоги и сообщения ──────────────────────────────────────────────
    getChatConversations: () =>
        apiFetch('/api/chat/conversations'),
    startChatConversation: (sellerId, message) =>
        apiFetch('/api/chat/conversations', {
            method: 'POST',
            body: JSON.stringify({ sellerId, message }),
        }),
    getChatMessages: (conversationId) =>
        apiFetch(`/api/chat/conversations/${conversationId}/messages`),
    pollChatMessages: (conversationId, afterId) =>
        apiFetch(`/api/chat/conversations/${conversationId}/messages?after=${afterId}`),
    sendChatMessage: (conversationId, content) =>
        apiFetch(`/api/chat/conversations/${conversationId}/messages`, {
            method: 'POST',
            body: JSON.stringify({ content }),
        }),

    // ── Техподдержка ──────────────────────────────────────────────────────
    startSupportConversation: () =>
        apiFetch('/api/support/conversations', { method: 'POST' }),
    getSupportConversations: () =>
        apiFetch('/api/support/conversations'),
    getSupportMessages: (id) =>
        apiFetch(`/api/support/conversations/${id}/messages`),
    pollSupportMessages: (id, afterId) =>
        apiFetch(`/api/support/conversations/${id}/messages?after=${afterId}`),
    sendSupportMessage: (id, content) =>
        apiFetch(`/api/support/conversations/${id}/messages`, {
            method: 'POST',
            body: JSON.stringify({ content }),
        }),

    // ── Админ: отправка произвольного письма ──────────────────────────────
    sendAdminEmail: (to, subject, text) =>
        apiFetch('/api/admin/emails', {
            method: 'POST',
            body: JSON.stringify({ to, subject, text }),
        }),

    // ── Бухгалтер: отчёты ─────────────────────────────────────────────────
    getAccountantSummary:   () => apiFetch('/api/accountant/summary'),
    getAccountantOrders:    () => apiFetch('/api/accountant/orders'),
    getAccountantCarts:     () => apiFetch('/api/accountant/carts'),
    getAccountantCustomers: () => apiFetch('/api/accountant/customers'),
    getAccountantEmails:    () => apiFetch('/api/accountant/emails'),
};
