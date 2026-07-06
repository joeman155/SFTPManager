package com.sftpmanager.controller;

import com.sftpmanager.model.EmailVerification;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.EmailVerificationRepository;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/portal/api/auth")
public class PortalAuthController {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthController.class);

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int MAX_ATTEMPTS = 3;

    @Value("${recaptcha.secret-key:}")
    private String recaptchaSecretKey;

    public PortalAuthController(UserRepository userRepository,
                                EmailVerificationRepository verificationRepository,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.emailService = emailService;
    }

    private boolean verifyRecaptcha(String token) {
        if (recaptchaSecretKey == null || recaptchaSecretKey.isBlank()) return true; // skip if not configured
        try {
            RestTemplate rt = new RestTemplate();
            String url = "https://www.google.com/recaptcha/api/siteverify";
            String body = "secret=" + recaptchaSecretKey + "&response=" + token;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            var response = rt.postForObject(url,
                new org.springframework.http.HttpEntity<>(body, headers),
                java.util.Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) return false;
            // v3 returns a score 0.0-1.0. Require score >= 0.5
            Object score = response.get("score");
            log.info("reCAPTCHA score for signup: {}", score);
            if (score instanceof Number) {
                double s = ((Number) score).doubleValue();
                log.info("reCAPTCHA passed: {}", s >= 0.5);
                return s >= 0.5;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null) return ResponseEntity.badRequest().build();
        boolean exists = userRepository.findByEmail(email.trim().toLowerCase()).isPresent();
        return ResponseEntity.ok(Map.of("newUser", !exists));
    }

    @PostMapping("/email-signin")
    public ResponseEntity<?> emailSignIn(@RequestBody Map<String, String> body,
                                         HttpSession session) {
        String email    = body.get("email");
        String password = body.get("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }
        email = email.trim().toLowerCase();
        final String finalEmail = email;

        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();

            // Check locked
            if (Boolean.TRUE.equals(user.getLocked())) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Account is locked due to too many failed login attempts. Please contact support."
                ));
            }

            if ("EMAIL".equals(user.getAuthType())) {
                if (user.getPasswordHash() == null ||
                    !passwordEncoder.matches(password, user.getPasswordHash())) {

                    // Increment failed attempts
                    int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
                    user.setFailedLoginAttempts(attempts);
                    if (attempts >= MAX_ATTEMPTS) {
                        user.setLocked(true);
                        userRepository.save(user);
                        return ResponseEntity.status(403).body(Map.of(
                            "error", "Account locked after " + MAX_ATTEMPTS + " failed attempts. Please contact support."
                        ));
                    }
                    userRepository.save(user);
                    return ResponseEntity.status(401).body(Map.of(
                        "error", "Incorrect password. " + (MAX_ATTEMPTS - attempts) + " attempt(s) remaining."
                    ));
                }
                // Successful login — reset attempts
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            } else {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "This email is registered with Google. Please use the Google sign-in button."
                ));
            }

            // Store in session
            session.setAttribute("EMAIL_AUTH_USER", email);

            // Only send verification code if email not yet verified
            boolean alreadyVerified = verificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .map(EmailVerification::getVerified)
                .orElse(false);

            if (!alreadyVerified) {
                sendCode(email);
            }

            return ResponseEntity.ok(Map.of("success", true, "email", email));

        } else {
            // New user
            if (password.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
            }
            String firstName = body.getOrDefault("firstName", "");
            String surname   = body.getOrDefault("surname", "");
            if (firstName.isBlank() || surname.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "First name and surname are required"));
            }

            // Verify Turnstile captcha for new signups
            String captchaToken = body.getOrDefault("captchaToken", "");
            if (!verifyRecaptcha(captchaToken)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Captcha verification failed. Please try again."));
            }

            User user = new User();
            user.setEmail(finalEmail);
            user.setFirstName(firstName.trim());
            user.setSurname(surname.trim());
            user.setAuthType("EMAIL");
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setCreatedBy("email-signup");
            user.setLastUpdatedBy("email-signup");
            userRepository.save(user);

            session.setAttribute("EMAIL_AUTH_USER", email);
            sendCode(email);

            return ResponseEntity.ok(Map.of("success", true, "email", email));
        }
    }

    private void sendCode(String email) {
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        EmailVerification ev = new EmailVerification();
        ev.setEmail(email);
        ev.setCode(code);
        ev.setVerified(false);
        ev.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        verificationRepository.save(ev);
        emailService.sendVerificationCode(email, code);
    }

    @GetMapping("/email-status")
    public ResponseEntity<?> emailStatus(HttpSession session) {
        String email = (String) session.getAttribute("EMAIL_AUTH_USER");
        if (email != null) {
            return ResponseEntity.ok(Map.of("authenticated", true, "email", email));
        }
        return ResponseEntity.ok(Map.of("authenticated", false));
    }
}
