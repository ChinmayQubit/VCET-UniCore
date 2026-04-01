/**
 * Admin Dashboard — ES Module version.
 */
import { apiFetch, apiGet, apiUpload, apiDelete, apiPut, apiPost } from '../api.js';
import { requireAdmin, logout as doLogout } from '../auth.js';

let studentsList = [];
let subjectsList = [];
let facultyList = [];
let adminChartInstance = null;

export async function initAdminDashboard() {
    if (!requireAdmin()) return;
    await loadStudents();
    await loadFaculty();
    await loadSubjects();
    await loadResults();
    hookupAdminForms();
}

// ======================== TAB NAVIGATION ========================
window.switchTab = function (tabId, navEl) {
    document.querySelectorAll('.tab-pane').forEach(el => { el.classList.remove('active'); el.classList.add('hidden'); });
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(el => el.classList.remove('active'));
    const target = document.getElementById(`tab-${tabId}`);
    if (target) { target.classList.remove('hidden'); target.classList.add('active'); }
    if (navEl?.classList) navEl.classList.add('active');
};

// ======================== STUDENT MANAGEMENT ========================
async function loadStudents() {
    try {
        const response = await apiFetch('/students');
        if (!response.ok) throw new Error("Failed");
        studentsList = await response.json();
        const tbody = document.getElementById('adminStudentsTableBody');
        tbody.innerHTML = '';
        const cgpas = [];
        for (const s of studentsList) {
            try {
                const cgpaRes = await apiFetch(`/results/cgpa/${s.id}`);
                if (cgpaRes.ok) { const v = Number(await cgpaRes.json()); cgpas.push({ id: s.id, cgpa: v > 0 ? v : null }); }
                else cgpas.push({ id: s.id, cgpa: null });
            } catch (_) { cgpas.push({ id: s.id, cgpa: null }); }
        }
        studentsList.forEach(s => {
            const row = cgpas.find(c => c.id === s.id);
            const cg = row?.cgpa != null ? row.cgpa.toFixed(2) : '—';
            tbody.innerHTML += `<tr><td>${s.id}</td><td><strong>${s.name}</strong></td><td>${s.email}</td><td>${s.department}</td><td>Sem ${s.semester}</td><td style="font-size:0.85rem;color:var(--text-muted);">${cg}</td><td><button type="button" class="btn-outline" style="padding:4px 8px;font-size:12px;margin-right:4px;" onclick="openEditStudent(${s.id})">Edit</button><button type="button" class="btn-outline" style="border-color:var(--accent-orange);color:var(--accent-orange);padding:4px 8px;font-size:12px;margin-right:4px;" onclick="resetStudentPassword(${s.id})">Reset Pwd</button><button type="button" class="btn-outline" style="border-color:var(--accent-red);color:var(--accent-red);padding:4px 8px;font-size:12px;" onclick="deleteStudent(${s.id})">Delete</button></td></tr>`;
        });
        document.getElementById('adminTotalStudents').innerText = studentsList.length;
        let totalCgpa = 0, validCount = 0, placementReady = 0, atRisk = 0;
        for (const row of cgpas) {
            if (row.cgpa != null && row.cgpa > 0) { totalCgpa += row.cgpa; validCount++; if (row.cgpa >= 7) placementReady++; if (row.cgpa < 6.5) atRisk++; }
        }
        document.getElementById('adminAvgCgpa').innerText = validCount > 0 ? (totalCgpa / validCount).toFixed(2) : '—';
        document.getElementById('adminPlacementReady').innerText = String(placementReady);
        document.getElementById('adminRiskCount').innerText = String(atRisk);
    } catch (err) { console.error(err); alert("Error loading students."); }
}

window.addStudent = async function (e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    const studentData = { name: document.getElementById('studName').value.trim(), email: document.getElementById('studEmail').value.trim(), department: document.getElementById('studDept').value.trim(), semester: parseInt(document.getElementById('studSem').value, 10) };
    try {
        const response = await apiPost('/students', studentData);
        if (!response.ok) throw new Error("Failed");
        const result = await response.json();
        alert(`✅ Student added! Claim Code: ${result.claimToken}`);
        e.target.reset(); await loadStudents();
    } catch (err) { alert("Failed to add student."); } finally { btn.disabled = false; }
};

window.deleteStudent = async function (id) {
    if (!confirm(`Delete Student ID: ${id}?`)) return;
    try { const r = await apiDelete(`/students/${id}`); if (!r.ok) throw new Error(); alert("Deleted!"); await loadStudents(); }
    catch (err) { alert("Failed to delete."); }
};

window.openEditStudent = function (id) {
    const s = studentsList.find(x => x.id === id); if (!s) return;
    document.getElementById('editStudId').value = s.id;
    document.getElementById('editStudName').value = s.name || '';
    document.getElementById('editStudEmail').value = s.email || '';
    document.getElementById('editStudDept').value = s.department || '';
    document.getElementById('editStudSem').value = s.semester;
    document.getElementById('editStudentModal').classList.remove('hidden');
};
window.closeEditStudentModal = () => document.getElementById('editStudentModal').classList.add('hidden');

window.submitEditStudent = async function (e) {
    e.preventDefault();
    const id = document.getElementById('editStudId').value;
    const body = { name: document.getElementById('editStudName').value.trim(), email: document.getElementById('editStudEmail').value.trim(), department: document.getElementById('editStudDept').value.trim(), semester: parseInt(document.getElementById('editStudSem').value, 10) };
    try { const r = await apiPut(`/students/${id}`, body); if (!r.ok) throw new Error(); window.closeEditStudentModal(); await loadStudents(); }
    catch (err) { alert('Could not save.'); }
};

window.resetStudentPassword = async function (id) {
    if (!confirm(`Reset password for Student ID: ${id}?`)) return;
    try { const r = await apiFetch(`/students/${id}/reset-password`, { method: 'POST' }); if (!r.ok) throw new Error(); const d = await r.json(); alert(`🔒 Reset! New Claim Code: ${d.claimToken}`); }
    catch (err) { alert('Could not reset.'); }
};

// ======================== FACULTY MANAGEMENT ========================
window.loadFaculty = async function () {
    try {
        const response = await apiFetch('/faculty');
        if (!response.ok) throw new Error("Failed");
        facultyList = await response.json();
        const tbody = document.getElementById('adminFacultyTableBody');
        tbody.innerHTML = '';
        facultyList.forEach(fac => {
            tbody.innerHTML += `<tr><td>${fac.id}</td><td><strong>${fac.name}</strong></td><td>${fac.email}</td><td>${fac.department}</td><td><button type="button" class="btn-outline" style="padding:4px 8px;font-size:12px;margin-right:4px;" onclick="openBulkAssignModal(${fac.id})">Assign Subjects</button><button type="button" class="btn-outline" style="padding:4px 8px;font-size:12px;margin-right:4px;" onclick="openEditFaculty(${fac.id})">Edit</button><button type="button" class="btn-outline" style="border-color:var(--accent-orange);color:var(--accent-orange);padding:4px 8px;font-size:12px;margin-right:4px;" onclick="resetFacultyPassword(${fac.id})">Reset Pwd</button><button type="button" class="btn-outline" style="border-color:var(--accent-red);color:var(--accent-red);padding:4px 8px;font-size:12px;" onclick="deleteFaculty(${fac.id})">Delete</button></td></tr>`;
        });
        
        // Refresh faculty dropdowns in subject modals
        const facDropdowns = [document.getElementById('subFacId'), document.getElementById('editSubFacId')];
        facDropdowns.forEach(dd => {
            if (!dd) return;
            const currentVal = dd.value;
            dd.innerHTML = '<option value="">-- Unassigned --</option>';
            facultyList.forEach(f => {
                dd.innerHTML += `<option value="${f.id}" ${currentVal == f.id ? 'selected' : ''}>${f.name} (${f.department})</option>`;
            });
        });

    } catch (err) { console.error(err); alert("Error loading faculty."); }
};

window.addFaculty = async function (e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    const data = { name: document.getElementById('facName').value.trim(), email: document.getElementById('facEmail').value.trim(), department: document.getElementById('facDept').value.trim() };
    try {
        const response = await apiPost('/faculty', data);
        if (!response.ok) throw new Error("Failed");
        const result = await response.json();
        alert(`✅ Faculty added!\nTemporary Password: ${result.temporaryPassword}\n(Please copy this down)`);
        e.target.reset(); await loadFaculty();
    } catch (err) { alert("Failed to add faculty."); } finally { btn.disabled = false; }
};

window.deleteFaculty = async function (id) {
    if (!confirm(`Delete Faculty ID: ${id}?`)) return;
    try { const r = await apiDelete(`/faculty/${id}`); if (!r.ok) throw new Error(); alert("Deleted!"); await loadFaculty(); }
    catch (err) { alert("Failed to delete."); }
};

window.openEditFaculty = function (id) {
    const fac = facultyList.find(x => x.id === id); if (!fac) return;
    document.getElementById('editFacId').value = fac.id;
    document.getElementById('editFacName').value = fac.name || '';
    document.getElementById('editFacEmail').value = fac.email || '';
    document.getElementById('editFacDept').value = fac.department || '';
    document.getElementById('editFacultyModal').classList.remove('hidden');
};
window.closeEditFacultyModal = () => document.getElementById('editFacultyModal').classList.add('hidden');

window.submitEditFaculty = async function (e) {
    e.preventDefault();
    const id = document.getElementById('editFacId').value;
    const body = { name: document.getElementById('editFacName').value.trim(), email: document.getElementById('editFacEmail').value.trim(), department: document.getElementById('editFacDept').value.trim() };
    try { const r = await apiPut(`/faculty/${id}`, body); if (!r.ok) throw new Error(); window.closeEditFacultyModal(); await loadFaculty(); }
    catch (err) { alert('Could not save.'); }
};

window.resetFacultyPassword = async function (id) {
    if (!confirm(`Reset password for Faculty ID: ${id}?`)) return;
    try { const r = await apiFetch(`/faculty/${id}/reset-password`, { method: 'POST' }); if (!r.ok) throw new Error(); const d = await r.json(); alert(`🔒 Reset! New Temporary Password: ${d.temporaryPassword}`); }
    catch (err) { alert('Could not reset.'); }
};

// ======================== SUBJECT MANAGEMENT ========================
window.addSubject = async function (e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    const data = { subjectName: document.getElementById('subName').value.trim(), credits: parseInt(document.getElementById('subCredits').value, 10), semester: parseInt(document.getElementById('subSem').value, 10) };
    const facIdVal = document.getElementById('subFacId').value;
    
    try { 
        const r = await apiPost('/subjects', data); 
        if (!r.ok) throw new Error(); 
        
        const createdSubject = await r.json();
        
        // Assign faculty immediately if selected
        if (facIdVal) {
            await apiPut(`/subjects/${createdSubject.id}/assign-faculty`, { facultyId: parseInt(facIdVal, 10) });
        }
        
        alert("Subject added!"); 
        e.target.reset(); 
        await loadSubjects(); 
    }
    catch (err) { alert("Failed."); } finally { btn.disabled = false; }
};

async function loadSubjects() {
    try {
        const response = await apiFetch('/subjects');
        if (!response.ok) throw new Error();
        subjectsList = await response.json();
        const tbody = document.getElementById('adminSubjectsTableBody');
        tbody.innerHTML = '';
        subjectsList.forEach(sub => {
            const facName = sub.faculty ? sub.faculty.name : '<span style="color:var(--text-muted);">Unassigned</span>';
            tbody.innerHTML += `<tr><td>${sub.id}</td><td><strong>${sub.subjectName}</strong></td><td>${sub.credits}</td><td>Sem ${sub.semester}</td><td>${facName}</td><td><button type="button" class="btn-outline" style="padding:4px 8px;font-size:12px;margin-right:4px;" onclick="openAssignFaculty(${sub.id})">Assign Fac</button><button type="button" class="btn-outline" style="padding:4px 8px;font-size:12px;margin-right:4px;" onclick="openEditSubject(${sub.id})">Edit</button><button type="button" class="btn-outline" style="border-color:var(--accent-red);color:var(--accent-red);padding:4px 8px;font-size:12px;" onclick="deleteSubject(${sub.id})">Delete</button></td></tr>`;
        });
    } catch (err) { console.error(err); }
}

window.openEditSubject = function (id) {
    const sub = subjectsList.find(x => x.id === id); if (!sub) return;
    document.getElementById('editSubId').value = sub.id;
    document.getElementById('editSubName').value = sub.subjectName || '';
    document.getElementById('editSubCredits').value = sub.credits;
    document.getElementById('editSubSem').value = sub.semester;
    document.getElementById('editSubFacId').value = sub.faculty ? sub.faculty.id : '';
    document.getElementById('editSubjectModal').classList.remove('hidden');
};
window.closeEditSubjectModal = () => document.getElementById('editSubjectModal').classList.add('hidden');

window.submitEditSubject = async function (e) {
    e.preventDefault();
    const id = document.getElementById('editSubId').value;
    const facIdVal = document.getElementById('editSubFacId').value;
    const body = { subjectName: document.getElementById('editSubName').value.trim(), credits: parseInt(document.getElementById('editSubCredits').value, 10), semester: parseInt(document.getElementById('editSubSem').value, 10) };
    
    try { 
        const r = await apiPut(`/subjects/${id}`, body); 
        if (!r.ok) throw new Error(); 
        
        // Ensure faculty gets assigned or unassigned
        const facId = facIdVal ? parseInt(facIdVal, 10) : null;
        await apiPut(`/subjects/${id}/assign-faculty`, { facultyId: facId });
        
        window.closeEditSubjectModal(); 
        await loadSubjects(); 
    }
    catch (err) { alert('Could not save.'); }
};

window.deleteSubject = async function (id) {
    if (!confirm(`Delete Subject ID: ${id}?`)) return;
    try { const r = await apiDelete(`/subjects/${id}`); if (!r.ok) throw new Error(); alert("Deleted!"); await loadSubjects(); }
    catch (err) { alert("Failed to delete."); }
};

window.openAssignFaculty = function (id) {
    const sub = subjectsList.find(x => x.id === id); if (!sub) return;
    document.getElementById('assignSubId').value = sub.id;
    document.getElementById('assignSubName').innerText = sub.subjectName + ' (Sem ' + sub.semester + ')';
    
    const sel = document.getElementById('assignFacId');
    sel.innerHTML = '<option value="">-- Unassign Faculty --</option>';
    facultyList.forEach(f => {
        const isSelected = sub.faculty && sub.faculty.id === f.id ? 'selected' : '';
        sel.innerHTML += `<option value="${f.id}" ${isSelected}>${f.name} (${f.department})</option>`;
    });
    
    document.getElementById('assignFacultyModal').classList.remove('hidden');
};
window.closeAssignFacultyModal = () => document.getElementById('assignFacultyModal').classList.add('hidden');

window.submitAssignFaculty = async function (e) {
    e.preventDefault();
    const id = document.getElementById('assignSubId').value;
    const facIdVal = document.getElementById('assignFacId').value;
    const facId = facIdVal ? parseInt(facIdVal, 10) : null;
    try {
        const r = await apiPut(`/subjects/${id}/assign-faculty`, { facultyId: facId });
        if (!r.ok) throw new Error();
        window.closeAssignFacultyModal();
        await loadSubjects();
    } catch (err) { alert('Could not assign faculty.'); }
};

// ======================== BULK ASSIGN FACULTY SUBJECTS ========================
window.openBulkAssignModal = function (facultyId) {
    const fac = facultyList.find(x => x.id === facultyId); if (!fac) return;
    document.getElementById('bulkAssignFacId').value = fac.id;
    document.getElementById('bulkAssignFacName').innerText = "Faculty: " + fac.name + " (" + fac.department + ")";
    
    const container = document.getElementById('bulkAssignCheckboxes');
    container.innerHTML = '';
    
    if (subjectsList.length === 0) {
        container.innerHTML = '<p style="color:var(--text-muted); font-size:0.9rem;">No subjects available to assign.</p>';
    } else {
        subjectsList.forEach(sub => {
            const isAssigned = sub.faculty && sub.faculty.id === fac.id;
            const assignedToOther = sub.faculty && sub.faculty.id !== fac.id;
            const disabledText = assignedToOther ? `(Currently: ${sub.faculty.name})` : '';
            
            container.innerHTML += `
                <div style="display:flex; align-items:center; gap:10px; padding:6px 0; border-bottom:1px solid rgba(255,255,255,0.05);">
                    <input type="checkbox" id="bulk-sub-${sub.id}" value="${sub.id}" class="bulk-sub-chk" ${isAssigned ? 'checked' : ''}>
                    <label for="bulk-sub-${sub.id}" style="color: ${assignedToOther ? 'var(--text-muted)' : 'inherit'};">
                        ${sub.subjectName} (Sem ${sub.semester}) <span style="font-size:0.8rem;">${disabledText}</span>
                    </label>
                </div>
            `;
        });
    }
    
    document.getElementById('bulkAssignSubjectsModal').classList.remove('hidden');
};
window.closeBulkAssignModal = () => document.getElementById('bulkAssignSubjectsModal').classList.add('hidden');

window.submitBulkAssign = async function (e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    
    const facId = parseInt(document.getElementById('bulkAssignFacId').value, 10);
    const checkboxes = Array.from(document.querySelectorAll('.bulk-sub-chk'));
    
    try {
        // We will make PUT requests for each subject whose state has changed.
        for (const chk of checkboxes) {
            const subId = parseInt(chk.value, 10);
            const sub = subjectsList.find(s => s.id === subId);
            const initiallyAssigned = sub.faculty && sub.faculty.id === facId;
            const currentlyChecked = chk.checked;
            
            if (currentlyChecked && !initiallyAssigned) {
                // Assign to this faculty
                await apiPut(`/subjects/${subId}/assign-faculty`, { facultyId: facId });
            } else if (!currentlyChecked && initiallyAssigned) {
                // Unassign from this faculty
                await apiPut(`/subjects/${subId}/assign-faculty`, { facultyId: null });
            }
        }
        alert('Subjects assigned successfully!');
        window.closeBulkAssignModal();
        await loadSubjects(); // Refresh lists
    } catch (err) {
        alert('Could not update all subject assignments.');
    } finally {
        btn.disabled = false;
    }
};

// ======================== RESULTS ========================
async function loadResults() {
    try {
        const response = await apiFetch('/results');
        if (!response.ok) throw new Error();
        const resultsList = await response.json();
        const tbody = document.getElementById('adminResultsTableBody');
        tbody.innerHTML = '';
        resultsList.slice().reverse().slice(0, 50).forEach(res => {
            const sid = res.student ? res.student.id : 'N/A';
            const cn = res.courseName || (res.subject ? res.subject.subjectName : 'N/A');
            const gp = res.gradePoint || '0';
            tbody.innerHTML += `<tr><td><strong>#${sid}</strong></td><td>${cn}</td><td>Sem ${res.semester}</td><td>${res.totalMarks || '-'}</td><td><span class="badge ${gp > 6 ? 'badge-strong' : 'badge-weak'}">${gp}</span></td><td>${res.creditGrade || '-'}</td></tr>`;
        });
    } catch (err) { console.error(err); }
}

// ======================== UTILITIES ========================
function hookupAdminForms() {
    document.getElementById('addStudentForm')?.addEventListener('submit', window.addStudent);
    document.getElementById('addFacultyForm')?.addEventListener('submit', window.addFaculty);
    document.getElementById('addSubjectForm')?.addEventListener('submit', window.addSubject);
    document.getElementById('editStudentForm')?.addEventListener('submit', window.submitEditStudent);
    document.getElementById('editFacultyForm')?.addEventListener('submit', window.submitEditFaculty);
    document.getElementById('editSubjectForm')?.addEventListener('submit', window.submitEditSubject);
}

window.openStudentPreview = function (studentId) {
    localStorage.setItem('studentId', String(studentId));
    window.open('/student-dashboard.html', '_blank');
};

window.logout = doLogout;

window.loadAnalytics = async function () {
    const tbody = document.getElementById('analyticsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:1rem;">Loading...</td></tr>';
    const studRes = await apiFetch('/students');
    const students = await studRes.json();
    const studentData = [];
    for (const s of students) {
        try { const r = await apiFetch(`/results/cgpa/${s.id}`); const c = r.ok ? await r.json() : 0; studentData.push({ ...s, cgpa: parseFloat(c) || 0 }); }
        catch (_) { studentData.push({ ...s, cgpa: 0 }); }
    }
    studentData.sort((a, b) => b.cgpa - a.cgpa);
    tbody.innerHTML = '';
    studentData.forEach((s, idx) => {
        let badge = '';
        if (s.cgpa >= 7.5) badge = '<span style="color:#10b981;font-weight:600;">✓ Eligible</span>';
        else if (s.cgpa >= 6.5) badge = '<span style="color:#f59e0b;font-weight:600;">⚠ Check Cutoff</span>';
        else if (s.cgpa > 0) badge = '<span style="color:#ef4444;font-weight:600;">✗ Below Cutoff</span>';
        else badge = '<span style="color:#94a3b8;">No Results</span>';
        const rank = idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : `#${idx + 1}`;
        tbody.innerHTML += `<tr style="cursor:pointer;" onclick="openStudentPreview(${s.id})"><td><strong>${rank}</strong></td><td><strong>${s.name}</strong></td><td>${s.department}</td><td><strong style="font-size:1.1rem;color:${s.cgpa >= 7 ? '#10b981' : '#ef4444'}">${s.cgpa > 0 ? s.cgpa.toFixed(2) : '--'}</strong></td><td>${badge}</td></tr>`;
    });
    const ctx = document.getElementById('adminDeptChart');
    if (!ctx) return;
    if (adminChartInstance) adminChartInstance.destroy();
    adminChartInstance = new Chart(ctx, {
        type: 'bar', data: { labels: studentData.map(s => s.name), datasets: [{ label: 'CGPA', data: studentData.map(s => s.cgpa), backgroundColor: studentData.map(s => s.cgpa >= 8 ? '#10b981' : s.cgpa >= 7 ? '#3b82f6' : s.cgpa >= 6 ? '#f59e0b' : '#ef4444'), borderRadius: 8 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { min: 0, max: 10, grid: { color: 'rgba(255,255,255,0.05)' } }, x: { grid: { display: false } } } }
    });
};

window.bulkUploadStudents = async function () {
    const fileInput = document.getElementById('studentsCsvFile');
    if (!fileInput.files.length) { alert("Select a CSV file first!"); return; }
    const formData = new FormData(); formData.append("file", fileInput.files[0]);
    try { const r = await apiUpload('/students/upload', formData); const result = await r.json(); if (r.ok) { alert("✅ " + result.message); fileInput.value = ""; await loadStudents(); } else { alert(`Upload Failed: ${result.error}`); } }
    catch (err) { alert("Error during upload."); }
};

window.bulkUploadResults = async function () {
    const fileInput = document.getElementById('resultsCsvFile');
    if (!fileInput.files.length) { alert("Select a CSV file first!"); return; }
    const formData = new FormData(); formData.append("file", fileInput.files[0]);
    try { const r = await apiUpload('/results/upload', formData); const result = await r.json(); if (r.ok) { alert("✅ " + result.message); fileInput.value = ""; await loadResults(); } else { alert(`Upload Failed: ${result.error}`); } }
    catch (err) { alert("Error during upload."); }
};

// ======================== ANNOUNCEMENTS ========================
window.toggleStudentIdField = function () {
    const selected = document.querySelector('input[name="annTarget"]:checked').value;
    document.getElementById('annStudentIdGroup').style.display = selected === 'SELECTED' ? 'block' : 'none';
};

window.postAnnouncement = async function (e) {
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
        const response = await apiPost('/announcements', data);
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
};

window.loadAnnouncements = async function () {
    try {
        const response = await apiFetch('/announcements');
        if (!response.ok) throw new Error('Failed to load announcements');
        const announcements = await response.json();

        const tbody = document.getElementById('announcementsTableBody');
        if (!tbody) return;
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
};

window.deleteAnnouncement = async function (id) {
    if (!confirm(`Delete Announcement ID: ${id}?`)) return;

    try {
        const response = await apiDelete(`/announcements/${id}`);
        if (!response.ok) throw new Error('Delete failed');
        alert('Announcement deleted.');
        await loadAnnouncements();
    } catch (err) {
        console.error(err);
        alert('Could not delete announcement.');
    }
};
