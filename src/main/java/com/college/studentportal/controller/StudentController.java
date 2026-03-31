package com.college.studentportal.controller;

import com.college.studentportal.model.Student;
import com.college.studentportal.repository.StudentRepository;
import com.college.studentportal.service.AuthService;
import com.college.studentportal.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

@RestController
@RequestMapping("/students")
public class StudentController {

    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private EmailService emailService;
    
    private final AuthService authService;

    public StudentController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Admin creates a new student.
     * Account is created in an "unclaimed" state (password remains null).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createStudent(@RequestBody Student student) {
        student.setPassword("");
        
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        student.setClaimToken(token);
        Student saved = studentRepository.save(student);
        
        // Dispatch Welcome Email
        emailService.sendStudentWelcomeEmail(saved.getEmail(), saved.getName(), token);

        return ResponseEntity.ok(Map.of(
                "student", saved,
                "claimToken", token,
                "message",
                "Student created successfully. A welcome email containing the Claim Code has been dispatched."));
    }

    /**
     * Bulk Upload Students via CSV
     * Format: Name, Email, Department, Semester
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadStudents(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        List<Student> createdStudents = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = br.readLine()) != null) {
                // Ignore empty lines
                if (line.trim().isEmpty()) continue;
                
                String[] columns = line.split(",");
                // Assuming minimum: Name, Email, Dept, Sem
                if (columns.length < 4) continue;

                String name = columns[0].trim();
                String email = columns[1].trim();
                String dept = columns[2].trim();
                String semStr = columns[3].trim();

                // Skip header row
                if (isFirstLine) {
                    isFirstLine = false;
                    if (email.toLowerCase().contains("email") || name.toLowerCase().contains("name")) {
                        continue;
                    }
                }

                // Skip if student already exists
                Optional<Student> existing = studentRepository.findByEmail(email);
                if (existing.isPresent()) {
                    skipped++;
                    continue;
                }

                int semester = 1;
                try {
                    semester = Integer.parseInt(semStr);
                } catch (NumberFormatException ignored) {}

                Student newStudent = new Student();
                newStudent.setName(name);
                newStudent.setEmail(email);
                newStudent.setDepartment(dept);
                newStudent.setSemester(semester);
                newStudent.setPassword(""); // Enforce claim process

                String claimToken = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                newStudent.setClaimToken(claimToken);

                createdStudents.add(newStudent);
            }
            
            studentRepository.saveAll(createdStudents);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully imported " + createdStudents.size() + " students. Skipped " + skipped + " duplicates.",
                    "importedCount", createdStudents.size(),
                    "skippedCount", skipped
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error processing CSV file: " + e.getMessage()));
        }
    }

    @GetMapping
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody Student incoming) {
        return studentRepository.findById(id)
                .map(existing -> {
                    existing.setName(incoming.getName());
                    existing.setEmail(incoming.getEmail());
                    existing.setDepartment(incoming.getDepartment());
                    existing.setSemester(incoming.getSemester());
                    return ResponseEntity.ok(studentRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Admin resets a student's password by nullifying it, forcing another 'Account
     * Claim'.
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable Long id) {
        return studentRepository.findById(id)
                .map(student -> {
                    student.setPassword("");
                    String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    student.setClaimToken(token);
                    studentRepository.save(student);
                    
                    // Dispatch Password Reset Email
                    emailService.sendStudentWelcomeEmail(student.getEmail(), student.getName(), token);

                    return ResponseEntity.ok(Map.of(
                            "claimToken", (Object) token,
                            "message", (Object) ("Password reset successfully. A new Claim Code has been emailed to the student: " + token)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Student changes their own password.
     */
    @PostMapping("/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password cannot be empty."));
        }
        return studentRepository.findById(id)
                .map(student -> {
                    student.setPassword(authService.hashPassword(newPassword));
                    studentRepository.save(student);
                    return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public void deleteStudent(@PathVariable Long id) {
        studentRepository.deleteById(id);
    }
}