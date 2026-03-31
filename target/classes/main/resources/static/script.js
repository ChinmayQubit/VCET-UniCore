document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const errorMessage = document.getElementById('errorMessage');
    
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const role = document.getElementById('role').value;
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value.trim();
        
        // Basic validation
        if (!email || !password) {
            showError("Please enter both email and password.");
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
            if (role === 'student') {
                const response = await fetch(`http://localhost:8080/auth/login?email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`, {
                    method: 'POST'
                });
                
                if (response.status === 401) {
                    showError("Invalid email or password. Please try again.");
                    return;
                }

                if (!response.ok) {
                    throw new Error('Server error');
                }
                
                const studentData = await response.json();
                
                if (studentData && studentData.id) {
                    // Success — brief delay for visual feedback
                    submitBtn.querySelector('.btn-text').textContent = '✓ Success';
                    btnText.style.display = 'inline';
                    btnLoader.style.display = 'none';
                    localStorage.setItem('studentId', studentData.id);
                    setTimeout(() => {
                        window.location.href = "student-dashboard.html";
                    }, 600);
                } else {
                    showError("Invalid credentials. Student not found.");
                }
            } else if (role === 'admin') {
                const response = await fetch(`http://localhost:8080/auth/admin-login?email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`, {
                    method: 'POST'
                });
                
                if (response.status === 401) {
                    showError("Invalid administrator credentials.");
                    return;
                }
                
                if (!response.ok) {
                    throw new Error('Server error');
                }
                
                btnText.textContent = '✓ Success';
                btnText.style.display = 'inline';
                btnLoader.style.display = 'none';
                setTimeout(() => {
                    window.location.href = "admin-dashboard.html";
                }, 600);
            }
            
        } catch (err) {
            console.error(err);
            showError("Could not connect to the server. Is the backend running?");
        } finally {
            // Reset button state (only if still on page)
            setTimeout(() => {
                if (btnText) btnText.textContent = 'Sign In';
                if (btnText) btnText.style.display = 'inline';
                if (btnLoader) btnLoader.style.display = 'none';
                if (submitBtn) submitBtn.disabled = false;
            }, 700);
        }
    });
});

function showError(msg) {
    const errorEl = document.getElementById('errorMessage');
    errorEl.innerText = msg;
    errorEl.style.opacity = '0';
    
    // Quick fade in
    requestAnimationFrame(() => {
        errorEl.style.transition = 'opacity 0.3s ease';
        errorEl.style.opacity = '1';
    });

    // Shake the login card briefly
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

function togglePasswordVisibility() {
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
}

function openForgotPasswordModal() {
    const el = document.getElementById('forgotPasswordModal');
    if (el) {
        el.classList.remove('hidden');
        const inp = document.getElementById('forgotEmail');
        if (inp) inp.value = document.getElementById('email')?.value?.trim() || '';
        document.getElementById('forgotPasswordMessage').innerText = '';
    }
}

function closeForgotPasswordModal() {
    const el = document.getElementById('forgotPasswordModal');
    if (el) el.classList.add('hidden');
}

async function submitForgotPassword(e) {
    e.preventDefault();
    const email = document.getElementById('forgotEmail').value.trim();
    const msgEl = document.getElementById('forgotPasswordMessage');
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.querySelector('.btn-text') && (btn.querySelector('.btn-text').textContent = 'Checking...');
    
    try {
        const response = await fetch(
            `http://localhost:8080/auth/forgot-password?email=${encodeURIComponent(email)}`,
            { method: 'POST' }
        );
        const data = response.ok ? await response.json() : null;
        msgEl.style.color = 'var(--text-light, #c8c4b8)';
        msgEl.innerText = data?.message || 'Could not reach the server. Is the backend running?';
    } catch (err) {
        console.error(err);
        msgEl.style.color = 'var(--accent-red, #e74c3c)';
        msgEl.innerText = 'Could not reach the server. Ensure the Spring Boot app is running on port 8080.';
    } finally {
        btn.disabled = false;
        if (btn.querySelector('.btn-text')) btn.querySelector('.btn-text').textContent = 'Get Instructions';
    }
}

// Add the shake keyframes dynamically
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

// Claim Account Modal Logic
function openClaimAccountModal() {
    document.getElementById('claimAccountModal').classList.remove('hidden');
    document.getElementById('claimAccountMessage').textContent = '';
    document.getElementById('claimAccountMessage').className = 'forgot-message';
    document.getElementById('claimAccountForm').reset();
}

function closeClaimAccountModal() {
    document.getElementById('claimAccountModal').classList.add('hidden');
}

async function submitClaimAccount(e) {
    e.preventDefault();
    const claimToken = document.getElementById('claimToken').value;
    const email = document.getElementById('claimEmail').value;
    const newPassword = document.getElementById('claimPassword').value;
    
    const msgElement = document.getElementById('claimAccountMessage');
    const claimBtnText = document.getElementById('claimBtnText');
    const claimBtnLoader = document.getElementById('claimBtnLoader');
    
    // UI Loading state
    claimBtnText.style.display = 'none';
    claimBtnLoader.style.display = 'inline';
    msgElement.className = 'forgot-message';
    msgElement.textContent = 'Processing...';

    try {
        const response = await fetch(`http://localhost:8080/auth/claim-account`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({ claimToken, email, newPassword })
        });

        const data = await response.json();

        if (response.ok) {
            msgElement.style.color = '#10b981'; // Success green
            msgElement.textContent = '✅ ' + data.message;
            setTimeout(() => {
                closeClaimAccountModal();
                document.getElementById('email').value = email;
                document.getElementById('password').focus();
            }, 2500);
        } else {
            msgElement.style.color = '#ef4444'; // Error red
            msgElement.textContent = '❌ ' + (data.error || 'Failed to claim account.');
        }
    } catch (err) {
        console.error('Claim account error:', err);
        msgElement.style.color = '#ef4444';
        msgElement.textContent = '❌ Could not connect to the server.';
    } finally {
        claimBtnText.style.display = 'inline';
        claimBtnLoader.style.display = 'none';
    }
}
