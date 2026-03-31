package com.college.studentportal.model;

import jakarta.persistence.*;

@Entity
@Table(name = "results")
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    private String courseCode;
    private String courseName;
    private double internalMarks;
    private double externalMarks;
    private double totalMarks;
    private int credits;
    private String grade;
    private double gradePoint;
    private double creditGrade;
    private int semester;

    public Result() {}

    public Long getId() { return id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public double getInternalMarks() { return internalMarks; }
    public void setInternalMarks(double internalMarks) { this.internalMarks = internalMarks; }

    public double getExternalMarks() { return externalMarks; }
    public void setExternalMarks(double externalMarks) { this.externalMarks = externalMarks; }

    public double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(double totalMarks) { this.totalMarks = totalMarks; }

    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public double getGradePoint() { return gradePoint; }
    public void setGradePoint(double gradePoint) { this.gradePoint = gradePoint; }

    public double getCreditGrade() { return creditGrade; }
    public void setCreditGrade(double creditGrade) { this.creditGrade = creditGrade; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }
}