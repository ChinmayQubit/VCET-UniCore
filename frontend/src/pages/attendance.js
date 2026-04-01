/**
 * Attendance page — ES Module version.
 */
import { apiFetch } from '../api.js';
import { requireAdmin } from '../auth.js';

export function initAttendancePage() {
    if (!requireAdmin()) return;
}

window.loadStudentsAndSubjects = async function () {
    const semester = document.getElementById('filterSemester').value;
    if (!semester) return;

    try {
        const respStudents = await apiFetch(`/api/attendance/students-by-semester/${semester}`);
        const students = await respStudents.json();

        const respSubjects = await apiFetch(`/subjects/semester/${semester}`);
        const subjects = await respSubjects.json();

        const subSelect = document.getElementById('filterSubject');
        subSelect.innerHTML = '<option value="">Select Subject</option>';
        subjects.forEach(s => {
            subSelect.innerHTML += `<option value="${s.id}">${s.subjectName}</option>`;
        });

        const tableBody = document.getElementById('attendanceTableBody');
        tableBody.innerHTML = '';
        students.forEach(student => {
            tableBody.innerHTML += `
                <tr data-student-id="${student.id}">
                    <td>${student.id}</td>
                    <td>${student.name}</td>
                    <td>${student.department}</td>
                    <td>
                        <div class="status-toggle">
                            <button class="status-btn present active" onclick="setStatus(this, 'PRESENT')">Present</button>
                            <button class="status-btn absent" onclick="setStatus(this, 'ABSENT')">Absent</button>
                        </div>
                    </td>
                </tr>`;
        });

        document.getElementById('studentListContainer').classList.remove('hidden');
        document.getElementById('noDataMessage').classList.add('hidden');
    } catch (err) {
        console.error("Error loading data:", err);
        alert("Failed to load students or subjects.");
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
        alert("Please select subject and date.");
        return;
    }

    const records = [];
    document.querySelectorAll('#attendanceTableBody tr').forEach(row => {
        const studentId = row.getAttribute('data-student-id');
        const status = row.querySelector('.status-btn.active').classList.contains('present') ? 'PRESENT' : 'ABSENT';
        records.push({ studentId: parseInt(studentId), status });
    });

    try {
        const resp = await apiFetch('/api/attendance/mark', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                subjectId: parseInt(subjectId),
                date, sessionNumber: parseInt(sessionNumber), records
            })
        });
        if (resp.ok) {
            alert("Attendance marked successfully!");
        } else {
            const err = await resp.json();
            alert("Error: " + (err.error || "Failed to mark attendance"));
        }
    } catch (err) {
        alert("Network error. Check console.");
    }
};
