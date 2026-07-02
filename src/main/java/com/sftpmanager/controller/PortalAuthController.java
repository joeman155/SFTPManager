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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/portal/api/auth")
public class PortalAuthController {

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PortalAuthController(UserRepository userRepository,
                                EmailVerificationRepository verificationRepository,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.emailService = emailService;
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

        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();
            // Existing user — check password
            if ("EMAIL".equals(user.getAuthType())) {
                if (user.getPasswordHash() == null ||
                    !passwordEncoder.matches(password, user.getPasswordHash())) {
                    return ResponseEntity.status(401).body(Map.of("error", "Incorrect password"));
                }
            } else {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "This email is registered with Google. Please use the Google sign-in button."
                ));
            }
        } else {
            // New user — create account
            if (password.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
            }
            final String finalEmail = email;
            User user = new User();
            user.setEmail(finalEmail);
            user.setFirstName("");
            user.setSurname("");
            user.setAuthType("EMAIL");
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setCreatedBy("email-signup");
            user.setLastUpdatedBy("email-signup");
            userRepository.save(user);
        }

        // Store email in session so portal.html can use it
        session.setAttribute("EMAIL_AUTH_USER", email);

        // Send verification code
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        EmailVerification ev = new EmailVerification();
        ev.setEmail(email);
        ev.setCode(code);
        ev.setVerified(false);
        ev.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        verificationRepository.save(ev);
        emailService.sendVerificationCode(email, code);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "email", email,
            "message", "Verification code sent"
        ));
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
