package com.college.studentportal.controller;

import com.college.studentportal.model.Subject;
import com.college.studentportal.repository.SubjectRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectRepository subjectRepository;

    public SubjectController(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    // Add new subject
    @PostMapping
    public Subject addSubject(@RequestBody Subject subject) {
        return subjectRepository.save(subject);
    }

    // Get all subjects
    @GetMapping
    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }
    @GetMapping("/semester/{semester}")
    public List<Subject> getSubjectsBySemester(@PathVariable int semester) {
        return subjectRepository.findBySemester(semester);
    }

    @PutMapping("/{id}")
    public Subject updateSubject(@PathVariable Long id, @RequestBody Subject subject) {

        Subject existingSubject = subjectRepository.findById(id).orElseThrow();

        existingSubject.setSubjectName(subject.getSubjectName());
        existingSubject.setCredits(subject.getCredits());
        existingSubject.setSemester(subject.getSemester());

        return subjectRepository.save(existingSubject);
    }

    @DeleteMapping("/{id}")
    public void deleteSubject(@PathVariable Long id) {
        subjectRepository.deleteById(id);
    }
}