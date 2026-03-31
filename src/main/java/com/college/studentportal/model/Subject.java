package com.college.studentportal.model;

import jakarta.persistence.*;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String subjectName;
    private int credits;
    private int semester;

    public Subject() {}

    public Subject(String subjectName, int credits, int semester) {
        this.subjectName = subjectName;
        this.credits = credits;
        this.semester = semester;
    }

    public Long getId() { return id; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }
}