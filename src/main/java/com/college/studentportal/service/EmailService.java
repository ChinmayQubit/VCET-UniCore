package com.college.studentportal.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendStudentWelcomeEmail(String toEmail, String studentName, String claimToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to VCET Student Portal - Account Setup Required");

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

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send Welcome Email to: " + toEmail);
            e.printStackTrace();
        }
    }

    public void sendPasswordResetEmail(String toEmail, String studentName, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("VCET Student Portal - Password Reset Requested");

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

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send Password Reset Email to: " + toEmail);
            e.printStackTrace();
        }
    }
}
