package com.sftpmanager.controller;

import com.sftpmanager.model.*;
import com.sftpmanager.repository.EmailVerificationRepository;
import com.sftpmanager.repository.PlanRepository;
import com.sftpmanager.model.EmailVerification;
import com.sftpmanager.service.EmailService;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import com.sftpmanager.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/portal/api")
public class PortalController {

    private final PortalUserRepository portalUserRepository;
    private final UserRepository userRepository;
    private final SftpServiceRepository sftpServiceRepository;
    private final SftpServiceAccountRepository accountRepository;
    private final SftpServiceIpWhitelistRepository whitelistRepository;
    private final PlanRepository planRepository;
    private final RuntimeSettingsRepository runtimeSettingsRepository;
    private final EmailService emailService;
    private final EmailVerificationRepository verificationRepository;

    public PortalController(PortalUserRepository portalUserRepository,
                            UserRepository userRepository,
                            SftpServiceRepository sftpServiceRepository,
                            SftpServiceAccountRepository accountRepository,
                            SftpServiceIpWhitelistRepository whitelistRepository,
                            PlanRepository planRepository,
                            RuntimeSettingsRepository runtimeSettingsRepository,
                            EmailService emailService,
                            EmailVerificationRepository verificationRepository) {
        this.portalUserRepository = portalUserRepository;
        this.userRepository = userRepository;
        this.sftpServiceRepository = sftpServiceRepository;
        this.accountRepository = accountRepository;
        this.whitelistRepository = whitelistRepository;
        this.planRepository = planRepository;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
        this.emailService = emailService;
        this.verificationRepository = verificationRepository;
    }

    // ── Helper: get current User from OAuth principal ──
    private Optional<User> currentUser(OAuth2User principal) {
        if (principal == null) return Optional.empty();
        String email = principal.getAttribute("email");
        return portalUserRepository.findByGoogleEmail(email)
                .map(PortalUser::getUser);
    }

    // ── Me ──
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        String email      = principal.getAttribute("email");
        String name       = principal.getAttribute("name");
        String picture    = principal.getAttribute("picture");
        String givenName  = principal.getAttribute("given_name");
        String familyName = principal.getAttribute("family_name");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFirstName(givenName  != null ? givenName  : (name != null ? name : email));
            u.setSurname(familyName != null ? familyName : "");
            u.setCreatedBy("google-oauth");
            u.setLastUpdatedBy("google-oauth");
            return userRepository.save(u);
        });

        PortalUser portalUser = portalUserRepository.findByGoogleEmail(email).orElse(new PortalUser());
        boolean isNewUser = portalUser.getId() == null;
        portalUser.setGoogleEmail(email);
        portalUser.setGoogleName(name);
        portalUser.setGooglePicture(picture);
        portalUser.setUser(user);
        portalUserRepository.save(portalUser);

        // Check if email is verified
        boolean emailVerified = verificationRepository
            .findTopByEmailOrderByCreatedAtDesc(email)
            .map(EmailVerification::getVerified)
            .orElse(false);

        // Send verification code if not yet verified
        if (!emailVerified) {
            String code = String.format("%06d", (int)(Math.random() * 1000000));
            EmailVerification ev = new EmailVerification();
            ev.setEmail(email);
            ev.setCode(code);
            ev.setVerified(false);
            ev.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
            verificationRepository.save(ev);
            emailService.sendVerificationCode(email, code);
        }

        return ResponseEntity.ok(Map.of(
            "email",         email,
            "name",          name    != null ? name    : "",
            "picture",       picture != null ? picture : "",
            "userId",        user.getId(),
            "emailVerified", emailVerified
        ));
    }

    // ── Services ──
    @GetMapping("/services")
    public ResponseEntity<?> getServices(@AuthenticationPrincipal OAuth2User principal) {
        return currentUser(principal)
            .map(user -> ResponseEntity.ok(Map.of(
                "linked", true,
                "services", sftpServiceRepository.findByUserId(user.getId())
            )))
            .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<?> getService(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable Long id) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services")
    public ResponseEntity<?> createService(@AuthenticationPrincipal OAuth2User principal,
                                           @RequestBody SftpService service) {
        return currentUser(principal).map(user -> {
            service.setUser(user);
            service.setCreatedBy(user.getEmail());
            service.setLastUpdatedBy(user.getEmail());
            // Auto-assign host from runtime settings
            String host = runtimeSettingsRepository.findByName("sftphost001")
                .map(s -> s.getValue())
                .orElse("sftphost001.leederville.net");
            service.setHost(host);
            service.setDescription(service.getDescription());
            return ResponseEntity.status(HttpStatus.CREATED).body(sftpServiceRepository.save(service));
        }).orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<?> updateService(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id,
                                           @RequestBody SftpService updated) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> {
                    s.setName(updated.getName());
                    s.setDescription(updated.getDescription());
                    s.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(sftpServiceRepository.save(s));
                })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<?> deleteService(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> { sftpServiceRepository.delete(s); return ResponseEntity.noContent().build(); })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    // ── Service Accounts ──
    @GetMapping("/services/{svcId}/accounts")
    public ResponseEntity<?> getAccounts(@AuthenticationPrincipal OAuth2User principal,
                                         @PathVariable Long svcId) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> ResponseEntity.ok(accountRepository.findBySftpServiceId(svcId)))
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services/{svcId}/accounts")
    public ResponseEntity<?> createAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long svcId,
                                           @RequestBody SftpServiceAccount account) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> {
                    account.setSftpService(s);
                    account.setCreatedBy(user.getEmail());
                    account.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(accountRepository.save(account));
                })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/services/{svcId}/accounts/{id}")
    public ResponseEntity<?> updateAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long svcId,
                                           @PathVariable Long id,
                                           @RequestBody SftpServiceAccount updated) {
        return currentUser(principal).map(user ->
            accountRepository.findById(id)
                .filter(a -> a.getSftpService() != null && a.getSftpService().getId().equals(svcId))
                .map(a -> {
                    a.setUsername(updated.getUsername());
                    a.setAuthenticationType(updated.getAuthenticationType());
                    a.setPassword(updated.getPassword());
                    a.setPublicKey(updated.getPublicKey());
                    a.setEnabled(updated.getEnabled());
                    a.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(accountRepository.save(a));
                })
                .orElse(ResponseEntity.status(404).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable Long id) {
        return currentUser(principal).map(user ->
            accountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id) {
        return currentUser(principal).map(user -> {
            accountRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.status(401).build());
    }

    // ── IP Whitelist ──
    @GetMapping("/services/{svcId}/whitelist")
    public ResponseEntity<?> getWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                          @PathVariable Long svcId) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> ResponseEntity.ok(whitelistRepository.findBySftpServiceId(svcId)))
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services/{svcId}/whitelist")
    public ResponseEntity<?> createWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long svcId,
                                             @RequestBody SftpServiceIpWhitelist entry) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> {
                    entry.setSftpService(s);
                    entry.setCreatedBy(user.getEmail());
                    entry.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(whitelistRepository.save(entry));
                })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/services/{svcId}/whitelist/{id}")
    public ResponseEntity<?> updateWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long svcId,
                                             @PathVariable Long id,
                                             @RequestBody SftpServiceIpWhitelist updated) {
        return currentUser(principal).map(user ->
            whitelistRepository.findById(id)
                .map(e -> {
                    e.setIpAddress(updated.getIpAddress());
                    e.setEnabled(updated.getEnabled());
                    e.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(whitelistRepository.save(e));
                })
                .orElse(ResponseEntity.status(404).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/whitelist/{id}")
    public ResponseEntity<?> getWhitelistEntry(@AuthenticationPrincipal OAuth2User principal,
                                               @PathVariable Long id) {
        return currentUser(principal).map(user ->
            whitelistRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/whitelist/{id}")
    public ResponseEntity<?> deleteWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long id) {
        return currentUser(principal).map(user -> {
            whitelistRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.status(401).build());
    }

    // ── Email Verification ──

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@AuthenticationPrincipal OAuth2User principal,
                                        @RequestBody Map<String, Object> body) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getAttribute("email");
        String code = (String) body.get("code");

        return verificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
            .map(ev -> {
                if (ev.getVerified()) {
                    return ResponseEntity.ok(Map.of("success", true, "message", "Already verified"));
                }
                if (ev.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Code has expired. Please sign in again to get a new code."));
                }
                if (!ev.getCode().equals(code != null ? code.trim() : "")) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Incorrect code. Please try again."));
                }
                ev.setVerified(true);
                verificationRepository.save(ev);
                return ResponseEntity.ok(Map.of("success", true));
            })
            .orElse(ResponseEntity.badRequest().body(Map.of("error", "No verification code found. Please sign in again.")));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<?> resendCode(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getAttribute("email");
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        EmailVerification ev = new EmailVerification();
        ev.setEmail(email);
        ev.setCode(code);
        ev.setVerified(false);
        ev.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
        verificationRepository.save(ev);
        emailService.sendVerificationCode(email, code);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Onboarding ──

    @GetMapping("/onboarding")
    public ResponseEntity<?> getOnboardingData(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getAttribute("email");

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();

        // Already onboarded
        if (Boolean.TRUE.equals(user.getOnboarded())) {
            return ResponseEntity.ok(Map.of("onboarded", true));
        }

        // Get plans
        List<Plan> plans = planRepository.findAll();

        // Get T&C from runtime settings
        String tc = runtimeSettingsRepository.findByName("termsandconditions")
            .map(RuntimeSettings::getValue)
            .orElse("<p>Please contact your administrator for terms and conditions.</p>");

        return ResponseEntity.ok(Map.of(
            "onboarded", false,
            "plans", plans,
            "termsAndConditions", tc
        ));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<?> completeOnboarding(@AuthenticationPrincipal OAuth2User principal,
                                                @RequestBody Map<String, Object> body) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getAttribute("email");

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();

        // Set plan
        Integer planId = body.get("planId") != null ? Integer.valueOf(body.get("planId").toString()) : null;
        if (planId != null) {
            planRepository.findById(planId).ifPresent(user::setPlan);
        }

        // Set CC details if provided
        String ccNumber = (String) body.get("ccNumber");
        String ccName   = (String) body.get("ccName");
        String ccExpiry = (String) body.get("ccExpiry");

        if (ccNumber != null && !ccNumber.isBlank()) {
            user.setCcNumber(ccNumber);
            user.setCcName(ccName);
            user.setCcExpiry(ccExpiry);
            user.setTrialExpires(null); // has CC, no trial needed
        } else {
            // No CC - set 7 day trial
            user.setTrialExpires(java.time.LocalDate.now().plusDays(7));
        }

        // Set phone
        String phone = (String) body.get("phone");
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone);
        }

        user.setOnboarded(true);
        user.setLastUpdatedBy(email);
        userRepository.save(user);

        // Send welcome email
        emailService.sendWelcomeEmail(email, user.getFirstName());

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Account ──

    @GetMapping("/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getAttribute("email");
        return userRepository.findByEmail(email)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/account")
    public ResponseEntity<?> updateAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @RequestBody Map<String, Object> body) {
        if (principal == null) return ResponseEntity.status(401).build();
        String email = principal.getAttribute("email");

        return userRepository.findByEmail(email).map(user -> {
            if (body.get("firstName")   != null) user.setFirstName((String) body.get("firstName"));
            if (body.get("surname")     != null) user.setSurname((String) body.get("surname"));
            if (body.get("phone")       != null) user.setPhone((String) body.get("phone"));
            if (body.get("company")     != null) user.setCompany((String) body.get("company"));
            if (body.get("addressLine1")!= null) user.setAddressLine1((String) body.get("addressLine1"));
            if (body.get("addressLine2")!= null) user.setAddressLine2((String) body.get("addressLine2"));
            if (body.get("state")       != null) user.setState((String) body.get("state"));
            if (body.get("postcode")    != null) user.setPostcode((String) body.get("postcode"));
            if (body.get("country")     != null) user.setCountry((String) body.get("country"));
            if (body.get("ccNumber")    != null) user.setCcNumber((String) body.get("ccNumber"));
            if (body.get("ccName")      != null) user.setCcName((String) body.get("ccName"));
            if (body.get("ccExpiry")    != null) user.setCcExpiry((String) body.get("ccExpiry"));
            user.setLastUpdatedBy(email);
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }

}