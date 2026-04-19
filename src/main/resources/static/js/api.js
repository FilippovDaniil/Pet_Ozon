// api.js — обёртка над fetch для всех эндпоинтов бэкенда
// API_BASE определён в auth.js, который загружается раньше

function buildHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    return headers;
}

async function apiFetch(path, { method = 'GET', body } = {}) {
    const res = await fetch(API_BASE + path, {
        method,
        headers: buildHeaders(),
        body,
    });
    if (res.status === 401 || res.status === 403) {
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
        apiFetch('/api/cart/add', { method: 'POST', body: JSON.stringify({ productId, quantity }) }),
    removeFromCart: (cartItemId) =>
        apiFetch(`/api/cart/remove/${cartItemId}`, { method: 'DELETE' }),
    updateCartItem: (cartItemId, quantity) =>
        apiFetch(`/api/cart/update/${cartItemId}`, { method: 'PUT', body: JSON.stringify({ quantity }) }),
    checkout: (shippingAddress) =>
        apiFetch('/api/cart/checkout', { method: 'POST', body: JSON.stringify({ shippingAddress }) }),

    // ── Заказы (клиент) ───────────────────────────────────────────────────
    getMyOrders: async () => {
        const page = await apiFetch('/api/orders/my');
        return page ? page.content : [];
    },

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
    getAllOrders: async () => {
        const page = await apiFetch('/api/admin/orders');
        return page ? page.content : [];
    },
    updateOrderStatus: (id, status) =>
        apiFetch(`/api/admin/orders/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),

    // ── Админ: счета ──────────────────────────────────────────────────────
    getAllInvoices: () =>
        apiFetch('/api/admin/invoices'),

    // ── Профиль ───────────────────────────────────────────────────────────
    getProfile: () =>
        apiFetch('/api/profile/me'),
    updateProfile: (data) =>
        apiFetch('/api/profile/me', { method: 'PATCH', body: JSON.stringify(data) }),
};
