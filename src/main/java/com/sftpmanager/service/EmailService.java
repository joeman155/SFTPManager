package com.sftpmanager.service;

import com.sftpmanager.repository.RuntimeSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final RuntimeSettingsRepository runtimeSettingsRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        RuntimeSettingsRepository runtimeSettingsRepository) {
        this.mailSender = mailSender;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String link = baseUrl + "/portal/verify?token=" + token;
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                <h2 style="color:#1a1f36">Verify your email</h2>
                <p>Click the button below to verify your email address and continue setting up your SFTP Manager account.</p>
                <a href="%s" style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                    Verify Email Address
                </a>
                <p style="color:#6b7280;font-size:.85rem">This link expires in 24 hours. If you didn't request this, ignore this email.</p>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager · sftp.leederville.net</p>
            </div>
            """.formatted(link);
        sendHtml(toEmail, "Verify your SFTP Manager email", html);
    }

    @Async
    public void sendVerificationCode(String toEmail, String code) {
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                <h2 style="color:#1a1f36">Verify your email</h2>
                <p>Enter this code to confirm your email address:</p>
                <div style="font-size:2.5rem;font-weight:800;letter-spacing:12px;color:#4f46e5;
                            background:#f5f3ff;border-radius:12px;padding:20px;text-align:center;
                            margin:20px 0">%s</div>
                <p style="color:#6b7280;font-size:.85rem">This code expires in 15 minutes.<br/>
                If you didn't sign in to SFTP Manager, ignore this email.</p>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager &middot; sftp.leederville.net</p>
            </div>
            """.formatted(code);
        sendHtml(toEmail, "Your SFTP Manager verification code: " + code, html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = baseUrl + "/portal/reset-password?token=" + token;
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                <h2 style="color:#1a1f36">Reset your password</h2>
                <p>Click the button below to choose a new password. This link expires in <strong>10 minutes</strong>.</p>
                <a href="%s" style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                    Reset Password
                </a>
                <p style="color:#6b7280;font-size:.85rem">If you didn't request this, ignore this email — your password will not change.</p>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager &middot; sftp.leederville.net</p>
            </div>
            """.formatted(link);
        sendHtml(toEmail, "Reset your SFTP Manager password", html);
    }

    @Async
    public void sendTrialWarningEmail(String toEmail, String firstName) {
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                <h2 style="color:#92400e">Your trial ends tomorrow</h2>
                <p>Hi %s,</p>
                <p>Your 7-day free trial of SFTP Manager ends <strong>tomorrow</strong>. To keep your SFTP services running, add a credit card to your account.</p>
                <a href="%s/portal" style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                    Add payment details
                </a>
                <p style="color:#6b7280;font-size:.85rem">If no payment method is added, your services will be deactivated when the trial ends.</p>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager &middot; sftp.leederville.net</p>
            </div>
            """.formatted(firstName != null && !firstName.isBlank() ? firstName : "there", baseUrl);
        sendHtml(toEmail, "Your SFTP Manager trial ends tomorrow", html);
    }

    @Async
    public void sendTrialExpiredEmail(String toEmail, String firstName) {
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                <h2 style="color:#dc2626">Your trial has ended</h2>
                <p>Hi %s,</p>
                <p>Your free trial of SFTP Manager has ended and your services have been <strong>deactivated</strong>.</p>
                <p>Add a credit card to reactivate your account immediately — your services and settings are all still saved.</p>
                <a href="%s/portal" style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                    Reactivate my account
                </a>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager &middot; sftp.leederville.net</p>
            </div>
            """.formatted(firstName != null && !firstName.isBlank() ? firstName : "there", baseUrl);
        sendHtml(toEmail, "Your SFTP Manager trial has ended — services deactivated", html);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String firstName) {
        String template = runtimeSettingsRepository.findByName("welcomeemail")
            .map(s -> s.getValue())
            .orElse(defaultWelcomeEmail(firstName));

        String html = template.replace("{{firstName}}", firstName);
        sendHtml(toEmail, "Welcome to SFTP Manager", html);
    }

    @Async
    public void sendPaymentFailedEmail(String toEmail, String firstName, String amountDisplay) {
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                <h2 style="color:#dc2626">Payment failed</h2>
                <p>Hi %s,</p>
                <p>We tried to charge <strong>%s</strong> to your saved card(s) for your SFTP Manager subscription, but the payment did not go through.</p>
                <p>Please check your card details or add a new card to keep your services running — otherwise they will be deactivated.</p>
                <a href="%s/portal" style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                    Update payment details
                </a>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager &middot; sftp.leederville.net</p>
            </div>
            """.formatted(firstName != null && !firstName.isBlank() ? firstName : "there", amountDisplay, baseUrl);
        sendHtml(toEmail, "SFTP Manager — payment failed, action needed", html);
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            // Log but don't throw — email failure shouldn't break the signup flow
            System.err.println("Email send failed to " + to + ": " + e.getMessage());
        }
    }

    private String defaultWelcomeEmail(String firstName) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:0 auto;padding:32px">
                <h2 style="color:#1a1f36">Welcome to SFTP Manager, {{firstName}}!</h2>
                <p>Your account is now active. Here's what you can do:</p>
                <ul style="line-height:2">
                    <li>🖥️ Create and manage your SFTP services</li>
                    <li>🔑 Add service accounts with password or public key authentication</li>
                    <li>🛡️ Configure IP whitelists to secure your connections</li>
                </ul>
                <a href="%s/portal" style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                    Go to my dashboard
                </a>
                <p style="color:#6b7280;font-size:.85rem">If you have any questions, reply to this email and we'll be happy to help.</p>
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                <p style="color:#9ca3af;font-size:.78rem">SFTP Manager · sftp.leederville.net</p>
            </div>
            """.formatted(baseUrl);
    }
}
