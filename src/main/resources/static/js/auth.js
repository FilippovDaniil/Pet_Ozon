// auth.js — управление сессией через JWT

const API_BASE = '';

function saveSession(data) {
    sessionStorage.setItem('marketplace_token', data.token);
    sessionStorage.setItem('marketplace_user', JSON.stringify({
        userId:   data.userId,
        email:    data.email,
        role:     data.role,
        name:     data.fullName || data.email,
        shopName: data.shopName || null,
    }));
}

function getToken() {
    return sessionStorage.getItem('marketplace_token');
}

function getCurrentUser() {
    try {
        const raw = sessionStorage.getItem('marketplace_user');
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

async function login(email, password) {
    const res = await fetch(API_BASE + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Неверный email или пароль');
    }
    const data = await res.json();
    saveSession(data);
    return data;
}

async function register(email, password, fullName) {
    const res = await fetch(API_BASE + '/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, fullName }),
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Ошибка регистрации');
    }
    const data = await res.json();
    saveSession(data);
    return data;
}

function logout() {
    sessionStorage.removeItem('marketplace_token');
    sessionStorage.removeItem('marketplace_user');
    window.location.href = 'login.html';
}

function roleHome(role) {
    if (role === 'ADMIN')      return 'admin.html';
    if (role === 'SELLER')     return 'seller.html';
    if (role === 'ACCOUNTANT') return 'accountant.html';
    return 'client.html';
}

function requireAuth(role) {
    const user = getCurrentUser();
    if (!user || !getToken()) { window.location.href = 'login.html'; return null; }
    if (role && user.role !== role) {
        window.location.href = roleHome(user.role);
        return null;
    }
    return user;
}
