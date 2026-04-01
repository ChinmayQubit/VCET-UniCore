package com.college.studentportal.controller;

import com.college.studentportal.model.Faculty;
import com.college.studentportal.model.Subject;
import com.college.studentportal.repository.FacultyRepository;
import com.college.studentportal.repository.SubjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;

    public SubjectController(SubjectRepository subjectRepository, FacultyRepository facultyRepository) {
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
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

    /**
     * Assign a faculty member to a subject.
     */
    @PutMapping("/{id}/assign-faculty")
    public ResponseEntity<?> assignFaculty(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long facultyId = body.get("facultyId");

        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        if (facultyId == null) {
            // Unassign faculty
            subject.setFaculty(null);
        } else {
            Faculty faculty = facultyRepository.findById(facultyId)
                    .orElseThrow(() -> new RuntimeException("Faculty not found"));
            subject.setFaculty(faculty);
        }

        subjectRepository.save(subject);
        return ResponseEntity.ok(subject);
    }
}