// api.js — обёртка над fetch для всех эндпоинтов бэкенда

const API_BASE = 'http://localhost:8888';

function buildHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    const user = getCurrentUser();
    if (user) headers['X-User-Id'] = String(user.userId);
    return headers;
}

async function apiFetch(path, { method = 'GET', body } = {}) {
    const res = await fetch(API_BASE + path, {
        method,
        headers: buildHeaders(),
        body,
    });
    if (res.status === 204) return null;
    const text = await res.text();
    if (!text || !text.trim()) return null;
    let json;
    try { json = JSON.parse(text); } catch { return null; }
    if (!res.ok) throw new Error(json?.message || `Ошибка сервера: HTTP ${res.status}`);
    return json;
}

const api = {
    // ── Товары ────────────────────────────────────────────────────────────
    getProducts: () =>
        apiFetch('/api/products'),

    // ── Корзина ───────────────────────────────────────────────────────────
    getCart: () =>
        apiFetch('/api/cart'),
    addToCart: (productId, quantity) =>
        apiFetch('/api/cart/add', { method: 'POST', body: JSON.stringify({ productId, quantity }) }),
    removeFromCart: (cartItemId) =>
        apiFetch(`/api/cart/remove/${cartItemId}`, { method: 'DELETE' }),
    updateCartItem: (cartItemId, quantity) =>
        apiFetch(`/api/cart/update/${cartItemId}`, { method: 'PUT', body: JSON.stringify({ quantity }) }),
    checkout: (shippingAddress) =>
        apiFetch('/api/cart/checkout', { method: 'POST', body: JSON.stringify({ shippingAddress }) }),

    // ── Заказы (клиент) ───────────────────────────────────────────────────
    getMyOrders: () =>
        apiFetch('/api/orders/my'),

    // ── Счета ─────────────────────────────────────────────────────────────
    getInvoice: (id) =>
        apiFetch(`/api/invoice/${id}`),
    payInvoice: (invoiceId, paymentMethod) =>
        apiFetch(`/api/invoice/${invoiceId}/pay`, { method: 'POST', body: JSON.stringify({ paymentMethod }) }),

    // ── Продавец ──────────────────────────────────────────────────────────
    getSellerProducts: () =>
        apiFetch('/api/seller/products'),
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

    // ── Админ: товары ─────────────────────────────────────────────────────
    createProduct: (data) =>
        apiFetch('/api/admin/products', { method: 'POST', body: JSON.stringify(data) }),
    updateProduct: (id, data) =>
        apiFetch(`/api/admin/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteProduct: (id) =>
        apiFetch(`/api/admin/products/${id}`, { method: 'DELETE' }),

    // ── Админ: заказы ─────────────────────────────────────────────────────
    getAllOrders: () =>
        apiFetch('/api/admin/orders'),
    updateOrderStatus: (id, status) =>
        apiFetch(`/api/admin/orders/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),

    // ── Админ: счета ──────────────────────────────────────────────────────
    getAllInvoices: () =>
        apiFetch('/api/admin/invoices'),
};
