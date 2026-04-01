package com.college.studentportal.service;

import com.college.studentportal.model.Attendance;
import com.college.studentportal.model.Student;
import com.college.studentportal.model.Subject;
import com.college.studentportal.repository.AttendanceRepository;
import com.college.studentportal.repository.StudentRepository;
import com.college.studentportal.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             StudentRepository studentRepository,
                             SubjectRepository subjectRepository) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
    }

    @Transactional
    public void markAttendance(Long subjectId, LocalDate date, Integer sessionNumber,
                               Map<Long, Attendance.AttendanceStatus> studentStatuses,
                               com.college.studentportal.model.Faculty markedBy) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        for (Map.Entry<Long, Attendance.AttendanceStatus> entry : studentStatuses.entrySet()) {
            Long studentId = entry.getKey();
            Attendance.AttendanceStatus status = entry.getValue();

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

            // Check if record already exists to perform update instead of double insert
            Optional<Attendance> existing = attendanceRepository.findExisting(studentId, subjectId, date, sessionNumber);
            
            Attendance attendance;
            if (existing.isPresent()) {
                attendance = existing.get();
                attendance.setStatus(status);
                attendance.setMarkedBy(markedBy);
            } else {
                attendance = new Attendance(student, subject, date, status, sessionNumber);
                attendance.setMarkedBy(markedBy);
            }
            attendanceRepository.save(attendance);
        }
    }

    public Map<String, Object> getStudentAttendanceSummary(Long studentId) {
        List<Subject> allSubjects = subjectRepository.findAll();
        List<Map<String, Object>> subjectStats = new ArrayList<>();
        double overallTotal = 0;
        double overallPresent = 0;

        for (Subject subject : allSubjects) {
            long totalClasses = attendanceRepository.countByStudentIdAndSubjectId(studentId, subject.getId());
            long presentCount = attendanceRepository.countByStudentIdAndSubjectIdAndStatus(studentId, subject.getId(), Attendance.AttendanceStatus.PRESENT);

            double percentage = totalClasses == 0 ? 100.0 : (double) presentCount / totalClasses * 100.0;
            
            Map<String, Object> stat = new HashMap<>();
            stat.put("subjectName", subject.getSubjectName());
            stat.put("total", totalClasses);
            stat.put("present", presentCount);
            stat.put("percentage", Math.round(percentage * 100.0) / 100.0);
            
            subjectStats.add(stat);
            
            overallTotal += totalClasses;
            overallPresent += presentCount;
        }

        double overallPercentage = overallTotal == 0 ? 100.0 : (overallPresent / overallTotal) * 100.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("overallPercentage", Math.round(overallPercentage * 100.0) / 100.0);
        summary.put("subjectWise", subjectStats);
        
        return summary;
    }

    public double getOverallAttendancePercentage(Long studentId) {
        List<Attendance> allAttendance = attendanceRepository.findByStudentId(studentId);
        if (allAttendance.isEmpty()) return 100.0;

        long presentCount = allAttendance.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT)
                .count();

        return (double) presentCount / allAttendance.size() * 100.0;
    }
}
