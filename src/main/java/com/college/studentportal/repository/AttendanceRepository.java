package com.college.studentportal.repository;

import com.college.studentportal.model.Attendance;
import com.college.studentportal.model.Student;
import com.college.studentportal.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByStudentId(Long studentId);

    List<Attendance> findByStudentIdAndSubjectId(Long studentId, Long subjectId);

    long countByStudentIdAndSubjectIdAndStatus(Long studentId, Long subjectId, Attendance.AttendanceStatus status);

    long countByStudentIdAndSubjectId(Long studentId, Long subjectId);

    @Query("SELECT a FROM Attendance a WHERE a.student.id = :studentId AND a.subject.id = :subjectId AND a.date = :date AND a.sessionNumber = :sessionNumber")
    Optional<Attendance> findExisting(
            @Param("studentId") Long studentId,
            @Param("subjectId") Long subjectId,
            @Param("date") LocalDate date,
            @Param("sessionNumber") Integer sessionNumber
    );
}
