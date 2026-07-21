package com.sftpmanager.service;

import com.sftpmanager.model.User;
import com.sftpmanager.repository.AccountControlsRepository;
import com.sftpmanager.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public User update(Long id, User updated, Long accountControlsId, String adminEmail) {
        return userRepository.findById(id).map(existing -> {
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

            // Plan change is handled BEFORE the save below, using the OLD plan
            // still on `existing` — switchPaidPlan needs to see the prior plan
            // to decide same-plan / upgrade-proration / downgrade correctly.
            if (accountControlsId != null) {
                var oldPlan = existing.getAccountControls();
                var newPlan = accountControlsRepository.findById(accountControlsId).orElse(null);
                if (newPlan != null && (oldPlan == null || !newPlan.getId().equals(oldPlan.getId()))) {
                    if (newPlan.getTrialDays() != null && newPlan.getTrialDays() > 0) {
                        // Assigned a trial plan: start its clock
                        existing.setAccountControls(newPlan);
                        existing.setTrialExpires(java.time.LocalDate.now().plusDays(newPlan.getTrialDays()));
                        existing.setPaidToDate(null);
                        existing.setServicesDeactivated(false);
                    } else {
                        // Priced plan: same billing rules as the portal's Change
                        // Plan flow (upgrade = prorated charge now; not currently
                        // paid up = first-month charge if a card is on file) —
                        // EXCEPT downgrades, which admins can apply immediately
                        // with no charge (allowDirectDowngrade=true); the
                        // email-support loop is only for self-service customers.
                        billingService.switchPaidPlan(existing, newPlan, "ADMIN:" + adminEmail, true);
                    }
                } else if (newPlan != null) {
                    existing.setAccountControls(newPlan); // same plan re-selected — no billing action
                }
            }

            return userRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public void deleteById(Long id) { userRepository.deleteById(id); }
}
