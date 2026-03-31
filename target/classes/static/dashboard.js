const API_BASE_URL = 'http://localhost:8080';

// Global Chart instances
let trendChartInstance = null;

// Ensure we have a student ID
const studentId = localStorage.getItem('studentId') || 1; // Default to 1 for demo if missing

/** ---------------- STUDENT DASHBOARD ---------------- **/
async function initStudentDashboard() {
    try {
        // Fetch core KPIs concurrently where applicable
        const [cgpa, aiCgpa, aiTarget, trendData, advisor, companies, rawResults, attendance] = await Promise.all([
            fetchData(`/results/cgpa/${studentId}`),
            fetchData(`/analysis/ai-cgpa/${studentId}`),
            fetchData(`/analysis/target/${studentId}`),
            fetchData(`/analysis/trend/${studentId}`),
            fetchData(`/analysis/advisor/${studentId}`),
            fetchData(`/analysis/companies/${studentId}`),
            fetchData(`/results/student/${studentId}`), // Fetches List<Result>
            fetchData(`/api/attendance/summary/${studentId}`)
        ]);

        // Handle Empty State for New Students
        if (!Array.isArray(rawResults) || rawResults.length === 0) {
            document.getElementById('cgpa').innerText = 'No Data';
            document.getElementById('aiCgpa').innerText = '--';
            document.getElementById('targetSgpa').innerText = '--';
            document.getElementById('adviceText').innerText = "Welcome! Once your semester results are uploaded by the college, your AI Academic Advisor will awaken to analyze your performance and predict your trajectory.";
            document.getElementById('placementDesc').innerText = "Complete at least one semester to see campus placement eligibility.";
            const tableBody = document.getElementById('subjectTableBody');
            if (tableBody) tableBody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 30px; color: #a0aec0;">No semester results uploaded yet.</td></tr>';
            const riskBanner = document.getElementById('riskBanner');
            if (riskBanner) riskBanner.classList.add('hidden');
            
            document.getElementById('dashboardContent').style.opacity = 1;
            return;
        }

        // Process data for UI
        document.getElementById('cgpa').innerText = cgpa || '0.0';
        // Placement readiness UI removed per user request
        document.getElementById('aiCgpa').innerText = aiCgpa && aiCgpa.predictedFinalCGPA !== undefined ? Number(aiCgpa.predictedFinalCGPA).toFixed(2) : '--';
        document.getElementById('targetSgpa').innerText = aiTarget && aiTarget.aiTargetSGPA !== undefined ? Number(aiTarget.aiTargetSGPA).toFixed(2) : '--';

        // Attendance
        if (attendance && attendance.overallPercentage !== undefined) {
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

        // Advisor & Companies
        document.getElementById('adviceText').innerText = advisor?.advice || "Keep working hard!";
        renderTags('subjectTags', advisor?.weakSubjects, 'badge-weak');
        
        let placements = typeof companies === 'string' ? "Not easily available." : "Eligible for multiple roles.";
        document.getElementById('placementDesc').innerText = placements;
        if(Array.isArray(companies)) {
            renderTags('companiesList', companies, 'badge-strong');
        }

        // Subject Performance Table & Risk Detection
        let weakCount = 0;
        let isDeclining = false;
        
        if (Array.isArray(rawResults)) {
            const tableBody = document.getElementById('subjectTableBody');
            tableBody.innerHTML = '';

            // Filter out old legacy null results without semantics
            let validResults = rawResults.filter(r => r.courseCode != null && r.semester > 0);

            // Sort by semester descending
            validResults.sort((a,b) => b.semester - a.semester);

            validResults.forEach(res => {
                const gp = res.gradePoint || 0;
                let status = 'Average';
                let sClass = 'badge-average';
                
                if (gp > 8) { status = 'Strong'; sClass = 'badge-strong'; }
                else if (gp < 6) { status = 'Weak'; sClass = 'badge-weak'; weakCount++; }

                tableBody.innerHTML += `
                    <tr>
                        <td><strong>${res.courseName || res.courseCode || 'N/A'}</strong></td>
                        <td>Sem ${res.semester || '-'}</td>
                        <td>${res.credits || '-'}</td>
                        <td style="font-weight:600;">${gp}</td>
                        <td><span class="badge ${sClass}">${status}</span></td>
                    </tr>
                `;
            });
        }

        // Render Chart
        if (trendData && trendData.semesterSgpa && typeof trendData.semesterSgpa === 'object') {
            const semMap = trendData.semesterSgpa;
            const labels = Object.keys(semMap).map(sem => `Sem ${sem}`);
            const data = Object.values(semMap);
            
            // Use trend string from backend (Improving / Declining / Stable)
            const apiTrend = trendData.trend || 'Stable';
            isDeclining = apiTrend === 'Declining';

            // Set trend label
            const trendLabel = isDeclining ? 'Declining Trend' : (apiTrend === 'Improving' ? 'Upward Trend ↑' : 'Stable Trend');
            document.getElementById('trend').innerText = trendLabel;
            document.getElementById('trend').style.color = isDeclining ? 'var(--accent-red)' : 'var(--accent-green)';

            renderStudentChart(labels, data);
        }

        // Risk detection: only show for genuinely bad performance (not just slight decline)
        if (cgpa < 6.5 || weakCount >= 2) {
            document.getElementById('riskBanner').classList.remove('hidden');
            let riskMsg = "Academic risk detected: ";
            if(cgpa < 6.5) riskMsg += "CGPA is critically low. ";
            if(weakCount > 0) riskMsg += `You have ${weakCount} weak subject(s). Focus on improving them.`;
            document.getElementById('riskMessage').innerText = riskMsg;
        } else {
            document.getElementById('riskBanner').classList.add('hidden');
        }

        // Reveal dashboard smoothly
        document.getElementById('dashboardContent').style.opacity = 1;

    } catch (error) {
        console.error("Dashboard Init Error:", error);
        alert("Could not load some dashboard data. Is the backend running?");
        document.getElementById('dashboardContent').style.opacity = 1;
    }
}

/** ---------------- UTILITIES & CHARTS ---------------- **/

async function fetchData(endpoint) {
    try {
        const response = await fetch(API_BASE_URL + endpoint);
        if (!response.ok) throw new Error('API Error: ' + response.status);
        const ct = response.headers.get("content-type");
        if (ct && ct.includes("application/json")) {
            return await response.json();
        }
        return await response.text();
    } catch (error) {
        console.warn(`Fetch failed for ${endpoint}:`, error);
        return null;
    }
}

function renderTags(elementId, items, cssClass) {
    const el = document.getElementById(elementId);
    if(!el || !items) return;
    el.innerHTML = items.map(item => `<span class="badge ${cssClass}">${item}</span>`).join(' ');
}

function logout() {
    localStorage.removeItem('studentId');
    window.location.href = 'index.html';
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
            labels: labels,
            datasets: [{
                label: 'SGPA',
                data: data,
                borderColor: '#3b82f6',
                backgroundColor: gradient,
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointBackgroundColor: '#ffffff',
                pointBorderColor: '#3b82f6',
                pointRadius: 4,
                pointHoverRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { min: 0, max: 10, grid: { color: 'rgba(255,255,255,0.05)' } },
                x: { grid: { display: false } }
            }
        }
    });
}

/** ---------------- CHANGE PASSWORD ---------------- **/
function openChangePasswordModal() {
    document.getElementById('changePasswordModal').classList.remove('hidden');
    document.getElementById('newPassword').value = '';
}

function closeChangePasswordModal() {
    document.getElementById('changePasswordModal').classList.add('hidden');
}

async function submitChangePassword(event) {
    event.preventDefault();
    const newPassword = document.getElementById('newPassword').value;
    
    try {
        const response = await fetch(`${API_BASE_URL}/students/${studentId}/change-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ newPassword })
        });
        
        if (response.ok) {
            alert('Password changed successfully!');
            closeChangePasswordModal();
        } else {
            const data = await response.json();
            alert(data.error || 'Failed to change password');
        }
    } catch (error) {
        console.error("Change Password Error:", error);
        alert('An error occurred while changing the password.');
    }
}
