package com.college.studentportal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    private final RestTemplate restTemplate;

    public EmailService() {
        this.restTemplate = new RestTemplate();
    }

    private void sendBrevoEmail(String toEmail, String studentName, String subject, String htmlContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);
            headers.set("accept", "application/json");

            Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", toEmail, "name", studentName)),
                "subject", subject,
                "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully dispatched email to: {}", toEmail);
            } else {
                logger.error("Failed to send email via Brevo. Status: {}, Response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            logger.error("Exception occurred while sending email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendStudentWelcomeEmail(String toEmail, String studentName, String claimToken) {
        String subject = "Welcome to VCET Student Portal - Account Setup Required";
        String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                <div style="max-width: 600px; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); margin: auto;">
                    <h2 style="color: #0b1a30; text-align: center;">Vidyavardhini's College of Engineering and Technology</h2>
                    <hr style="border: 1px solid #d4af37;" />
                    <p style="font-size: 16px; color: #333;">Dear <strong>%s</strong>,</p>
                    <p style="font-size: 16px; color: #333;">Your official student portfolio has been successfully created by the college administration.</p>
                    
                    <div style="background-color: #f8f9fa; border-left: 4px solid #d4af37; padding: 15px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 15px; color: #555;">To activate your account and set your permanent password, please use the following secure Claim Code:</p>
                        <h1 style="text-align: center; font-family: monospace; letter-spacing: 5px; color: #0b1a30; background: #e2e8f0; padding: 10px; border-radius: 4px;">%s</h1>
                    </div>
                    
                    <p style="font-size: 16px; color: #333;"><strong>Instructions:</strong></p>
                    <ol style="font-size: 15px; color: #444; line-height: 1.6;">
                        <li>Visit the VCET Student Portal login page.</li>
                        <li>Click on <strong>"First Time Login? Claim Account"</strong>.</li>
                        <li>Enter your registered email address (%s) and the Claim Code above.</li>
                        <li>Create a secure, private password.</li>
                    </ol>
                    
                    <br/>
                    <p style="font-size: 14px; color: #777;">If you did not expect this email, please contact the IT Help Desk immediately.</p>
                    <p style="font-size: 14px; color: #777; font-weight: bold;">Do not share your Claim Code with anyone.</p>
                </div>
            </body>
            </html>
            """.formatted(studentName, claimToken, toEmail);

        sendBrevoEmail(toEmail, studentName, subject, htmlContent);
    }

    public void sendPasswordResetEmail(String toEmail, String studentName, String resetToken) {
        String subject = "VCET Student Portal - Password Reset Requested";
        String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                <div style="max-width: 600px; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); margin: auto;">
                    <h2 style="color: #0b1a30; text-align: center;">Security Alert - Password Reset</h2>
                    <hr style="border: 1px solid #d4af37;" />
                    <p style="font-size: 16px; color: #333;">Hello <strong>%s</strong>,</p>
                    <p style="font-size: 16px; color: #333;">We received a request to reset the password for your VCET Student Portal account.</p>
                    
                    <div style="background-color: #f8f9fa; border-left: 4px solid #e74c3c; padding: 15px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 15px; color: #555;">Use the following exact securely-generated Reset Token to reclaim your account:</p>
                        <h1 style="text-align: center; font-family: monospace; letter-spacing: 5px; color: #e74c3c; background: #e2e8f0; padding: 10px; border-radius: 4px;">%s</h1>
                    </div>
                    
                    <p style="font-size: 16px; color: #333;"><strong>What to do next:</strong></p>
                    <ol style="font-size: 15px; color: #444; line-height: 1.6;">
                        <li>Go back to the portal and click <strong>"First Time Login? Claim Account"</strong>.</li>
                        <li>Enter your email (%s) along with the reset token shown above.</li>
                        <li>Create a brand new secure password.</li>
                    </ol>
                    
                    <br/>
                    <p style="font-size: 14px; color: #777;">If you did not request this password reset, you can safely ignore this email. Your password will not change unless you complete the claim process.</p>
                </div>
            </body>
            </html>
            """.formatted(studentName, resetToken, toEmail);

        sendBrevoEmail(toEmail, studentName, subject, htmlContent);
    }
    public void sendFacultyWelcomeEmail(String toEmail, String facultyName, String tempPassword) {
        String subject = "Welcome to VCET Student Portal - Faculty Account Setup";
        String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                <div style="max-width: 600px; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); margin: auto;">
                    <h2 style="color: #0b1a30; text-align: center;">Vidyavardhini's College of Engineering and Technology</h2>
                    <hr style="border: 1px solid #d4af37;" />
                    <p style="font-size: 16px; color: #333;">Dear Prof. <strong>%s</strong>,</p>
                    <p style="font-size: 16px; color: #333;">Your official Faculty Portal account has been successfully created by the college administration.</p>
                    
                    <div style="background-color: #f8f9fa; border-left: 4px solid #d4af37; padding: 15px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 15px; color: #555;">Please use the following securely-generated temporary password to log in for the first time:</p>
                        <h1 style="text-align: center; font-family: monospace; letter-spacing: 5px; color: #0b1a30; background: #e2e8f0; padding: 10px; border-radius: 4px;">%s</h1>
                    </div>
                    
                    <p style="font-size: 16px; color: #333;"><strong>Instructions:</strong></p>
                    <ol style="font-size: 15px; color: #444; line-height: 1.6;">
                        <li>Visit the VCET Academic Portal login page.</li>
                        <li>Select <strong>Faculty</strong> from the Role dropdown.</li>
                        <li>Log in using your official email (%s) and the temporary password above.</li>
                        <li>For your security, navigate to the <strong>Change Password</strong> tab immediately to set a permanent private password.</li>
                    </ol>
                    
                    <br/>
                    <p style="font-size: 14px; color: #777;">If you did not expect this email, please contact the IT Help Desk immediately.</p>
                </div>
            </body>
            </html>
            """.formatted(facultyName, tempPassword, toEmail);

        sendBrevoEmail(toEmail, facultyName, subject, htmlContent);
    }

    public void sendFacultyPasswordResetEmail(String toEmail, String facultyName, String newTempPassword) {
        String subject = "VCET Faculty Portal - Admin Password Reset";
        String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                <div style="max-width: 600px; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); margin: auto;">
                    <h2 style="color: #0b1a30; text-align: center;">Security Alert - Password Reset</h2>
                    <hr style="border: 1px solid #d4af37;" />
                    <p style="font-size: 16px; color: #333;">Hello Prof. <strong>%s</strong>,</p>
                    <p style="font-size: 16px; color: #333;">Your faculty portal password has been reset by the systems administrator.</p>
                    
                    <div style="background-color: #f8f9fa; border-left: 4px solid #e74c3c; padding: 15px; margin: 20px 0;">
                        <p style="margin: 0; font-size: 15px; color: #555;">Please use the following temporary password to log back into your account:</p>
                        <h1 style="text-align: center; font-family: monospace; letter-spacing: 5px; color: #e74c3c; background: #e2e8f0; padding: 10px; border-radius: 4px;">%s</h1>
                    </div>
                    
                    <p style="font-size: 16px; color: #333;"><strong>What to do next:</strong></p>
                    <ol style="font-size: 15px; color: #444; line-height: 1.6;">
                        <li>Log in using your email (%s) and this new temporary password.</li>
                        <li>Change your password immediately in the Faculty Dashboard under the "Change Password" tab.</li>
                    </ol>
                    
                    <br/>
                    <p style="font-size: 14px; color: #777;">If you did not request this password reset, please contact the administration immediately.</p>
                </div>
            </body>
            </html>
            """.formatted(facultyName, newTempPassword, toEmail);

        sendBrevoEmail(toEmail, facultyName, subject, htmlContent);
    }
}
