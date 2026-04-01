/**
 * Authentication module for the Student Academic Portal.
 * Handles token storage, user metadata, auth guards, and logout.
 */

// ======================== TOKEN STORAGE ========================

export function getToken() {
    return localStorage.getItem('authToken');
}

export function setToken(token) {
    localStorage.setItem('authToken', token);
}

export function clearToken() {
    localStorage.removeItem('authToken');
}

// ======================== USER METADATA ========================

export function getRole() {
    return localStorage.getItem('authRole');
}

export function setRole(role) {
    localStorage.setItem('authRole', role);
}

export function getStudentId() {
    return localStorage.getItem('studentId');
}

export function setStudentId(id) {
    localStorage.setItem('studentId', String(id));
}

export function getUserName() {
    return localStorage.getItem('userName');
}

export function setUserName(name) {
    localStorage.setItem('userName', name);
}

export function getDepartment() {
    return localStorage.getItem('userDepartment');
}

export function setDepartment(dept) {
    localStorage.setItem('userDepartment', dept);
}

export function isLoggedIn() {
    return !!getToken();
}

// ======================== LOGIN / LOGOUT ========================

/**
 * Store all auth data from a login response.
 */
export function saveLoginData(data) {
    setToken(data.token);
    setRole(data.role);
    if (data.studentId) setStudentId(data.studentId);
    if (data.name) setUserName(data.name);
    if (data.department) setDepartment(data.department);
}

/**
 * Clear all auth data and redirect to login.
 */
export function logout() {
    localStorage.removeItem('authToken');
    localStorage.removeItem('authRole');
    localStorage.removeItem('studentId');
    localStorage.removeItem('userName');
    localStorage.removeItem('userDepartment');
    window.location.href = '/';
}

// ======================== AUTH GUARDS ========================

/**
 * Require any authenticated user. Redirects to login if not authenticated.
 * @returns {boolean} true if authenticated
 */
export function requireAuth() {
    if (!isLoggedIn()) {
        window.location.href = '/';
        return false;
    }
    return true;
}

/**
 * Require STUDENT role.
 */
export function requireStudent() {
    if (!requireAuth()) return false;
    if (getRole() !== 'STUDENT' && getRole() !== 'ADMIN') {
        alert('Access denied. Student login required.');
        window.location.href = '/';
        return false;
    }
    return true;
}

/**
 * Require ADMIN role.
 */
export function requireAdmin() {
    if (!requireAuth()) return false;
    if (getRole() !== 'ADMIN') {
        alert('Access denied. Admin login required.');
        window.location.href = '/';
        return false;
    }
    return true;
}

/**
 * Require FACULTY role.
 */
export function requireFaculty() {
    if (!requireAuth()) return false;
    if (getRole() !== 'FACULTY') {
        alert('Access denied. Faculty login required.');
        window.location.href = '/';
        return false;
    }
    return true;
}

