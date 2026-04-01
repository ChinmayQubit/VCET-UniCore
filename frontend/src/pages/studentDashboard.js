/**
 * Student Dashboard — ES Module version.
 */
import { apiGet, apiFetch } from '../api.js';
import { requireAuth, getStudentId, logout as doLogout } from '../auth.js';

let trendChartInstance = null;

export async function initStudentDashboard() {
    if (!requireAuth()) return;

    const studentId = getStudentId() || 1;

    // Load announcements independently (don't block KPIs)
    loadStudentAnnouncements(studentId);

    try {
        const [cgpa, aiCgpa, aiTarget, trendData, advisor, companies, rawResults, attendance] = await Promise.all([
            apiGet(`/results/cgpa/${studentId}`),
            apiGet(`/analysis/ai-cgpa/${studentId}`),
            apiGet(`/analysis/target/${studentId}`),
            apiGet(`/analysis/trend/${studentId}`),
            apiGet(`/analysis/advisor/${studentId}`),
            apiGet(`/analysis/companies/${studentId}`),
            apiGet(`/results/student/${studentId}`),
            apiGet(`/api/attendance/summary/${studentId}`)
        ]);

        // Handle Empty State
        if (!Array.isArray(rawResults) || rawResults.length === 0) {
            document.getElementById('cgpa').innerText = 'No Data';
            document.getElementById('aiCgpa').innerText = '--';
            document.getElementById('targetSgpa').innerText = '--';
            document.getElementById('adviceText').innerText = "Welcome! Once your semester results are uploaded by the college, your AI Academic Advisor will awaken to analyze your performance.";
            document.getElementById('placementDesc').innerText = "Complete at least one semester to see campus placement eligibility.";
            const tableBody = document.getElementById('subjectTableBody');
            if (tableBody) tableBody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 30px; color: #a0aec0;">No semester results uploaded yet.</td></tr>';
            const riskBanner = document.getElementById('riskBanner');
            if (riskBanner) riskBanner.classList.add('hidden');
            document.getElementById('dashboardContent').style.opacity = 1;
            return;
        }

        document.getElementById('cgpa').innerText = cgpa || '0.0';
        document.getElementById('aiCgpa').innerText = aiCgpa?.predictedFinalCGPA !== undefined ? Number(aiCgpa.predictedFinalCGPA).toFixed(2) : '--';
        document.getElementById('targetSgpa').innerText = aiTarget?.aiTargetSGPA !== undefined ? Number(aiTarget.aiTargetSGPA).toFixed(2) : '--';

        // Attendance
        if (attendance?.overallPercentage !== undefined) {
            const perc = attendance.overallPercentage;
            document.getElementById('overallAttendance').innerText = perc + '%';
            const statusEl = document.getElementById('attendanceStatus');
            if (perc >= 75) {
                statusEl.innerText = "Satisfactory ✅";
                statusEl.style.color = "var(--accent-green)";
            } else {
                statusEl.innerText = "Low Attendance ⚠️";
                statusEl.style.color = "var(--accent-red)";
            }
        }

        document.getElementById('adviceText').innerText = advisor?.advice || "Keep working hard!";
        renderTags('subjectTags', advisor?.weakSubjects, 'badge-weak');

        let placements = typeof companies === 'string' ? "Not easily available." : "Eligible for multiple roles.";
        document.getElementById('placementDesc').innerText = placements;
        if (Array.isArray(companies)) {
            renderTags('companiesList', companies, 'badge-strong');
        }

        // Subject Performance Table
        let weakCount = 0;
        if (Array.isArray(rawResults)) {
            const tableBody = document.getElementById('subjectTableBody');
            tableBody.innerHTML = '';
            let validResults = rawResults.filter(r => r.courseCode != null && r.semester > 0);
            validResults.sort((a, b) => b.semester - a.semester);
            validResults.forEach(res => {
                const gp = res.gradePoint || 0;
                let status = 'Average', sClass = 'badge-average';
                if (gp > 8) { status = 'Strong'; sClass = 'badge-strong'; }
                else if (gp < 6) { status = 'Weak'; sClass = 'badge-weak'; weakCount++; }
                tableBody.innerHTML += `
                    <tr>
                        <td><strong>${res.courseName || res.courseCode || 'N/A'}</strong></td>
                        <td>Sem ${res.semester || '-'}</td>
                        <td>${res.credits || '-'}</td>
                        <td style="font-weight:600;">${gp}</td>
                        <td><span class="badge ${sClass}">${status}</span></td>
                    </tr>`;
            });
        }

        // Chart
        if (trendData?.semesterSgpa && typeof trendData.semesterSgpa === 'object') {
            const semMap = trendData.semesterSgpa;
            const labels = Object.keys(semMap).map(sem => `Sem ${sem}`);
            const data = Object.values(semMap);
            const apiTrend = trendData.trend || 'Stable';
            const isDeclining = apiTrend === 'Declining';
            const trendLabel = isDeclining ? 'Declining Trend' : (apiTrend === 'Improving' ? 'Upward Trend ↑' : 'Stable Trend');
            document.getElementById('trend').innerText = trendLabel;
            document.getElementById('trend').style.color = isDeclining ? 'var(--accent-red)' : 'var(--accent-green)';
            renderStudentChart(labels, data);
        }

        // Risk detection
        if (cgpa < 6.5 || weakCount >= 2) {
            document.getElementById('riskBanner').classList.remove('hidden');
            let riskMsg = "Academic risk detected: ";
            if (cgpa < 6.5) riskMsg += "CGPA is critically low. ";
            if (weakCount > 0) riskMsg += `You have ${weakCount} weak subject(s).`;
            document.getElementById('riskMessage').innerText = riskMsg;
        } else {
            document.getElementById('riskBanner').classList.add('hidden');
        }

        document.getElementById('dashboardContent').style.opacity = 1;
    } catch (error) {
        console.error("Dashboard Init Error:", error);
        alert("Could not load dashboard data. Is the backend running?");
        document.getElementById('dashboardContent').style.opacity = 1;
    }
}

function renderTags(elementId, items, cssClass) {
    const el = document.getElementById(elementId);
    if (!el || !items) return;
    el.innerHTML = items.map(item => `<span class="badge ${cssClass}">${item}</span>`).join(' ');
}

function renderStudentChart(labels, data) {
    const ctx = document.getElementById('trendChart');
    if (!ctx) return;
    if (trendChartInstance) trendChartInstance.destroy();
    const gradient = ctx.getContext('2d').createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(59, 130, 246, 0.4)');
    gradient.addColorStop(1, 'rgba(59, 130, 246, 0.05)');
    trendChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels, datasets: [{
                label: 'SGPA', data, borderColor: '#3b82f6', backgroundColor: gradient,
                borderWidth: 3, fill: true, tension: 0.4,
                pointBackgroundColor: '#ffffff', pointBorderColor: '#3b82f6', pointRadius: 4, pointHoverRadius: 6
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { min: 0, max: 10, grid: { color: 'rgba(255,255,255,0.05)' } },
                x: { grid: { display: false } }
            }
        }
    });
}

// Expose globals for onclick handlers
window.logout = doLogout;
window.openChangePasswordModal = function () {
    document.getElementById('changePasswordModal').classList.remove('hidden');
    document.getElementById('newPassword').value = '';
};
window.closeChangePasswordModal = function () {
    document.getElementById('changePasswordModal').classList.add('hidden');
};
window.submitChangePassword = async function (event) {
    event.preventDefault();
    const studentId = getStudentId();
    const newPassword = document.getElementById('newPassword').value;
    try {
        const response = await apiFetch(`/students/${studentId}/change-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ newPassword })
        });
        if (response.ok) {
            alert('Password changed successfully!');
            window.closeChangePasswordModal();
        } else {
            const data = await response.json();
            alert(data.error || 'Failed to change password');
        }
    } catch (error) {
        alert('An error occurred while changing the password.');
    }
};

/** ---------------- STUDENT ANNOUNCEMENTS ---------------- **/
async function loadStudentAnnouncements(studentId) {
    const container = document.getElementById('studentAnnouncementsList');
    if (!container) return;

    try {
        const response = await apiFetch(`/announcements/student/${studentId}`);
        if (!response.ok) throw new Error('Failed to load announcements');
        const announcements = await response.json();

        if (!announcements || announcements.length === 0) {
            container.innerHTML = '<p style="color:var(--text-muted); font-size:0.9rem;">No announcements at this time.</p>';
            return;
        }

        container.innerHTML = '';
        announcements.forEach(a => {
            const date = a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
            container.innerHTML += `
                <div style="background:rgba(255,255,255,0.04); border:1px solid rgba(255,255,255,0.08); border-radius:10px; padding:14px 18px; margin-bottom:10px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                        <strong style="font-size:1rem; color:#f59e0b;">${a.title}</strong>
                        <span style="font-size:0.75rem; color:var(--text-muted);">${date}</span>
                    </div>
                    <p style="margin:0; font-size:0.9rem; color:#cbd5e1; line-height:1.5;">${a.message}</p>
                </div>
            `;
        });
    } catch (err) {
        console.error('Failed to load announcements:', err);
        container.innerHTML = '<p style="color:var(--text-muted);">Could not load announcements.</p>';
    }
}
