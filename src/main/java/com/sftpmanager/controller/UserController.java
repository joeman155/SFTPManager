package com.sftpmanager.controller;

import com.sftpmanager.model.PasswordReset;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.PasswordResetRepository;
import com.sftpmanager.service.EmailService;
import com.sftpmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailService emailService;

    public UserController(UserService userService, PasswordResetRepository passwordResetRepository,
                          EmailService emailService) {
        this.userService = userService;
        this.passwordResetRepository = passwordResetRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Admins only ever create email/password accounts here — Google accounts come from
    // self-service Google sign-in, not this form — so authType is always forced to EMAIL
    // regardless of what's in the request body.
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody User user,
                                    @RequestParam(required = false) Long accountControlsId) {
        if (userService.existsByEmail(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email already exists"));
        }
        user.setAuthType("EMAIL");
        User saved = userService.save(user, accountControlsId);
        sendAccountInvite(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Admin-created accounts start with no password — instead of an admin
     * typing one in, we email the new user a set-password link (same
     * mechanism as self-service "forgot password", just a longer-lived
     * token since this doubles as the account invite).
     */
    private void sendAccountInvite(User user) {
        PasswordReset reset = new PasswordReset();
        reset.setEmail(user.getEmail());
        reset.setToken(UUID.randomUUID().toString());
        reset.setUsed(false);
        reset.setExpiresAt(LocalDateTime.now().plusHours(48));
        passwordResetRepository.save(reset);
        emailService.sendAccountInviteEmail(user.getEmail(), user.getFirstName(), reset.getToken());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody User user,
                                    @RequestParam(required = false) Long accountControlsId,
                                    @AuthenticationPrincipal OAuth2User principal) {
        try {
            String adminEmail = principal != null ? principal.getAttribute("email") : "unknown";
            return ResponseEntity.ok(userService.update(id, user, accountControlsId, adminEmail));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return userService.findById(id).map(user -> {
            if (updates.containsKey("locked")) {
                user.setLocked(Boolean.TRUE.equals(updates.get("locked")));
                user.setFailedLoginAttempts(0); // reset on unlock
            }
            if (updates.containsKey("servicesDeactivated")) {
                user.setServicesDeactivated(Boolean.TRUE.equals(updates.get("servicesDeactivated")));
            }
            if (updates.containsKey("accountClosed")) {
                user.setAccountClosed(Boolean.TRUE.equals(updates.get("accountClosed")));
            }
            return ResponseEntity.ok(userService.save(user, null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            userService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Unable to delete this user because related records still reference it."));
        }
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }
        return userService.findById(id).<ResponseEntity<?>>map(user -> {
            if (!"EMAIL".equals(user.getAuthType())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "This user signs in with Google; there is no password to reset."));
            }
            userService.resetPassword(user, newPassword);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errors);
    }
}
