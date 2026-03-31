package com.college.studentportal.controller;

import com.college.studentportal.model.Admin;
import com.college.studentportal.model.Student;
import com.college.studentportal.repository.AdminRepository;
import com.college.studentportal.repository.StudentRepository;
import com.college.studentportal.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final AuthService authService;
    private final com.college.studentportal.service.EmailService emailService;

    public AuthController(StudentRepository studentRepository, AdminRepository adminRepository, AuthService authService, com.college.studentportal.service.EmailService emailService) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.authService = authService;
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam("email") String email, @RequestParam("password") String password) {
        Optional<Student> studentOpt = studentRepository.findByEmail(email);

        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password."));
        }

        Student student = studentOpt.get();

        if (student.getPassword() == null || student.getPassword().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Account has not been claimed yet. Please claim your account to set a password."));
        }

        if (!authService.verifyPassword(password, student.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password."));
        }

        return ResponseEntity.ok(student);
    }

    @PostMapping("/admin-login")
    public ResponseEntity<?> adminLogin(@RequestParam("email") String email, @RequestParam("password") String password) {
        Optional<Admin> adminOpt = adminRepository.findByEmail(email);
        
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid admin credentials."));
        }
        
        Admin admin = adminOpt.get();
        if (!authService.verifyPassword(password, admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid admin credentials."));
        }
        
        return ResponseEntity.ok(admin);
    }

    @PostMapping("/claim-account")
    public ResponseEntity<?> claimAccount(@RequestParam("claimToken") String claimToken, @RequestParam("email") String email, @RequestParam("newPassword") String newPassword) {
        Optional<Student> studentOpt = studentRepository.findAll().stream()
                 .filter(s -> email.equalsIgnoreCase(s.getEmail()) && claimToken.equalsIgnoreCase(s.getClaimToken()))
                 .findFirst();
        
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Email or Claim Code."));
        }
        
        Student student = studentOpt.get();
        
        if (student.getPassword() != null && !student.getPassword().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Account has already been claimed. If you forgot your password, please contact the IT department."));
        }
        
        if (newPassword == null || newPassword.trim().length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Password must be at least 6 characters long."));
        }
        
        student.setPassword(authService.hashPassword(newPassword));
        student.setClaimToken(null); // Invalidate token after unique use
        studentRepository.save(student);
        
        return ResponseEntity.ok(Map.of("message", "Account successfully claimed and password set. You may now log in."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please enter your registered email address."));
        }
        
        Optional<Student> studentOpt = studentRepository.findByEmail(email);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No student account found with the provided email address."));
        }
        
        Student student = studentOpt.get();
        // Generate a random 8-character token
        String resetToken = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        student.setClaimToken(resetToken);
        student.setPassword(null); // Clear password to allow reclaiming
        studentRepository.save(student);
        
        // Dispatch live email to the student
        emailService.sendPasswordResetEmail(student.getEmail(), student.getName(), resetToken);
        
        return ResponseEntity.ok(Map.of(
                "message", "A password reset email with detailed instructions has been successfully forwarded to your inbox."
        ));
    }

}