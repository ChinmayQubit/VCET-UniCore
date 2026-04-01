/**
 * Centralized API client for the Student Academic Portal.
 * All API calls go through this module to ensure consistent auth headers.
 */

// In dev mode, Vite proxy handles routing to localhost:8080
// In production, the app is served from the same origin as Spring Boot
const API_BASE_URL = '';

/**
 * Get the stored JWT token.
 */
export function getAuthToken() {
    return localStorage.getItem('authToken');
}

/**
 * Authenticated fetch wrapper.
 * Automatically attaches JWT Bearer token and handles 401/403 redirects.
 */
export async function apiFetch(endpoint, options = {}) {
    const token = getAuthToken();

    if (!options.headers) {
        options.headers = {};
    }

    // Don't override Content-Type for FormData (file uploads)
    if (options.body instanceof FormData) {
        // Let the browser set Content-Type with boundary
    } else if (!options.headers['Content-Type'] && options.body) {
        options.headers['Content-Type'] = 'application/json';
    }

    if (token) {
        options.headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(API_BASE_URL + endpoint, options);

    // Handle expired/invalid tokens globally
    if (response.status === 401 || response.status === 403) {
        localStorage.clear();
        window.location.href = '/';
        throw new Error('Authentication expired. Please log in again.');
    }

    return response;
}

/**
 * Convenience method: GET request that returns parsed JSON (or text fallback).
 */
export async function apiGet(endpoint) {
    try {
        const response = await apiFetch(endpoint);
        if (!response.ok) throw new Error('API Error: ' + response.status);
        const ct = response.headers.get('content-type');
        if (ct && ct.includes('application/json')) {
            return await response.json();
        }
        return await response.text();
    } catch (error) {
        console.warn(`Fetch failed for ${endpoint}:`, error);
        return null;
    }
}

/**
 * Convenience method: POST request with JSON body.
 */
export async function apiPost(endpoint, body) {
    return apiFetch(endpoint, {
        method: 'POST',
        body: JSON.stringify(body),
    });
}

/**
 * Convenience method: POST with FormData (file uploads).
 */
export async function apiUpload(endpoint, formData) {
    return apiFetch(endpoint, {
        method: 'POST',
        body: formData,
    });
}

/**
 * Convenience method: PUT request with JSON body.
 */
export async function apiPut(endpoint, body) {
    return apiFetch(endpoint, {
        method: 'PUT',
        body: JSON.stringify(body),
    });
}

/**
 * Convenience method: DELETE request.
 */
export async function apiDelete(endpoint) {
    return apiFetch(endpoint, { method: 'DELETE' });
}
