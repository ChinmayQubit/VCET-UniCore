/**
 * Login page logic — ES Module version.
 */
import { apiFetch } from '../api.js';
import { saveLoginData } from '../auth.js';

export function initLoginPage() {
    const loginForm = document.getElementById('loginForm');
    if (!loginForm) return;

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const role = document.getElementById('role').value;
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value.trim();

        if (!email || !password) {
            showError('Please enter both email and password.');
            return;
        }

        const submitBtn = document.getElementById('loginBtn');
        const btnText = submitBtn.querySelector('.btn-text');
        const btnLoader = submitBtn.querySelector('.btn-loader');
        btnText.style.display = 'none';
        btnLoader.style.display = 'inline-flex';
        submitBtn.disabled = true;
        clearError();

        try {
            let endpoint;
            if (role === 'admin') {
                endpoint = `/auth/admin-login?email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`;
            } else if (role === 'faculty') {
                endpoint = `/auth/faculty-login?email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`;
            } else {
                endpoint = `/auth/login?email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`;
            }

            const response = await fetch(endpoint, { method: 'POST' });

            if (response.status === 401) {
                const errorMessages = {
                    admin: 'Invalid administrator credentials.',
                    faculty: 'Invalid faculty credentials.',
                    student: 'Invalid email or password. Please try again.'
                };
                showError(errorMessages[role] || 'Invalid credentials.');
                return;
            }

            if (!response.ok) {
                const errData = await response.json().catch(() => null);
                showError(errData?.error || 'Server error');
                return;
            }

            const data = await response.json();

            if (data && data.token) {
                saveLoginData(data);
                btnText.textContent = '✓ Success';
                btnText.style.display = 'inline';
                btnLoader.style.display = 'none';

                let redirectUrl = '/student-dashboard.html';
                if (role === 'admin') redirectUrl = '/admin-dashboard.html';
                else if (role === 'faculty') redirectUrl = '/faculty-dashboard.html';

                setTimeout(() => {
                    window.location.href = redirectUrl;
                }, 600);
            } else {
                showError('Invalid response from server.');
            }
        } catch (err) {
            console.error(err);
            showError('Could not connect to the server. Is the backend running?');
        } finally {
            setTimeout(() => {
                if (btnText) btnText.textContent = 'Sign In';
                if (btnText) btnText.style.display = 'inline';
                if (btnLoader) btnLoader.style.display = 'none';
                if (submitBtn) submitBtn.disabled = false;
            }, 700);
        }
    });
}

function showError(msg) {
    const errorEl = document.getElementById('errorMessage');
    errorEl.innerText = msg;
    errorEl.style.opacity = '0';
    requestAnimationFrame(() => {
        errorEl.style.transition = 'opacity 0.3s ease';
        errorEl.style.opacity = '1';
    });
    const card = document.getElementById('loginCard');
    card.style.opacity = '1';
    card.style.animation = 'none';
    requestAnimationFrame(() => {
        card.style.animation = 'cardShake 0.4s ease';
    });
}

function clearError() {
    const errorEl = document.getElementById('errorMessage');
    errorEl.innerText = '';
    errorEl.style.opacity = '0';
}

// Expose globally for onclick handlers in HTML
window.togglePasswordVisibility = function () {
    const pwInput = document.getElementById('password');
    const eyeIcon = document.getElementById('eyeIcon');
    const eyeOffIcon = document.getElementById('eyeOffIcon');
    if (pwInput.type === 'password') {
        pwInput.type = 'text';
        eyeIcon.style.display = 'none';
        eyeOffIcon.style.display = 'block';
    } else {
        pwInput.type = 'password';
        eyeIcon.style.display = 'block';
        eyeOffIcon.style.display = 'none';
    }
};

window.openForgotPasswordModal = function () {
    const el = document.getElementById('forgotPasswordModal');
    if (el) {
        el.classList.remove('hidden');
        const inp = document.getElementById('forgotEmail');
        if (inp) inp.value = document.getElementById('email')?.value?.trim() || '';
        document.getElementById('forgotPasswordMessage').innerText = '';
    }
};

window.closeForgotPasswordModal = function () {
    const el = document.getElementById('forgotPasswordModal');
    if (el) el.classList.add('hidden');
};

window.submitForgotPassword = async function (e) {
    e.preventDefault();
    const email = document.getElementById('forgotEmail').value.trim();
    const role = document.getElementById('role') ? document.getElementById('role').value : 'student';
    const msgEl = document.getElementById('forgotPasswordMessage');
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        let endpoint = `/auth/forgot-password?email=${encodeURIComponent(email)}`;
        if (role === 'faculty') {
            endpoint = `/auth/faculty/forgot-password?email=${encodeURIComponent(email)}`;
        }
        
        const response = await fetch(endpoint, { method: 'POST' });
        const data = response.ok ? await response.json() : await response.json().catch(() => null);
        
        if (response.ok) {
            msgEl.style.color = 'var(--text-light, #c8c4b8)';
            msgEl.innerText = data?.message || 'Success.';
        } else {
            msgEl.style.color = 'var(--accent-red, #e74c3c)';
            msgEl.innerText = data?.message || 'Server error.';
        }
    } catch (err) {
        msgEl.style.color = 'var(--accent-red, #e74c3c)';
        msgEl.innerText = 'Could not reach the server.';
    } finally {
        btn.disabled = false;
    }
};

window.openClaimAccountModal = function () {
    document.getElementById('claimAccountModal').classList.remove('hidden');
    document.getElementById('claimAccountMessage').textContent = '';
    document.getElementById('claimAccountForm').reset();
};

window.closeClaimAccountModal = function () {
    document.getElementById('claimAccountModal').classList.add('hidden');
};

window.submitClaimAccount = async function (e) {
    e.preventDefault();
    const claimToken = document.getElementById('claimToken').value;
    const email = document.getElementById('claimEmail').value;
    const newPassword = document.getElementById('claimPassword').value;
    const msgElement = document.getElementById('claimAccountMessage');
    const claimBtnText = document.getElementById('claimBtnText');
    const claimBtnLoader = document.getElementById('claimBtnLoader');

    claimBtnText.style.display = 'none';
    claimBtnLoader.style.display = 'inline';
    msgElement.textContent = 'Processing...';

    try {
        const response = await fetch('/auth/claim-account', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ claimToken, email, newPassword })
        });
        const data = await response.json();
        if (response.ok) {
            msgElement.style.color = '#10b981';
            msgElement.textContent = '✅ ' + data.message;
            setTimeout(() => {
                window.closeClaimAccountModal();
                document.getElementById('email').value = email;
                document.getElementById('password').focus();
            }, 2500);
        } else {
            msgElement.style.color = '#ef4444';
            msgElement.textContent = '❌ ' + (data.error || 'Failed to claim account.');
        }
    } catch (err) {
        msgElement.style.color = '#ef4444';
        msgElement.textContent = '❌ Could not connect to the server.';
    } finally {
        claimBtnText.style.display = 'inline';
        claimBtnLoader.style.display = 'none';
    }
};

// Inject shake keyframes
const shakeStyle = document.createElement('style');
shakeStyle.textContent = `
    @keyframes cardShake {
        0%, 100% { transform: translateX(0); }
        20% { transform: translateX(-6px); }
        40% { transform: translateX(6px); }
        60% { transform: translateX(-4px); }
        80% { transform: translateX(4px); }
    }
`;
document.head.appendChild(shakeStyle);
