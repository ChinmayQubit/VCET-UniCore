const API_BASE_URL = 'http://localhost:8080';

// Global Data
let studentsList = [];
let subjectsList = [];
let adminChartInstance = null;

async function initAdminDashboard() {
    await loadStudents();
    await loadSubjects();
    await loadResults();
    hookupAdminForms();
}

/** ---------------- TAB NAVIGATION ---------------- **/
function switchTab(tabId, navEl) {
    document.querySelectorAll('.tab-pane').forEach(el => {
        el.classList.remove('active');
        el.classList.add('hidden');
    });
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(el => el.classList.remove('active'));

    const target = document.getElementById(`tab-${tabId}`);
    if (target) {
        target.classList.remove('hidden');
        target.classList.add('active');
    }
    if (navEl && navEl.classList) {
        navEl.classList.add('active');
    }
}

/** ---------------- STUDENT MANAGEMENT ---------------- **/

async function loadStudents() {
    try {
        const response = await fetch(`${API_BASE_URL}/students`);
        if (!response.ok) throw new Error("Failed to load students");

        studentsList = await response.json();

        // Update Table
        const tbody = document.getElementById('adminStudentsTableBody');
        tbody.innerHTML = '';

        const cgpas = [];
        for (const s of studentsList) {
            try {
                const cgpaRes = await fetch(`${API_BASE_URL}/results/cgpa/${s.id}`);
                if (cgpaRes.ok) {
                    const cgpaVal = Number(await cgpaRes.json());
                    cgpas.push({ id: s.id, cgpa: cgpaVal > 0 ? cgpaVal : null });
                } else cgpas.push({ id: s.id, cgpa: null });
            } catch (_) {
                cgpas.push({ id: s.id, cgpa: null });
            }
        }

        studentsList.forEach(s => {
            const row = cgpas.find(c => c.id === s.id);
            const cg = row && row.cgpa != null ? row.cgpa.toFixed(2) : '—';
            tbody.innerHTML += `
                <tr>
                    <td>${s.id}</td>
                    <td><strong>${s.name}</strong></td>
                    <td>${s.email}</td>
                    <td>${s.department}</td>
                    <td>Sem ${s.semester}</td>
                    <td style="font-size:0.85rem; color:var(--text-muted);">${cg}</td>
                    <td>
                        <button type="button" class="btn-outline" style="padding:4px 8px; font-size:12px; margin-right:4px;" onclick="openEditStudent(${s.id})">Edit</button>
                        <button type="button" class="btn-outline" style="border-color: var(--accent-orange); color: var(--accent-orange); padding:4px 8px; font-size:12px; margin-right:4px;" onclick="resetStudentPassword(${s.id})">Reset Pwd</button>
                        <button type="button" class="btn-outline" style="border-color: var(--accent-red); color: var(--accent-red); padding:4px 8px; font-size:12px;" onclick="deleteStudent(${s.id})">Delete</button>
                    </td>
                </tr>
            `;
        });

        document.getElementById('adminTotalStudents').innerText = studentsList.length;

        let totalCgpa = 0, validCount = 0, placementReady = 0, atRisk = 0;
        for (const row of cgpas) {
            if (row.cgpa != null && row.cgpa > 0) {
                totalCgpa += row.cgpa;
                validCount++;
                if (row.cgpa >= 7) placementReady++;
                if (row.cgpa < 6.5) atRisk++;
            }
        }
        const avgCgpa = validCount > 0 ? (totalCgpa / validCount).toFixed(2) : '—';
        document.getElementById('adminAvgCgpa').innerText = avgCgpa;
        document.getElementById('adminPlacementReady').innerText = String(placementReady);
        document.getElementById('adminRiskCount').innerText = String(atRisk);

    } catch (err) {
        console.error(err);
        alert("Error loading students list.");
    }
}

async function addStudent(e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    const studentData = {
        name: document.getElementById('studName').value.trim(),
        email: document.getElementById('studEmail').value.trim(),
        department: document.getElementById('studDept').value.trim(),
        semester: parseInt(document.getElementById('studSem').value, 10)
    };

    try {
        const response = await fetch(`${API_BASE_URL}/students`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(studentData)
        });

        if (!response.ok) throw new Error("Failed to add student");

        const result = await response.json();
        alert(`✅ Student added successfully!\n\nAn official Welcome Email containing the required Claim Code (${result.claimToken}) has been automatically dispatched to ${studentData.email}.`);
        e.target.reset();
        await loadStudents();
    } catch (err) {
        console.error(err);
        alert("Failed to add student. Ensure backend is running.");
    } finally {
        btn.disabled = false;
    }
}

async function deleteStudent(id) {
    if (!confirm(`Are you sure you want to delete Student ID: ${id}?`)) return;

    try {
        const response = await fetch(`${API_BASE_URL}/students/${id}`, {
            method: 'DELETE'
        });

        if (!response.ok) throw new Error("Failed to delete student");

        alert("Student deleted successfully!");
        await loadStudents(); // Refresh table
    } catch (err) {
        console.error(err);
        alert("Failed to delete student. They might have dependent results.");
    }
}

function openEditStudent(id) {
    const s = studentsList.find(x => x.id === id);
    if (!s) return;
    document.getElementById('editStudId').value = s.id;
    document.getElementById('editStudName').value = s.name || '';
    document.getElementById('editStudEmail').value = s.email || '';
    document.getElementById('editStudDept').value = s.department || '';
    document.getElementById('editStudSem').value = s.semester;
    document.getElementById('editStudentModal').classList.remove('hidden');
}

function closeEditStudentModal() {
    document.getElementById('editStudentModal').classList.add('hidden');
}

async function submitEditStudent(e) {
    e.preventDefault();
    const id = document.getElementById('editStudId').value;
    const body = {
        name: document.getElementById('editStudName').value.trim(),
        email: document.getElementById('editStudEmail').value.trim(),
        department: document.getElementById('editStudDept').value.trim(),
        semester: parseInt(document.getElementById('editStudSem').value, 10)
    };
    try {
        const response = await fetch(`${API_BASE_URL}/students/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error('update failed');
        closeEditStudentModal();
        await loadStudents();
    } catch (err) {
        console.error(err);
        alert('Could not save student. Check values and try again.');
    }
}

/** ---------------- SUBJECT MANAGEMENT ---------------- **/

async function addSubject(e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    const subjectData = {
        subjectName: document.getElementById('subName').value.trim(),
        credits: parseInt(document.getElementById('subCredits').value, 10),
        semester: parseInt(document.getElementById('subSem').value, 10)
    };

    try {
        const response = await fetch(`${API_BASE_URL}/subjects`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(subjectData)
        });

        if (!response.ok) throw new Error("Failed to add subject");

        alert("Subject added successfully!");
        e.target.reset();
        await loadSubjects(); // Refresh
    } catch (err) {
        console.error(err);
        alert("Failed to add subject.");
    } finally {
        btn.disabled = false;
    }
}

async function loadSubjects() {
    try {
        const response = await fetch(`${API_BASE_URL}/subjects`);
        if (!response.ok) throw new Error("Failed to load subjects");
        subjectsList = await response.json();

        const tbody = document.getElementById('adminSubjectsTableBody');
        tbody.innerHTML = '';

        subjectsList.forEach(sub => {
            tbody.innerHTML += `
                <tr>
                    <td>${sub.id}</td>
                    <td><strong>${sub.subjectName}</strong></td>
                    <td>${sub.credits}</td>
                    <td>Sem ${sub.semester}</td>
                    <td>
                        <button type="button" class="btn-outline" style="padding:4px 8px; font-size:12px; margin-right:4px;" onclick="openEditSubject(${sub.id})">Edit</button>
                        <button type="button" class="btn-outline" style="border-color: var(--accent-red); color: var(--accent-red); padding:4px 8px; font-size:12px;" onclick="deleteSubject(${sub.id})">Delete</button>
                    </td>
                </tr>
            `;
        });
    } catch (err) {
        console.error(err);
    }
}

function openEditSubject(id) {
    const sub = subjectsList.find(x => x.id === id);
    if (!sub) return;
    document.getElementById('editSubId').value = sub.id;
    document.getElementById('editSubName').value = sub.subjectName || '';
    document.getElementById('editSubCredits').value = sub.credits;
    document.getElementById('editSubSem').value = sub.semester;
    document.getElementById('editSubjectModal').classList.remove('hidden');
}

function closeEditSubjectModal() {
    document.getElementById('editSubjectModal').classList.add('hidden');
}

async function submitEditSubject(e) {
    e.preventDefault();
    const id = document.getElementById('editSubId').value;
    const body = {
        subjectName: document.getElementById('editSubName').value.trim(),
        credits: parseInt(document.getElementById('editSubCredits').value, 10),
        semester: parseInt(document.getElementById('editSubSem').value, 10)
    };
    try {
        const response = await fetch(`${API_BASE_URL}/subjects/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error('update failed');
        closeEditSubjectModal();
        await loadSubjects();
    } catch (err) {
        console.error(err);
        alert('Could not save subject.');
    }
}

async function deleteSubject(id) {
    if (!confirm(`Are you sure you want to delete Subject ID: ${id}?`)) return;

    try {
        const response = await fetch(`${API_BASE_URL}/subjects/${id}`, {
            method: 'DELETE'
        });

        if (!response.ok) throw new Error("Failed to delete subject");

        alert("Subject deleted successfully!");
        await loadSubjects(); // Refresh table
    } catch (err) {
        console.error(err);
        alert("Failed to delete subject. Results may be dependent on it.");
    }
}

/** ---------------- RESULT LIST (uploads use admin-result-entry.html) ---------------- **/

async function loadResults() {
    try {
        const response = await fetch(`${API_BASE_URL}/results`);
        if (!response.ok) throw new Error("Failed to load results");
        const resultsList = await response.json();

        const tbody = document.getElementById('adminResultsTableBody');
        tbody.innerHTML = '';

        // Show most recent first (assume last in array is newest)
        const recentResults = resultsList.slice().reverse().slice(0, 50); // Show max 50 recent

        recentResults.forEach(res => {
            const studentId = res.student ? res.student.id : 'N/A';
            const courseName = res.courseName || (res.subject ? res.subject.subjectName : 'N/A');
            const total = res.totalMarks || '-';
            const gp = res.gradePoint || '0';
            const cg = res.creditGrade || '-';

            tbody.innerHTML += `
                <tr>
                    <td><strong>#${studentId}</strong></td>
                    <td>${courseName}</td>
                    <td>Sem ${res.semester}</td>
                    <td>${total}</td>
                    <td><span class="badge ${gp > 6 ? 'badge-strong' : 'badge-weak'}">${gp}</span></td>
                    <td>${cg}</td>
                </tr>
            `;
        });
    } catch (err) {
        console.error("No global results /results endpoint found or error", err);
    }
}

/** ---------------- UTILITIES ---------------- **/

function hookupAdminForms() {
    document.getElementById('addStudentForm').addEventListener('submit', addStudent);
    document.getElementById('addSubjectForm').addEventListener('submit', addSubject);
    document.getElementById('editStudentForm').addEventListener('submit', submitEditStudent);
    document.getElementById('editSubjectForm').addEventListener('submit', submitEditSubject);
}

function openStudentPreview(studentId) {
    localStorage.setItem('studentId', String(studentId));
    window.open('student-dashboard.html', '_blank');
}

function logout() {
    localStorage.removeItem('studentId');
    window.location.href = 'index.html';
}

async function loadAnalytics() {
    const tbody = document.getElementById('analyticsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:1rem;">Loading...</td></tr>';

    // Fetch all students
    const studRes = await fetch(`${API_BASE_URL}/students`);
    const students = await studRes.json();

    // Fetch CGPA for each student
    const studentData = [];
    for (const s of students) {
        try {
            const cgpaRes = await fetch(`${API_BASE_URL}/results/cgpa/${s.id}`);
            const cgpa = cgpaRes.ok ? await cgpaRes.json() : 0;
            studentData.push({ ...s, cgpa: parseFloat(cgpa) || 0 });
        } catch (_) {
            studentData.push({ ...s, cgpa: 0 });
        }
    }

    // Sort by CGPA descending
    studentData.sort((a, b) => b.cgpa - a.cgpa);

    // Populate table
    tbody.innerHTML = '';
    studentData.forEach((s, idx) => {
        let badge = '';
        if (s.cgpa >= 7.5) badge = '<span style="color:#10b981; font-weight:600;">✓ Eligible for Placement Drive</span>';
        else if (s.cgpa >= 6.5) badge = '<span style="color:#f59e0b; font-weight:600;">⚠ May be Eligible (Check Cutoff)</span>';
        else if (s.cgpa > 0) badge = '<span style="color:#ef4444; font-weight:600;">✗ Below Placement Cutoff</span>';
        else badge = '<span style="color:#94a3b8;">No Results Yet</span>';

        const rankEmoji = idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : `#${idx + 1}`;
        tbody.innerHTML += `
            <tr style="cursor:pointer;" onclick="openStudentPreview(${s.id})">
                <td><strong>${rankEmoji}</strong></td>
                <td><strong>${s.name}</strong></td>
                <td>${s.department}</td>
                <td><strong style="font-size:1.1rem; color:${s.cgpa >= 7 ? '#10b981' : '#ef4444'}">${s.cgpa > 0 ? s.cgpa.toFixed(2) : '--'}</strong></td>
                <td>${badge}</td>
            </tr>
        `;
    });

    // Draw chart
    const ctx = document.getElementById('adminDeptChart');
    if (!ctx) return;
    if (adminChartInstance) adminChartInstance.destroy();

    const names = studentData.map(s => s.name);
    const cgpas = studentData.map(s => s.cgpa);
    const colors = cgpas.map(c => c >= 8 ? '#10b981' : c >= 7 ? '#3b82f6' : c >= 6 ? '#f59e0b' : '#ef4444');

    adminChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: names,
            datasets: [{
                label: 'CGPA',
                data: cgpas,
                backgroundColor: colors,
                borderRadius: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: ctx => `CGPA: ${ctx.raw.toFixed(2)}`
                    }
                }
            },
            scales: {
                y: { min: 0, max: 10, grid: { color: 'rgba(255,255,255,0.05)' }, title: { display: true, text: 'CGPA', color: '#94a3b8' } },
                x: { grid: { display: false } }
            }
        }
    });
}

async function resetStudentPassword(id) {
    if (!confirm(`Reset password for Student ID: ${id} back to the default?`)) return;
    try {
        const response = await fetch(`${API_BASE_URL}/students/${id}/reset-password`, { method: 'POST' });
        if (!response.ok) throw new Error('reset failed');
        const data = await response.json();
        alert(`🔒 Password reset successful!\n\nThe account is now unclaimed again. Please send the student this new Claim Code to re-claim their account:\n\nCLAIM CODE: ${data.claimToken}`);
    } catch (err) {
        console.error(err);
        alert('Could not reset password. Check that the student exists and backend is running.');
    }
}

/** ---------------- BULK UPLOAD EXCEL/CSV ---------------- **/
async function bulkUploadStudents() {
    const fileInput = document.getElementById('studentsCsvFile');
    if (!fileInput.files.length) {
        alert("Please select a CSV file first!");
        return;
    }
    
    // Change button text to indicate loading
    const btn = event.target;
    const oldText = btn.innerText;
    btn.innerText = "Uploading...";
    btn.disabled = true;

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);

    try {
        const response = await fetch(`${API_BASE_URL}/students/upload`, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (response.ok) {
            alert("✅ " + result.message);
            fileInput.value = "";
            document.getElementById('studFileLabel').innerText = "Choose CSV...";
            await loadStudents();
        } else {
            alert(`Upload Failed: ${result.error || response.statusText}`);
        }
    } catch (err) {
        console.error("Bulk upload students error:", err);
        alert("An error occurred during bulk upload.");
    } finally {
        btn.innerText = oldText;
        btn.disabled = false;
    }
}

async function bulkUploadResults() {
    const fileInput = document.getElementById('resultsCsvFile');
    if (!fileInput.files.length) {
        alert("Please select a CSV file first!");
        return;
    }
    
    const btn = event.target;
    const oldText = btn.innerText;
    btn.innerText = "Processing...";
    btn.disabled = true;

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);

    try {
        const response = await fetch(`${API_BASE_URL}/results/upload`, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (response.ok) {
            alert("✅ " + result.message);
            fileInput.value = "";
            document.getElementById('resFileLabel').innerText = "Choose CSV...";
            await loadResults();
        } else {
            alert(`Upload Failed: ${result.error || response.statusText}`);
        }
    } catch (err) {
        console.error("Bulk upload results error:", err);
        alert("An error occurred during bulk upload.");
    } finally {
        btn.innerText = oldText;
        btn.disabled = false;
    }
}

/** ---------------- ANNOUNCEMENTS ---------------- **/

function toggleStudentIdField() {
    const selected = document.querySelector('input[name="annTarget"]:checked').value;
    document.getElementById('annStudentIdGroup').style.display = selected === 'SELECTED' ? 'block' : 'none';
}

async function postAnnouncement(e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.innerText = 'Posting...';

    const targetType = document.querySelector('input[name="annTarget"]:checked').value;
    const data = {
        title: document.getElementById('annTitle').value.trim(),
        message: document.getElementById('annMessage').value.trim(),
        targetType: targetType,
        targetStudentIds: targetType === 'SELECTED' ? document.getElementById('annStudentIds').value.trim() : null
    };

    try {
        const response = await fetch(`${API_BASE_URL}/announcements`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (!response.ok) throw new Error('Failed to post announcement');

        alert('📢 Announcement posted successfully!');
        e.target.reset();
        document.getElementById('annStudentIdGroup').style.display = 'none';
        await loadAnnouncements();
    } catch (err) {
        console.error(err);
        alert('Failed to post announcement.');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Post Announcement';
    }
}

async function loadAnnouncements() {
    try {
        const response = await fetch(`${API_BASE_URL}/announcements`);
        if (!response.ok) throw new Error('Failed to load announcements');
        const announcements = await response.json();

        const tbody = document.getElementById('announcementsTableBody');
        tbody.innerHTML = '';

        if (announcements.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding:1.5rem; color:var(--text-muted);">No announcements yet. Post your first one above!</td></tr>';
            return;
        }

        announcements.forEach(a => {
            const date = a.createdAt ? new Date(a.createdAt).toLocaleString() : '—';
            const target = a.targetType === 'ALL'
                ? '<span style="color:#10b981; font-weight:600;">All Students</span>'
                : `<span style="color:#f59e0b; font-weight:600;">IDs: ${a.targetStudentIds}</span>`;
            const msgPreview = a.message.length > 80 ? a.message.substring(0, 80) + '...' : a.message;

            tbody.innerHTML += `
                <tr>
                    <td>${a.id}</td>
                    <td><strong>${a.title}</strong></td>
                    <td style="font-size:0.85rem; color:var(--text-muted); max-width:300px;">${msgPreview}</td>
                    <td>${target}</td>
                    <td style="font-size:0.8rem;">${date}</td>
                    <td>
                        <button type="button" class="btn-outline" style="border-color:var(--accent-red); color:var(--accent-red); padding:4px 8px; font-size:12px;" onclick="deleteAnnouncement(${a.id})">Delete</button>
                    </td>
                </tr>
            `;
        });
    } catch (err) {
        console.error(err);
    }
}

async function deleteAnnouncement(id) {
    if (!confirm(`Delete Announcement ID: ${id}?`)) return;

    try {
        const response = await fetch(`${API_BASE_URL}/announcements/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Delete failed');
        alert('Announcement deleted.');
        await loadAnnouncements();
    } catch (err) {
        console.error(err);
        alert('Could not delete announcement.');
    }
}
