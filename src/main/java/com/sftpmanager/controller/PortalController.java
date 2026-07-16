package com.sftpmanager.controller;

import com.sftpmanager.model.*;
import com.sftpmanager.repository.EmailVerificationRepository;
import com.sftpmanager.model.EmailVerification;
import com.sftpmanager.service.EmailService;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import com.sftpmanager.repository.*;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpSession;
import org.springframework.scheduling.annotation.Async;
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
    private final AccountControlsRepository accountControlsRepository;
    private final RuntimeSettingsRepository runtimeSettingsRepository;
    private final EmailService emailService;
    private final EmailVerificationRepository verificationRepository;
    private final com.sftpmanager.service.BillingService billingService;

    public PortalController(PortalUserRepository portalUserRepository,
                            UserRepository userRepository,
                            SftpServiceRepository sftpServiceRepository,
                            SftpServiceAccountRepository accountRepository,
                            SftpServiceIpWhitelistRepository whitelistRepository,
                            AccountControlsRepository accountControlsRepository,
                            RuntimeSettingsRepository runtimeSettingsRepository,
                            EmailService emailService,
                            EmailVerificationRepository verificationRepository,
                            com.sftpmanager.service.BillingService billingService) {
        this.portalUserRepository = portalUserRepository;
        this.userRepository = userRepository;
        this.sftpServiceRepository = sftpServiceRepository;
        this.accountRepository = accountRepository;
        this.whitelistRepository = whitelistRepository;
        this.accountControlsRepository = accountControlsRepository;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
        this.emailService = emailService;
        this.verificationRepository = verificationRepository;
        this.billingService = billingService;
    }

    @org.springframework.beans.factory.annotation.Value("${signup.trial-ip-limit:5}")
    private long trialIpLimitDefault;

    /**
     * The cap lives in runtime_settings ("trialiplimit") so admins can change
     * it from the Runtime Settings page without a restart; the
     * signup.trial-ip-limit property is only the fallback default.
     */
    private long trialIpLimit() {
        return runtimeSettingsRepository.findByName("trialiplimit")
            .map(s -> {
                try { return Long.parseLong(s.getValue().trim()); }
                catch (NumberFormatException e) { return trialIpLimitDefault; }
            })
            .orElse(trialIpLimitDefault);
    }

    /**
     * Trial-farming guard: once more than trialIpLimit() accounts exist from
     * the same signup IP, further accounts from that IP get no free trial and
     * no grace period — they can still sign up and pay.
     */
    private boolean isTrialBlocked(User user, jakarta.servlet.http.HttpServletRequest request) {
        String ip = user.getSignupIp() != null ? user.getSignupIp() : com.sftpmanager.util.RequestIp.of(request);
        if (ip == null || ip.isBlank()) return false;
        return userRepository.countBySignupIp(ip) > trialIpLimit();
    }

    // ── Helper: resolve email from OAuth2 or email session ──
    private String resolveEmail(OAuth2User principal, HttpSession session) {
        if (principal != null) {
            return principal.getAttribute("email");
        }
        if (session != null) {
            return (String) session.getAttribute("EMAIL_AUTH_USER");
        }
        return null;
    }

    // ── Helper: get current User from OAuth principal or email session ──
    // Locked and closed accounts resolve to empty, so callers reject them the
    // same way they reject an unauthenticated request.
    private Optional<User> currentUser(OAuth2User principal, HttpSession session) {
        String email = resolveEmail(principal, session);
        if (email == null) return Optional.empty();
        return userRepository.findByEmail(email)
            .filter(u -> !Boolean.TRUE.equals(u.getLocked()))
            .filter(u -> !Boolean.TRUE.equals(u.getAccountClosed()));
    }

    // Keep old signature for backward compat with portalUser helper


    // ── Me ──
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal OAuth2User principal, HttpSession session,
                                   jakarta.servlet.http.HttpServletRequest request) {
        // Support both Google OAuth and email/password auth
        String email, name, picture;

        if (principal != null) {
            // Google OAuth user
            email     = principal.getAttribute("email");
            name      = principal.getAttribute("name");
            picture   = principal.getAttribute("picture");
            String givenName  = principal.getAttribute("given_name");
            String familyName = principal.getAttribute("family_name");
            final String fe = email, fn = givenName, fl = familyName, fn2 = name;
            userRepository.findByEmail(email).orElseGet(() -> {
                User u = new User();
                u.setEmail(fe);
                u.setFirstName(fn != null ? fn : (fn2 != null ? fn2 : fe));
                u.setSurname(fl != null ? fl : "");
                u.setAuthType("GOOGLE");
                u.setCreatedBy("google-oauth");
                u.setLastUpdatedBy("google-oauth");
                u.setSignupIp(com.sftpmanager.util.RequestIp.of(request));
                User saved = userRepository.save(u);
                emailService.sendSignupNotification("New signup (Google)",
                    (saved.getFirstName() + " " + saved.getSurname()).trim(), fe, "Account created via Google sign-in");
                return saved;
            });
        } else {
            // Email/password user — check session
            String sessionEmail = (String) session.getAttribute("EMAIL_AUTH_USER");
            if (sessionEmail == null) return ResponseEntity.status(401).build();
            email   = sessionEmail;
            name    = null;
            picture = null;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        if (Boolean.TRUE.equals(user.getLocked()) || Boolean.TRUE.equals(user.getAccountClosed())) {
            session.invalidate();
            return ResponseEntity.status(401).build();
        }

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

        // For Google users only: send code if not yet verified
        // Email/password users get their code sent during login in PortalAuthController
        if (!emailVerified && principal != null) {
            String code = String.format("%06d", (int)(Math.random() * 1000000));
            EmailVerification ev = new EmailVerification();
            ev.setEmail(email);
            ev.setCode(code);
            ev.setVerified(false);
            ev.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
            verificationRepository.save(ev);
            emailService.sendVerificationCode(email, code);
        }

        boolean deactivated = Boolean.TRUE.equals(user.getServicesDeactivated());

        return ResponseEntity.ok(Map.of(
            "email",               email,
            "name",                name    != null ? name    : "",
            "picture",             picture != null ? picture : "",
            "userId",              user.getId(),
            "emailVerified",       emailVerified,
            "servicesDeactivated", deactivated
        ));
    }

    // ── Services ──
    @GetMapping("/services")
    public ResponseEntity<?> getServices(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        return currentUser(principal, session)
            .map(user -> ResponseEntity.ok(Map.of(
                "linked", true,
                "services", sftpServiceRepository.findByUserId(user.getId())
            )))
            .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<?> getService(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable Long id,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services")
    public ResponseEntity<?> createService(@AuthenticationPrincipal OAuth2User principal,
                                           @RequestBody SftpService service,
                                           HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            // Enforce the plan's server limit (null = unlimited)
            AccountControls plan = user.getAccountControls();
            if (plan != null && plan.getMaxServers() != null) {
                long existing = sftpServiceRepository.findByUserId(user.getId()).size();
                if (existing >= plan.getMaxServers()) {
                    return ResponseEntity.status(403).body(Map.of("error",
                        "Your " + plan.getPlan() + " plan allows a maximum of " + plan.getMaxServers()
                        + " SFTP service" + (plan.getMaxServers() == 1 ? "" : "s")
                        + ". Upgrade your plan to add more."));
                }
            }
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
                                           @RequestBody SftpService updated,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
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
                                           @PathVariable Long id,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> { sftpServiceRepository.delete(s); return ResponseEntity.noContent().build(); })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    // ── Service Accounts ──
    @GetMapping("/services/{svcId}/accounts")
    public ResponseEntity<?> getAccounts(@AuthenticationPrincipal OAuth2User principal,
                                         @PathVariable Long svcId,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> ResponseEntity.ok(accountRepository.findBySftpServiceId(svcId)))
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services/{svcId}/accounts")
    public ResponseEntity<?> createAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long svcId,
                                           @RequestBody SftpServiceAccount account,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
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
                                           @RequestBody SftpServiceAccount updated,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            accountRepository.findById(id)
                .filter(a -> a.getSftpService() != null && a.getSftpService().getId().equals(svcId))
                .map(a -> {
                    a.setUsername(updated.getUsername());
                    a.setEmail(updated.getEmail());
                    a.setAuthenticationType(updated.getAuthenticationType());
                    a.setPassword(updated.getPassword());
                    a.setPublicKey(updated.getPublicKey());
                    a.setEnabled(updated.getEnabled());
                    a.setPermissions(updated.getPermissions());
                    a.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(accountRepository.save(a));
                })
                .orElse(ResponseEntity.status(404).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable Long id,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            accountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id,
                                           HttpSession session) {
        return currentUser(principal, session).map(user -> {
            accountRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.status(401).build());
    }

    // ── IP Whitelist ──
    @GetMapping("/services/{svcId}/whitelist")
    public ResponseEntity<?> getWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                          @PathVariable Long svcId,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> ResponseEntity.ok(whitelistRepository.findBySftpServiceId(svcId)))
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services/{svcId}/whitelist")
    public ResponseEntity<?> createWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long svcId,
                                             @RequestBody SftpServiceIpWhitelist entry,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
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
                                             @RequestBody SftpServiceIpWhitelist updated,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
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
                                               @PathVariable Long id,
                                           HttpSession session) {
        return currentUser(principal, session).map(user ->
            whitelistRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/whitelist/{id}")
    public ResponseEntity<?> deleteWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long id,
                                           HttpSession session) {
        return currentUser(principal, session).map(user -> {
            whitelistRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.status(401).build());
    }

    // ── Email Verification ──

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@AuthenticationPrincipal OAuth2User principal,
                                        @RequestBody Map<String, Object> body,
                                           HttpSession session) {
        String _email = resolveEmail(principal, session);
        if (_email == null) return ResponseEntity.status(401).build();
        String email = _email;
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
    public ResponseEntity<?> resendCode(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        String _email = resolveEmail(principal, session);
        if (_email == null) return ResponseEntity.status(401).build();
        String email = _email;
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
    public ResponseEntity<?> getOnboardingData(@AuthenticationPrincipal OAuth2User principal, HttpSession session,
                                               jakarta.servlet.http.HttpServletRequest request) {
        String _email = resolveEmail(principal, session);
        if (_email == null) return ResponseEntity.status(401).build();
        String email = _email;

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();

        // Already onboarded
        if (Boolean.TRUE.equals(user.getOnboarded())) {
            return ResponseEntity.ok(Map.of("onboarded", true));
        }

        // Plans live in account_controls (name, description, monthly price, limits).
        // Trial plans are silently omitted for IPs that have farmed too many accounts.
        boolean trialBlocked = isTrialBlocked(user, request);
        List<AccountControls> plans = accountControlsRepository.findAll().stream()
            .filter(p -> !trialBlocked || p.getTrialDays() == null || p.getTrialDays() <= 0)
            .toList();

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
                                                @RequestBody Map<String, Object> body,
                                           HttpSession session,
                                           jakarta.servlet.http.HttpServletRequest request) {
        String _email = resolveEmail(principal, session);
        if (_email == null) return ResponseEntity.status(401).build();
        String email = _email;

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();

        // Set plan (an AccountControls row)
        Long planId = body.get("planId") != null ? Long.valueOf(body.get("planId").toString()) : null;
        if (planId != null) {
            accountControlsRepository.findById(planId).ifPresent(user::setAccountControls);
        }

        // Card saving now happens through /portal/api/billing before this call.
        // Trust the server-side state, not the request body.
        // trialBlocked = this IP has created too many accounts: no free trial,
        // no grace period — only a successful payment completes onboarding.
        // The error is deliberately non-descript.
        boolean trialBlocked = isTrialBlocked(user, request);
        String paymentWarning = null;
        AccountControls selectedPlan = user.getAccountControls();
        if (selectedPlan != null && selectedPlan.getTrialDays() != null && selectedPlan.getTrialDays() > 0) {
            if (trialBlocked) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unable to complete signup. Please contact support."));
            }
            // Trial plan — time-limited, free, never billed
            user.setTrialExpires(java.time.LocalDate.now().plusDays(selectedPlan.getTrialDays()));
            user.setPaidToDate(null);
            user.setServicesDeactivated(false);
        } else if (user.getCcPmId() != null) {
            // Paid plan with a saved card — charge the first month NOW.
            // Activation/paidToDate is set inside chargeFirstMonthIfDue on success.
            var outcome = billingService.chargeFirstMonthIfDue(user);
            if (outcome != null && !outcome.succeeded()) {
                if (trialBlocked) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unable to complete signup. Please contact support."));
                }
                // Payment failed — fall back to a 7 day grace period so the
                // user can fix their card before services are cut off.
                user.setTrialExpires(java.time.LocalDate.now().plusDays(7));
                paymentWarning = "Your card could not be charged (" + outcome.message()
                    + "). You have 7 days to update your payment details.";
            }
        } else {
            if (trialBlocked) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unable to complete signup. Please contact support."));
            }
            // Paid plan, no card yet — 7 day grace trial
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

        // Notify support of the completed signup
        String planName = selectedPlan != null ? selectedPlan.getPlan() : "no plan";
        String outcome;
        if (selectedPlan != null && selectedPlan.getTrialDays() != null && selectedPlan.getTrialDays() > 0) {
            outcome = "free trial, expires " + user.getTrialExpires();
        } else if (paymentWarning != null) {
            outcome = "FIRST PAYMENT FAILED — 7 day grace";
        } else if (user.getPaidToDate() != null) {
            outcome = "first month paid, paid to " + user.getPaidToDate();
        } else {
            outcome = "no card — grace trial expires " + user.getTrialExpires();
        }
        emailService.sendSignupNotification("Onboarding completed",
            (user.getFirstName() + " " + user.getSurname()).trim(), email,
            "Plan: " + planName + " — " + outcome);

        return ResponseEntity.ok(paymentWarning != null
            ? Map.of("success", true, "paymentWarning", paymentWarning)
            : Map.of("success", true));
    }

    // ── Account ──

    /** User closes their own account: flag it and end the session. */
    @PostMapping("/account/close")
    public ResponseEntity<?> closeAccount(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            user.setAccountClosed(true);
            user.setLastUpdatedBy(user.getEmail());
            userRepository.save(user);
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            session.invalidate(); // logs them out (both Google and email sessions)
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        String _email = resolveEmail(principal, session);
        if (_email == null) return ResponseEntity.status(401).build();
        String email = _email;
        return userRepository.findByEmail(email)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/account")
    public ResponseEntity<?> updateAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @RequestBody Map<String, Object> body,
                                           HttpSession session) {
        String _email = resolveEmail(principal, session);
        if (_email == null) return ResponseEntity.status(401).build();
        String email = _email;

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
            // Card changes go through /portal/api/billing — never through here.

            user.setLastUpdatedBy(email);
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }

}