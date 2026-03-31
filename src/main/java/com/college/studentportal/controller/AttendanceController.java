package com.college.studentportal.controller;

import com.college.studentportal.model.Attendance;
import com.college.studentportal.model.Student;
import com.college.studentportal.repository.StudentRepository;
import com.college.studentportal.service.AttendanceService;
import org.springframework.http.ResponseEntity;
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

    public AttendanceController(AttendanceService attendanceService, StudentRepository studentRepository) {
        this.attendanceService = attendanceService;
        this.studentRepository = studentRepository;
    }

    @PostMapping("/mark")
    public ResponseEntity<?> markAttendance(@RequestBody AttendanceRequest request) {
        try {
            Map<Long, Attendance.AttendanceStatus> studentStatuses = new HashMap<>();
            
            for (AttendanceRecord record : request.getRecords()) {
                studentStatuses.put(record.getStudentId(), record.getStatus());
            }

            attendanceService.markAttendance(
                    request.getSubjectId(),
                    request.getDate() != null ? request.getDate() : LocalDate.now(),
                    request.getSessionNumber() != null ? request.getSessionNumber() : 1,
                    studentStatuses
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

    @GetMapping("/students-by-semester/{semester}")
    public ResponseEntity<List<Student>> getStudentsBySemester(@PathVariable int semester) {
        return ResponseEntity.ok(studentRepository.findBySemester(semester));
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
