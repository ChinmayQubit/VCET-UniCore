const API_BASE = '/api/attendance';
const SUBJECTS_API = '/subjects/semester';

async function loadStudentsAndSubjects() {
    const semester = document.getElementById('filterSemester').value;
    if (!semester) return;

    try {
        // Load Students
        const respStudents = await fetch(`${API_BASE}/students-by-semester/${semester}`);
        const students = await respStudents.json();

        // Load Subjects
        const respSubjects = await fetch(`${SUBJECTS_API}/${semester}`);
        const subjects = await respSubjects.json();

        // Populate Subjects Dropdown
        const subSelect = document.getElementById('filterSubject');
        subSelect.innerHTML = '<option value="">Select Subject</option>';
        subjects.forEach(s => {
            subSelect.innerHTML += `<option value="${s.id}">${s.subjectName}</option>`;
        });

        // Populate Students Table
        const tableBody = document.getElementById('attendanceTableBody');
        tableBody.innerHTML = '';
        
        students.forEach(student => {
            const row = `
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
                </tr>
            `;
            tableBody.innerHTML += row;
        });

        document.getElementById('studentListContainer').classList.remove('hidden');
        document.getElementById('noDataMessage').classList.add('hidden');

    } catch (err) {
        console.error("Error loading data:", err);
        alert("Failed to load students or subjects.");
    }
}

function setStatus(btn, status) {
    const parent = btn.parentElement;
    parent.querySelectorAll('.status-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
}

function setAllAttendance(status) {
    const rows = document.querySelectorAll('#attendanceTableBody tr');
    rows.forEach(row => {
        const btns = row.querySelectorAll('.status-btn');
        btns.forEach(b => {
            b.classList.remove('active');
            if (status === 'PRESENT' && b.classList.contains('present')) b.classList.add('active');
            if (status === 'ABSENT' && b.classList.contains('absent')) b.classList.add('active');
        });
    });
}

async function submitAttendance() {
    const subjectId = document.getElementById('filterSubject').value;
    const date = document.getElementById('attendanceDate').value;
    const sessionNumber = document.getElementById('sessionNumber').value;

    if (!subjectId || !date) {
        alert("Please select subject and date.");
        return;
    }

    const records = [];
    const rows = document.querySelectorAll('#attendanceTableBody tr');
    
    rows.forEach(row => {
        const studentId = row.getAttribute('data-student-id');
        const status = row.querySelector('.status-btn.active').classList.contains('present') ? 'PRESENT' : 'ABSENT';
        records.push({ studentId: parseInt(studentId), status });
    });

    const payload = {
        subjectId: parseInt(subjectId),
        date: date,
        sessionNumber: parseInt(sessionNumber),
        records: records
    };

    try {
        const resp = await fetch(`${API_BASE}/mark`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
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
}
