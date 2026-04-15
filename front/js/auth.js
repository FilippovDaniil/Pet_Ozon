// auth.js — управление сессией (без реального бэкенда, эмуляция через localStorage)

const KNOWN_USERS = [
    { userId: 1, email: 'client@example.com', password: 'pass', name: 'Иван Клиентов', role: 'CLIENT' },
    { userId: 2, email: 'admin@example.com',  password: 'pass', name: 'Администратор', role: 'ADMIN'  },
];

function login(email, password) {
    const user = KNOWN_USERS.find(u => u.email === email && u.password === password);
    if (!user) return null;
    const { password: _, ...info } = user;
    localStorage.setItem('marketplace_user', JSON.stringify(info));
    return info;
}

function logout() {
    localStorage.removeItem('marketplace_user');
    window.location.href = 'login.html';
}

function getCurrentUser() {
    try {
        const raw = localStorage.getItem('marketplace_user');
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

function requireAuth(role) {
    const user = getCurrentUser();
    if (!user) { window.location.href = 'login.html'; return null; }
    if (role && user.role !== role) {
        window.location.href = user.role === 'ADMIN' ? 'admin.html' : 'client.html';
        return null;
    }
    return user;
}
