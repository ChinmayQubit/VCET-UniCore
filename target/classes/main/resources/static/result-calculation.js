const API_BASE_URL = 'http://localhost:8080';

/** ---------------- ADMIN: UPLOAD RESULT ---------------- **/

const resultForm = document.getElementById('nepResultForm');
if(resultForm) {
    resultForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = e.target.querySelector('button[type="submit"]');
        btn.disabled = true;

        const payload = {
            student: { id: parseInt(document.getElementById('nepStudentId').value, 10) },
            courseCode: document.getElementById('nepCourseCode').value.trim(),
            courseName: document.getElementById('nepCourseName').value.trim(),
            internalMarks: parseFloat(document.getElementById('nepInternal').value),
            externalMarks: parseFloat(document.getElementById('nepExternal').value),
            credits: parseInt(document.getElementById('nepCredits').value, 10),
            semester: parseInt(document.getElementById('nepSemester').value, 10)
        };

        try {
            const response = await fetch(`${API_BASE_URL}/results`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if(!response.ok) throw new Error("Failed to upload result");

            alert("Result uploaded successfully! SGPA/CGPA automations applied.");
            e.target.reset();
        } catch(err) {
            console.error(err);
            alert("Failed to upload result. Ensure Student ID exists.");
        } finally {
            btn.disabled = false;
        }
    });
}


/** ---------------- STUDENT: VIEW MARKSHEET ---------------- **/

// Global state
let allValidResults = [];
let currentStudentId = 1;

async function initResultPage() {
    currentStudentId = localStorage.getItem('studentId') || 1;
    document.getElementById('msStudentId').innerText = currentStudentId;

    try {
        const resultsRes = await fetch(`${API_BASE_URL}/results/student/${currentStudentId}`);
        const resultsData = await resultsRes.json();
        
        // Filter out legacy null rows
        allValidResults = resultsData.filter(r => r.courseCode != null && r.semester > 0);
        
        if(!allValidResults || allValidResults.length === 0) {
            document.getElementById('marksheetTableBody').innerHTML = `<tr><td colspan="10" style="text-align:center; padding:2rem;">No results found. Please ask admin to upload results.</td></tr>`;
            return;
        }
        
        // Get unique semesters sorted ascending
        const semesters = [...new Set(allValidResults.map(r => r.semester))].sort((a,b)=>a-b);
        
        // Build semester selector buttons
        buildSemesterSelector(semesters);
        
        // Show the LATEST semester initially
        const latestSem = semesters[semesters.length - 1];
        renderSemesterMarksheet(latestSem);

    } catch (err) {
        console.error(err);
        document.getElementById('marksheetTableBody').innerHTML = `<tr><td colspan="10" style="color:red; text-align:center;">Error loading results. Is backend running?</td></tr>`;
    }
}

function buildSemesterSelector(semesters) {
    // Check if container already exists
    let selectorDiv = document.getElementById('semesterSelector');
    if (!selectorDiv) {
        selectorDiv = document.createElement('div');
        selectorDiv.id = 'semesterSelector';
        selectorDiv.style.cssText = 'display:flex; gap:0.5rem; margin-bottom:1rem; flex-wrap:wrap;';
        // Insert before the table wrapper
        const tableWrapper = document.querySelector('.marksheet-table-wrapper');
        tableWrapper.parentNode.insertBefore(selectorDiv, tableWrapper);
    }
    selectorDiv.innerHTML = '<span style="font-weight:600; color:#2b6cb0; margin-right:0.5rem; line-height:2;">View Semester:</span>';
    
    semesters.forEach(sem => {
        const btn = document.createElement('button');
        btn.innerText = `Sem ${sem}`;
        btn.className = 'sem-btn';
        btn.id = `semBtn-${sem}`;
        btn.style.cssText = 'padding: 0.4rem 1rem; border: 2px solid #2b6cb0; background: white; color: #2b6cb0; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 0.9rem;';
        btn.onclick = () => renderSemesterMarksheet(sem);
        selectorDiv.appendChild(btn);
    });
}

function renderSemesterMarksheet(semester) {
    // Highlight active button
    document.querySelectorAll('.sem-btn').forEach(b => {
        b.style.background = 'white';
        b.style.color = '#2b6cb0';
    });
    const activeBtn = document.getElementById(`semBtn-${semester}`);
    if (activeBtn) {
        activeBtn.style.background = '#2b6cb0';
        activeBtn.style.color = 'white';
    }
    
    document.getElementById('msSemester').innerText = semester;
    
    const semResults = allValidResults.filter(r => r.semester === semester);
    const tbody = document.getElementById('marksheetTableBody');
    tbody.innerHTML = '';
    
    let sumTotalMarks = 0;
    let sumMaxMarks = 0;
    let sumCredits = 0;
    let sumCreditGrades = 0;
    let hasFail = false;

    semResults.forEach((r, idx) => {
        const intM = r.internalMarks || 0;
        const extM = r.externalMarks || 0;
        const totM = r.totalMarks || (intM + extM);
        const cr = r.credits || 0;
        const point = r.gradePoint || 0;
        const cxg = r.creditGrade || (cr * point);
        const grade = r.grade || 'F';
        
        // Max marks = 40 internal max + 60 external max = 100 per subject for MU NEP
        const maxM = intM <= 40 ? 100 : 150;

        if(grade === 'F') hasFail = true;
        
        sumTotalMarks += totM;
        sumMaxMarks += maxM;
        sumCredits += cr;
        sumCreditGrades += cxg;

        tbody.innerHTML += `
            <tr>
                <td>${idx + 1}</td>
                <td><strong>${r.courseCode || '--'}</strong><br><small>Int:${intM} Ext:${extM}</small></td>
                <td>${r.courseName || '--'}</td>
                <td>${intM.toFixed(2)}</td>
                <td>${extM.toFixed(2)}</td>
                <td><strong>${totM.toFixed(2)}</strong></td>
                <td>${cr.toFixed(2)}</td>
                <td><strong>${grade}</strong></td>
                <td>${point.toFixed(2)}</td>
                <td>${cxg.toFixed(2)}</td>
            </tr>
        `;
    });
    
    // Footer totals
    document.getElementById('msTotalMarks').innerText = `${sumTotalMarks.toFixed(1)} / ${sumMaxMarks.toFixed(1)}`;
    document.getElementById('msTotalCredits').innerText = sumCredits.toFixed(1);
    document.getElementById('msTotalCreditGrades').innerText = sumCreditGrades.toFixed(2);
    
    // SGPA for this semester
    const sgpa = sumCredits > 0 ? (sumCreditGrades / sumCredits) : 0;
    document.getElementById('msSgpa').innerText = sgpa.toFixed(2);
    
    // Fetch overall CGPA from backend
    fetch(`${API_BASE_URL}/results/cgpa/${currentStudentId}`)
        .then(res => res.json())
        .then(cgpaNum => {
            cgpaNum = parseFloat(cgpaNum);
            let percent = cgpaNum >= 0.75 ? (cgpaNum - 0.75) * 10 : 0;
            document.getElementById('msCgpa').innerText = cgpaNum.toFixed(2);
            document.getElementById('msPercentage').innerText = percent.toFixed(2);
        });
    
    // Result status
    const statusBox = document.getElementById('msResultStatus');
    if(hasFail) {
        statusBox.innerText = "Result: FAIL";
        statusBox.style.background = "#e53e3e";
    } else {
        statusBox.innerText = "Result: PASS";
        statusBox.style.background = "#38a169";
    }

    // AI Analytics - correct endpoints and response parsing
    fetch(`${API_BASE_URL}/analysis/ai-cgpa/${currentStudentId}`)
        .then(res => res.json())
        .then(data => {
            const val = data && data.predictedFinalCGPA !== undefined ? data.predictedFinalCGPA : null;
            document.getElementById('msAiCgpa').innerText = val !== null ? parseFloat(val).toFixed(2) : '--';
        })
        .catch(() => document.getElementById('msAiCgpa').innerText = '--');
    
    fetch(`${API_BASE_URL}/analysis/target/${currentStudentId}`)
        .then(res => res.json())
        .then(data => {
            const val = data && data.aiTargetSGPA !== undefined ? data.aiTargetSGPA : null;
            document.getElementById('msTargetSgpa').innerText = val !== null ? parseFloat(val).toFixed(2) : '--';
        })
        .catch(() => document.getElementById('msTargetSgpa').innerText = '--');
}
