package com.sftpmanager.controller;

import com.sftpmanager.model.EmailVerification;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.EmailVerificationRepository;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/portal")
public class PortalAuthController {

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final EmailService emailService;

    public PortalAuthController(UserRepository userRepository,
                                EmailVerificationRepository verificationRepository,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.emailService = emailService;
    }

    // Send verification email for email-based signup
    @PostMapping("/api/auth/email-signup")
    @ResponseBody
    public ResponseEntity<?> emailSignup(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email address"));
        }
        email = email.trim().toLowerCase();

        // Create or find user
        final String finalEmail = email;
        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            User u = new User();
            u.setEmail(finalEmail);
            u.setFirstName("");
            u.setSurname("");
            u.setCreatedBy("email-signup");
            u.setLastUpdatedBy("email-signup");
            return userRepository.save(u);
        });

        // Create verification token
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setToken(UUID.randomUUID().toString());
        verification.setExpiresAt(LocalDateTime.now().plusHours(24));
        verification.setVerified(false);
        verificationRepository.save(verification);

        // Send email
        emailService.sendVerificationEmail(email, verification.getToken());

        return ResponseEntity.ok(Map.of(
            "message", "Verification email sent. Please check your inbox."
        ));
    }

    // Handle verification link click
    @GetMapping("/verify")
    public String verify(@RequestParam String token,
                         HttpSession session,
                         HttpServletResponse response) throws Exception {
        return verificationRepository.findByToken(token).map(v -> {
            if (v.getVerified()) {
                return "redirect:/portal/verify-already";
            }
            if (v.getExpiresAt().isBefore(LocalDateTime.now())) {
                return "redirect:/portal/verify-expired";
            }

            // Mark verified
            v.setVerified(true);
            verificationRepository.save(v);

            // Find user and mark email verified, store in session
            userRepository.findByEmail(v.getEmail()).ifPresent(user -> {
                session.setAttribute("PORTAL_EMAIL_VERIFIED", v.getEmail());
                session.setAttribute("PORTAL_USER_ID", user.getId());
            });

            return "redirect:/portal/verify-success";
        }).orElse("redirect:/portal/verify-invalid");
    }

    // Check if current session has a verified email
    @GetMapping("/api/auth/email-status")
    @ResponseBody
    public ResponseEntity<?> emailStatus(HttpSession session) {
        String email = (String) session.getAttribute("PORTAL_EMAIL_VERIFIED");
        Long userId = (Long) session.getAttribute("PORTAL_USER_ID");
        if (email != null && userId != null) {
            return ResponseEntity.ok(Map.of("verified", true, "email", email, "userId", userId));
        }
        return ResponseEntity.ok(Map.of("verified", false));
    }
}
