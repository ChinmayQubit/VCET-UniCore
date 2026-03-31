package com.college.studentportal.config;

import com.college.studentportal.model.Admin;
import com.college.studentportal.repository.AdminRepository;
import com.college.studentportal.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminSeeder.class);
    private final AdminRepository adminRepository;
    private final AuthService authService;

    public AdminSeeder(AdminRepository adminRepository, AuthService authService) {
        this.adminRepository = adminRepository;
        this.authService = authService;
    }

    @Override
    public void run(String... args) {
        if (adminRepository.count() == 0) {
            String defaultEmail = "admin@vcet.edu.in";
            String rawPassword = generateSecurePassword();

            Admin admin = new Admin();
            admin.setEmail(defaultEmail);
            admin.setPassword(authService.hashPassword(rawPassword));

            adminRepository.save(admin);

            logger.info("======================================================");
            logger.info("  INITIAL ADMIN ACCOUNT CREATED");
            logger.info("  Login Email: {}", defaultEmail);
            logger.info("  Temporary Password: {}", rawPassword);
            logger.info("  Please copy this password. It will not be shown again.");
            logger.info("======================================================");
        }
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
