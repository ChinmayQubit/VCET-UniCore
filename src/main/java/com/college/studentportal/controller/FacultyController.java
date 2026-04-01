package com.college.studentportal.controller;

import com.college.studentportal.model.Faculty;
import com.college.studentportal.repository.FacultyRepository;
import com.college.studentportal.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/faculty")
public class FacultyController {

    private final FacultyRepository facultyRepository;
    private final AuthService authService;
    private final com.college.studentportal.service.EmailService emailService;

    public FacultyController(FacultyRepository facultyRepository, AuthService authService, 
                             com.college.studentportal.service.EmailService emailService) {
        this.facultyRepository = facultyRepository;
        this.authService = authService;
        this.emailService = emailService;
    }

    /**
     * Admin creates a new faculty account. Auto-generates a password.
     */
    @PostMapping
    public ResponseEntity<?> createFaculty(@RequestBody Faculty faculty) {
        // Check if email already exists
        if (facultyRepository.findByEmail(faculty.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A faculty member with this email already exists."));
        }

        String rawPassword = generateSecurePassword();
        faculty.setPassword(authService.hashPassword(rawPassword));
        Faculty saved = facultyRepository.save(faculty);

        // Dispatch Welcome Email with password securely
        emailService.sendFacultyWelcomeEmail(saved.getEmail(), saved.getName(), rawPassword);

        return ResponseEntity.ok(Map.of(
                "message", "Faculty account created and email dispatched successfully.",
                "facultyId", saved.getId(),
                "name", saved.getName(),
                "email", saved.getEmail(),
                "department", saved.getDepartment()
        ));
    }

    /**
     * List all faculty members.
     */
    @GetMapping
    public ResponseEntity<List<Faculty>> getAllFaculty() {
        return ResponseEntity.ok(facultyRepository.findAll());
    }

    /**
     * Update a faculty member's details (not password).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateFaculty(@PathVariable Long id, @RequestBody Faculty faculty) {
        Optional<Faculty> opt = facultyRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Faculty existing = opt.get();
        existing.setName(faculty.getName());
        existing.setEmail(faculty.getEmail());
        existing.setDepartment(faculty.getDepartment());
        facultyRepository.save(existing);

        return ResponseEntity.ok(existing);
    }

    /**
     * Delete a faculty member.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaculty(@PathVariable Long id) {
        if (!facultyRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        facultyRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Faculty deleted successfully."));
    }

    /**
     * Admin resets a faculty member's password.
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        Optional<Faculty> opt = facultyRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String rawPassword = generateSecurePassword();
        Faculty faculty = opt.get();
        faculty.setPassword(authService.hashPassword(rawPassword));
        facultyRepository.save(faculty);

        // Dispatch password reset email
        emailService.sendFacultyPasswordResetEmail(faculty.getEmail(), faculty.getName(), rawPassword);

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully and email dispatched."
        ));
    }

    /**
     * Faculty changes their own password (requires current password).
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestParam String currentPassword,
                                            @RequestParam String newPassword,
                                            Authentication auth) {
        Long facultyId = (Long) auth.getDetails();
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new RuntimeException("Faculty not found"));

        if (!authService.verifyPassword(currentPassword, faculty.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Current password is incorrect."));
        }

        if (newPassword == null || newPassword.trim().length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "New password must be at least 6 characters."));
        }

        faculty.setPassword(authService.hashPassword(newPassword));
        facultyRepository.save(faculty);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }

    private String generateSecurePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
