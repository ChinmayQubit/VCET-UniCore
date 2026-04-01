package com.college.studentportal.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    private LocalDate date;
    
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    private Integer sessionNumber; // To track multiple lectures of same subject on same day

    @ManyToOne
    @JoinColumn(name = "marked_by_faculty_id")
    private Faculty markedBy;

    public enum AttendanceStatus {
        PRESENT, ABSENT
    }

    public Attendance() {}

    public Attendance(Student student, Subject subject, LocalDate date, AttendanceStatus status, Integer sessionNumber) {
        this.student = student;
        this.subject = subject;
        this.date = date;
        this.status = status;
        this.sessionNumber = sessionNumber;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }

    public Integer getSessionNumber() { return sessionNumber; }
    public void setSessionNumber(Integer sessionNumber) { this.sessionNumber = sessionNumber; }

    public Faculty getMarkedBy() { return markedBy; }
    public void setMarkedBy(Faculty markedBy) { this.markedBy = markedBy; }
}
