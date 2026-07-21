package com.sftpmanager.service;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.AccountControlsRepository;
import com.sftpmanager.repository.UserRepository;
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

    public UserService(UserRepository userRepository, AccountControlsRepository accountControlsRepository,
                       BillingService billingService) {
        this.userRepository = userRepository;
        this.accountControlsRepository = accountControlsRepository;
        this.billingService = billingService;
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

    public void deleteById(Long id) { userRepository.deleteById(id); }
}
