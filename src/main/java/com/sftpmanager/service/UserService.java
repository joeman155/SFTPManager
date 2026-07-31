package com.sftpmanager.service;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.model.Payment;
import com.sftpmanager.model.PaymentArchive;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.AccountControlsRepository;
import com.sftpmanager.repository.EmailVerificationRepository;
import com.sftpmanager.repository.PasswordResetRepository;
import com.sftpmanager.repository.PaymentArchiveRepository;
import com.sftpmanager.repository.PaymentRepository;
import com.sftpmanager.repository.PortalUserRepository;
import com.sftpmanager.repository.SftpServiceRepository;
import com.sftpmanager.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final AccountControlsRepository accountControlsRepository;
    private final BillingService billingService;
    private final SftpServiceRepository sftpServiceRepository;
    private final SftpServiceService sftpServiceService;
    private final PaymentRepository paymentRepository;
    private final PaymentArchiveRepository paymentArchiveRepository;
    private final PortalUserRepository portalUserRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, AccountControlsRepository accountControlsRepository,
                       BillingService billingService, SftpServiceRepository sftpServiceRepository,
                       SftpServiceService sftpServiceService, PaymentRepository paymentRepository,
                       PaymentArchiveRepository paymentArchiveRepository,
                       PortalUserRepository portalUserRepository,
                       EmailVerificationRepository emailVerificationRepository,
                       PasswordResetRepository passwordResetRepository) {
        this.userRepository = userRepository;
        this.accountControlsRepository = accountControlsRepository;
        this.billingService = billingService;
        this.sftpServiceRepository = sftpServiceRepository;
        this.sftpServiceService = sftpServiceService;
        this.paymentRepository = paymentRepository;
        this.paymentArchiveRepository = paymentArchiveRepository;
        this.portalUserRepository = portalUserRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordResetRepository = passwordResetRepository;
    }

    public List<User> findAll() { return userRepository.findAll(); }
    public Optional<User> findById(Long id) { return userRepository.findById(id); }
    public Optional<User> findByEmail(String email) { return userRepository.findByEmail(email); }
    public boolean existsByEmail(String email) { return userRepository.existsByEmail(email); }

    public User save(User user, Long accountControlsId) {
        if (accountControlsId != null) {
            accountControlsRepository.findById(accountControlsId).ifPresent(user::setAccountControls);
        }
        return userRepository.save(user);
    }

    // ── update() and its two private helpers ────────────────────────────────
    //
    // update() only does two things to `existing`, in order:
    //   1. applyProfileFields — copy the plain fields (name, address, etc.)
    //   2. applyPlanChange    — handle a plan switch, IF one was requested
    // then saves once. Splitting it this way means applyPlanChange can be
    // read top-to-bottom on its own, with no nesting to track.

    public User update(Long id, User updated, Long accountControlsId, String adminEmail) {
        return userRepository.findById(id).map(existing -> {
            applyProfileFields(existing, updated);
            applyPlanChange(existing, accountControlsId, adminEmail);
            return userRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    /** Plain field copy — no billing, no branching. */
    private void applyProfileFields(User existing, User updated) {
        existing.setFirstName(updated.getFirstName());
        existing.setSurname(updated.getSurname());
        existing.setCompany(updated.getCompany());
        existing.setCompanySize(updated.getCompanySize());
        existing.setAddressLine1(updated.getAddressLine1());
        existing.setAddressLine2(updated.getAddressLine2());
        existing.setState(updated.getState());
        existing.setPostcode(updated.getPostcode());
        existing.setCountry(updated.getCountry());
        existing.setPhone(updated.getPhone());
        existing.setEmail(updated.getEmail());
        // Card data is managed exclusively via BillingService — admins
        // can no longer write card fields through user updates.
        existing.setLastUpdatedBy(updated.getLastUpdatedBy());
    }

    /**
     * Applies a plan change to `existing`, BEFORE the caller saves it — this
     * method needs to see the OLD plan still on `existing` so it can compare
     * against the new one.
     *
     * Written as a series of early returns (guard clauses) instead of nested
     * if/else, so each paragraph below can be read as one standalone
     * question answered in order — nothing here is nested inside anything
     * else:
     *
     *   1. Did the admin touch the plan field at all?
     *   2. Does the chosen id match a real plan?
     *   3. Is it actually a DIFFERENT plan from the one they already have?
     *   4. Is the new plan a free TRIAL, or a PAID plan?
     *
     * Only question 4's two outcomes contain any real logic; everything
     * above it is just "is there even anything to do?"
     */
    private void applyPlanChange(User existing, Long accountControlsId, String adminEmail) {
        // Q1: did the admin touch the plan field at all?
        if (accountControlsId == null) {
            return; // no — nothing to do
        }

        AccountControls oldPlan = existing.getAccountControls();
        AccountControls newPlan = accountControlsRepository.findById(accountControlsId).orElse(null);

        // Q2: does the chosen id match a real plan?
        if (newPlan == null) {
            return; // no — leave the user's plan untouched
        }

        // Q3: is it actually a DIFFERENT plan from the one they already have?
        boolean planActuallyChanged = oldPlan == null || !newPlan.getId().equals(oldPlan.getId());
        if (!planActuallyChanged) {
            existing.setAccountControls(newPlan); // same plan re-selected — no billing action
            return;
        }

        // Q4: is the new plan a free TRIAL, or a PAID plan?
        boolean isTrialPlan = newPlan.getTrialDays() != null && newPlan.getTrialDays() > 0;
        if (isTrialPlan) {
            // Assigned a trial plan: start its clock
            existing.setAccountControls(newPlan);
            existing.setTrialExpires(LocalDate.now().plusDays(newPlan.getTrialDays()));
            existing.setPaidToDate(null);
            existing.setServicesDeactivated(false);
            return;
        }

        // Priced plan: same billing rules as the portal's Change Plan flow
        // (upgrade = prorated charge now; not currently paid up = first-month
        // charge if a card is on file) — EXCEPT downgrades, which admins can
        // apply immediately with no charge (allowDirectDowngrade=true); the
        // email-support loop is only for self-service customers.
        billingService.switchPaidPlan(existing, newPlan, "ADMIN:" + adminEmail, true);
    }

    /**
     * Services (and their accounts/whitelist rows), payments, and the Google-portal
     * link all carry a FK back to this user, so they must be cleared first or the
     * delete fails silently on a DB constraint violation.
     *
     * Payments are archived (not just deleted) — financial records typically need
     * to survive a customer deletion for accounting/tax purposes, so each Payment
     * is snapshotted into payments_arc (with the user's identity captured at time
     * of archiving, since payments_arc intentionally has no FK back to users) before
     * the original row is removed.
     *
     * Email verification codes and password-reset tokens aren't linked by FK at
     * all — they're keyed by the raw email address, since they exist to serve
     * requests made before/outside of an authenticated session. Left behind,
     * a leftover "verified" row lets a re-created account with the same email
     * skip email verification entirely, so they must be cleared by email too.
     */
    public void deleteById(Long id) {
        User user = userRepository.findById(id).orElse(null);
        sftpServiceRepository.findByUserId(id).forEach(svc -> sftpServiceService.deleteById(svc.getId()));

        List<Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(id);
        if (user != null && !payments.isEmpty()) {
            paymentArchiveRepository.saveAll(payments.stream().map(p -> archiveOf(p, user)).toList());
        }
        paymentRepository.deleteAll(payments);

        portalUserRepository.findByUserId(id).ifPresent(portalUserRepository::delete);
        if (user != null) {
            emailVerificationRepository.deleteAll(emailVerificationRepository.findByEmail(user.getEmail()));
            passwordResetRepository.deleteAll(passwordResetRepository.findByEmail(user.getEmail()));
        }
        userRepository.deleteById(id);
    }

    private PaymentArchive archiveOf(Payment p, User user) {
        PaymentArchive a = new PaymentArchive();
        a.setOriginalPaymentId(p.getId());
        a.setUserId(user.getId());
        a.setEmail(user.getEmail());
        a.setFirstname(user.getFirstName());
        a.setSurname(user.getSurname());
        a.setMobileNumber(user.getPhone());
        a.setAmountCents(p.getAmountCents());
        a.setCurrency(p.getCurrency());
        a.setStatus(p.getStatus());
        a.setCardUsed(p.getCardUsed());
        a.setCardDisplay(p.getCardDisplay());
        a.setDescription(p.getDescription());
        a.setGatewayPaymentId(p.getGatewayPaymentId());
        a.setFailureReason(p.getFailureReason());
        a.setInitiatedBy(p.getInitiatedBy());
        a.setPaymentCreatedAt(p.getCreatedAt());
        return a;
    }

    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLastUpdatedBy("admin-reset");
        userRepository.save(user);
    }
}
