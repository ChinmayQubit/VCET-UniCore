package com.college.studentportal.controller;

import com.college.studentportal.model.Admin;
import com.college.studentportal.model.Faculty;
import com.college.studentportal.model.Student;
import com.college.studentportal.repository.AdminRepository;
import com.college.studentportal.repository.FacultyRepository;
import com.college.studentportal.repository.StudentRepository;
import com.college.studentportal.security.JwtService;
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
    private final FacultyRepository facultyRepository;
    private final AuthService authService;
    private final com.college.studentportal.service.EmailService emailService;
    private final JwtService jwtService;

    public AuthController(StudentRepository studentRepository, AdminRepository adminRepository,
                          FacultyRepository facultyRepository,
                          AuthService authService, com.college.studentportal.service.EmailService emailService,
                          JwtService jwtService) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.facultyRepository = facultyRepository;
        this.authService = authService;
        this.emailService = emailService;
        this.jwtService = jwtService;
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

        // Generate JWT token instead of returning raw student entity
        String token = jwtService.generateToken(student.getEmail(), "STUDENT", student.getId());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", "STUDENT",
                "studentId", student.getId(),
                "name", student.getName(),
                "email", student.getEmail(),
                "department", student.getDepartment(),
                "semester", student.getSemester()
        ));
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
        
        // Generate JWT token instead of returning raw admin entity
        String token = jwtService.generateToken(admin.getEmail(), "ADMIN", admin.getId());
        
        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", "ADMIN",
                "email", admin.getEmail()
        ));
    }

    @PostMapping("/faculty-login")
    public ResponseEntity<?> facultyLogin(@RequestParam("email") String email, @RequestParam("password") String password) {
        Optional<Faculty> facultyOpt = facultyRepository.findByEmail(email);
        
        if (facultyOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid faculty credentials."));
        }
        
        Faculty faculty = facultyOpt.get();
        if (!authService.verifyPassword(password, faculty.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid faculty credentials."));
        }
        
        String token = jwtService.generateToken(faculty.getEmail(), "FACULTY", faculty.getId());
        
        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", "FACULTY",
                "email", faculty.getEmail(),
                "name", faculty.getName(),
                "department", faculty.getDepartment()
        ));
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

    @PostMapping("/faculty/forgot-password")
    public ResponseEntity<?> facultyForgotPassword(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please enter your registered email address."));
        }
        
        Optional<Faculty> facultyOpt = facultyRepository.findByEmail(email);
        if (facultyOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No faculty account found with the provided email address."));
        }
        
        Faculty faculty = facultyOpt.get();
        // Generate a new temporary 8-character password
        String newTempPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        faculty.setPassword(authService.hashPassword(newTempPassword));
        facultyRepository.save(faculty);
        
        // Dispatch live email to the faculty with the temporary password
        emailService.sendFacultyPasswordResetEmail(faculty.getEmail(), faculty.getName(), newTempPassword);
        
        return ResponseEntity.ok(Map.of(
                "message", "A new temporary password has been successfully dispatched to your inbox. You may use it to log in immediately."
        ));
    }

}