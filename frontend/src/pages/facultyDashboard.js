/**
 * Faculty Dashboard — ES Module.
 * Handles faculty subjects view, attendance marking (department-filtered), and password change.
 */
import { apiFetch } from '../api.js';
import { requireFaculty, getUserName, getDepartment, logout } from '../auth.js';

let mySubjects = [];

export function initFacultyDashboard() {
    if (!requireFaculty()) return;

    // Display faculty info in topbar
    const name = getUserName() || 'Faculty';
    const dept = getDepartment() || '';
    document.getElementById('facultyNameDisplay').textContent = name;
    document.getElementById('facultyDeptDisplay').textContent = dept;

    // Attach listeners for dynamic attendance loading
    document.getElementById('attendanceDate').addEventListener('change', window.onAttendanceParamsChange);
    document.getElementById('sessionNumber').addEventListener('change', window.onAttendanceParamsChange);

    loadMySubjects();
}

// ======================== TAB SWITCHING ========================

window.switchTab = function (tabId, navEl) {
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.add('hidden'));
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    const target = document.getElementById('tab-' + tabId);
    if (target) {
        target.classList.remove('hidden');
        target.classList.add('active');
    }

    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    if (navEl) navEl.classList.add('active');
};

window.logout = logout;

// ======================== MY SUBJECTS ========================

async function loadMySubjects() {
    try {
        const resp = await apiFetch('/api/attendance/my-subjects');
        mySubjects = await resp.json();

        const grid = document.getElementById('mySubjectsGrid');
        if (mySubjects.length === 0) {
            grid.innerHTML = `
                <div style="text-align: center; padding: 3rem; color: var(--text-muted); grid-column: 1 / -1;">
                    <p style="font-size: 1.2rem; margin-bottom: 0.5rem;">No subjects assigned yet</p>
                    <p style="font-size: 0.9rem;">Contact your administrator to get subjects assigned to you.</p>
                </div>`;
            return;
        }

        grid.innerHTML = mySubjects.map(s => `
            <div class="subject-card">
                <h4>${s.subjectName}</h4>
                <div class="meta">
                    <span>📖 ${s.credits} Credits</span>
                    <span>🎓 Semester ${s.semester}</span>
                </div>
            </div>
        `).join('');

        // Also populate the subject dropdown in attendance tab
        populateSubjectDropdown();
    } catch (err) {
        console.error('Error loading subjects:', err);
    }
}

function populateSubjectDropdown() {
    const select = document.getElementById('filterSubject');
    select.innerHTML = '<option value="">Select Subject</option>';
    mySubjects.forEach(s => {
        select.innerHTML += `<option value="${s.id}" data-semester="${s.semester}">${s.subjectName} (Sem ${s.semester})</option>`;
    });
}

// ======================== ATTENDANCE ========================

window.onSubjectChange = window.onAttendanceParamsChange = async function () {
    const subjectId = document.getElementById('filterSubject').value;
    const date = document.getElementById('attendanceDate').value;
    const sessionNumber = document.getElementById('sessionNumber').value;
    
    if (!subjectId) {
        document.getElementById('studentListContainer').classList.add('hidden');
        document.getElementById('noDataMessage').classList.remove('hidden');
        return;
    }

    // Find the semester from the selected subject
    const selectedOption = document.getElementById('filterSubject').selectedOptions[0];
    const semester = selectedOption.getAttribute('data-semester');

    try {
        // Fetch students by department
        const resp = await apiFetch(`/api/attendance/students-by-semester/${semester}/my-department`);
        const students = await resp.json();

        // Fetch existing attendance records if date is set
        let existingRecords = {};
        if (date && sessionNumber) {
            const recordsResp = await apiFetch(`/api/attendance/records?subjectId=${subjectId}&date=${date}&sessionNumber=${sessionNumber}`);
            if (recordsResp.ok) {
                existingRecords = await recordsResp.json();
            }
        }

        const tableBody = document.getElementById('attendanceTableBody');
        tableBody.innerHTML = '';

        if (students.length === 0) {
            document.getElementById('studentListContainer').classList.add('hidden');
            document.getElementById('noDataMessage').classList.remove('hidden');
            document.getElementById('noDataMessage').textContent = 'No students found in your department for this semester.';
            return;
        }

        students.forEach(student => {
            const existingStatus = existingRecords[student.id];
            
            // Set default present state or the existing state
            const isPresent = existingStatus === 'PRESENT';
            const isAbsent = existingStatus === 'ABSENT';
            
            // If neither is set, we can leave them blank or default to none
            const presentClass = isPresent ? 'active' : '';
            const absentClass = isAbsent ? 'active' : '';

            tableBody.innerHTML += `
                <tr data-student-id="${student.id}">
                    <td>${student.id}</td>
                    <td>${student.name}</td>
                    <td>${student.department}</td>
                    <td>
                        <div class="status-toggle">
                            <button class="status-btn present ${presentClass}" onclick="setStatus(this, 'PRESENT')">Present</button>
                            <button class="status-btn absent ${absentClass}" onclick="setStatus(this, 'ABSENT')">Absent</button>
                        </div>
                    </td>
                </tr>`;
        });

        document.getElementById('studentCountBadge').textContent = `(${students.length} students)`;
        document.getElementById('studentListContainer').classList.remove('hidden');
        document.getElementById('noDataMessage').classList.add('hidden');
    } catch (err) {
        console.error('Error loading attendance data:', err);
    }
};

window.setStatus = function (btn) {
    const parent = btn.parentElement;
    parent.querySelectorAll('.status-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
};

window.setAllAttendance = function (status) {
    document.querySelectorAll('#attendanceTableBody tr').forEach(row => {
        row.querySelectorAll('.status-btn').forEach(b => {
            b.classList.remove('active');
            if (status === 'PRESENT' && b.classList.contains('present')) b.classList.add('active');
            if (status === 'ABSENT' && b.classList.contains('absent')) b.classList.add('active');
        });
    });
};

window.submitAttendance = async function () {
    const subjectId = document.getElementById('filterSubject').value;
    const date = document.getElementById('attendanceDate').value;
    const sessionNumber = document.getElementById('sessionNumber').value;

    if (!subjectId || !date) {
        alert('Please select subject and date.');
        return;
    }

    const records = [];
    document.querySelectorAll('#attendanceTableBody tr').forEach(row => {
        const studentId = row.getAttribute('data-student-id');
        const activeBtn = row.querySelector('.status-btn.active');
        const status = activeBtn && activeBtn.classList.contains('present') ? 'PRESENT' : 'ABSENT';
        records.push({ studentId: parseInt(studentId), status });
    });

    if (records.length === 0) {
        alert('No students to mark attendance for.');
        return;
    }

    try {
        const resp = await apiFetch('/api/attendance/mark', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                subjectId: parseInt(subjectId),
                date,
                sessionNumber: parseInt(sessionNumber),
                records
            })
        });
        if (resp.ok) {
            alert('✅ Attendance marked successfully!');
        } else {
            const err = await resp.json();
            alert('Error: ' + (err.error || 'Failed to mark attendance'));
        }
    } catch (err) {
        alert('Network error. Check console.');
        console.error(err);
    }
};

// ======================== CHANGE PASSWORD ========================

window.changePassword = async function (e) {
    e.preventDefault();

    const currentPassword = document.getElementById('currentPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    const msgEl = document.getElementById('pwChangeMessage');

    if (newPassword !== confirmPassword) {
        msgEl.style.color = '#ef4444';
        msgEl.textContent = '❌ New passwords do not match.';
        return;
    }

    if (newPassword.length < 6) {
        msgEl.style.color = '#ef4444';
        msgEl.textContent = '❌ Password must be at least 6 characters.';
        return;
    }

    try {
        const resp = await apiFetch(`/faculty/change-password?currentPassword=${encodeURIComponent(currentPassword)}&newPassword=${encodeURIComponent(newPassword)}`, {
            method: 'POST'
        });

        if (resp.ok) {
            msgEl.style.color = '#10b981';
            msgEl.textContent = '✅ Password changed successfully!';
            document.getElementById('currentPassword').value = '';
            document.getElementById('newPassword').value = '';
            document.getElementById('confirmPassword').value = '';
        } else {
            const err = await resp.json();
            msgEl.style.color = '#ef4444';
            msgEl.textContent = '❌ ' + (err.error || 'Failed to change password.');
        }
    } catch (err) {
        msgEl.style.color = '#ef4444';
        msgEl.textContent = '❌ Could not connect to server.';
    }
};
