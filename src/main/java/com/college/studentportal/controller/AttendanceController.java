package com.college.studentportal.controller;

import com.college.studentportal.model.Attendance;
import com.college.studentportal.model.Faculty;
import com.college.studentportal.model.Student;
import com.college.studentportal.model.Subject;
import com.college.studentportal.repository.FacultyRepository;
import com.college.studentportal.repository.StudentRepository;
import com.college.studentportal.repository.SubjectRepository;
import com.college.studentportal.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final com.college.studentportal.repository.AttendanceRepository attendanceRepository;

    public AttendanceController(AttendanceService attendanceService,
                                StudentRepository studentRepository,
                                SubjectRepository subjectRepository,
                                FacultyRepository facultyRepository,
                                com.college.studentportal.repository.AttendanceRepository attendanceRepository) {
        this.attendanceService = attendanceService;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Mark attendance — only FACULTY can call this.
     * Validates that the subject is assigned to the calling faculty.
     */
    @PostMapping("/mark")
    public ResponseEntity<?> markAttendance(@RequestBody AttendanceRequest request, Authentication auth) {
        try {
            // Get the faculty from the JWT userId
            Long facultyId = (Long) auth.getDetails();
            Faculty faculty = facultyRepository.findById(facultyId)
                    .orElseThrow(() -> new RuntimeException("Faculty not found"));

            // Validate that this subject is assigned to this faculty
            Subject subject = subjectRepository.findById(request.getSubjectId())
                    .orElseThrow(() -> new RuntimeException("Subject not found"));

            if (subject.getFaculty() == null || !subject.getFaculty().getId().equals(facultyId)) {
                return ResponseEntity.status(403).body(Map.of("error", "You are not assigned to this subject."));
            }

            Map<Long, Attendance.AttendanceStatus> studentStatuses = new HashMap<>();
            for (AttendanceRecord record : request.getRecords()) {
                studentStatuses.put(record.getStudentId(), record.getStatus());
            }

            attendanceService.markAttendance(
                    request.getSubjectId(),
                    request.getDate() != null ? request.getDate() : LocalDate.now(),
                    request.getSessionNumber() != null ? request.getSessionNumber() : 1,
                    studentStatuses,
                    faculty
            );

            return ResponseEntity.ok(Map.of("message", "Attendance marked successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/summary/{studentId}")
    public ResponseEntity<?> getSummary(@PathVariable Long studentId) {
        return ResponseEntity.ok(attendanceService.getStudentAttendanceSummary(studentId));
    }

    /**
     * Get students by semester — used by admin.
     */
    @GetMapping("/students-by-semester/{semester}")
    public ResponseEntity<List<Student>> getStudentsBySemester(@PathVariable int semester) {
        return ResponseEntity.ok(studentRepository.findBySemester(semester));
    }

    /**
     * Get students by semester filtered by the calling faculty's department.
     */
    @GetMapping("/students-by-semester/{semester}/my-department")
    public ResponseEntity<?> getStudentsBySemesterAndDepartment(@PathVariable int semester, Authentication auth) {
        Long facultyId = (Long) auth.getDetails();
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new RuntimeException("Faculty not found"));

        List<Student> students = studentRepository.findBySemesterAndDepartment(semester, faculty.getDepartment());
        return ResponseEntity.ok(students);
    }

    /**
     * Get subjects assigned to the calling faculty.
     */
    @GetMapping("/my-subjects")
    public ResponseEntity<?> getMySubjects(Authentication auth) {
        Long facultyId = (Long) auth.getDetails();
        List<Subject> subjects = subjectRepository.findByFacultyId(facultyId);
        return ResponseEntity.ok(subjects);
    }

    /**
     * Get records for a specific session.
     */
    @GetMapping("/records")
    public ResponseEntity<?> getRecords(
            @RequestParam Long subjectId,
            @RequestParam String date,
            @RequestParam Integer sessionNumber,
            Authentication auth) {
        // Find existing attendance
        List<Attendance> attendances = attendanceRepository.findBySubjectIdAndDateAndSessionNumber(subjectId, LocalDate.parse(date), sessionNumber);
        
        // Map to studentId -> STATUS 
        Map<Long, String> recordsMap = new HashMap<>();
        for (Attendance a : attendances) {
            recordsMap.put(a.getStudent().getId(), a.getStatus().name());
        }
        
        return ResponseEntity.ok(recordsMap);
    }

    // DTOs for the request
    public static class AttendanceRequest {
        private Long subjectId;
        private LocalDate date;
        private Integer sessionNumber;
        private List<AttendanceRecord> records;

        public Long getSubjectId() { return subjectId; }
        public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public Integer getSessionNumber() { return sessionNumber; }
        public void setSessionNumber(Integer sessionNumber) { this.sessionNumber = sessionNumber; }

        public List<AttendanceRecord> getRecords() { return records; }
        public void setRecords(List<AttendanceRecord> records) { this.records = records; }
    }

    public static class AttendanceRecord {
        private Long studentId;
        private Attendance.AttendanceStatus status;

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }

        public Attendance.AttendanceStatus getStatus() { return status; }
        public void setStatus(Attendance.AttendanceStatus status) { this.status = status; }
    }
}
