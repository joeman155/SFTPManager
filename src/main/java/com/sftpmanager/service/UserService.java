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

    public User update(Long id, User updated, Long accountControlsId) {
        return userRepository.findById(id).map(existing -> {
            Long oldPlanId = existing.getAccountControls() != null ? existing.getAccountControls().getId() : null;
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
            if (accountControlsId != null) {
                accountControlsRepository.findById(accountControlsId).ifPresent(existing::setAccountControls);
            }
            User saved = userRepository.save(existing);

            // Admin changed the user's plan — apply the same billing rules as
            // the portal's Change Plan flow, so the account state stays honest.
            Long newPlanId = saved.getAccountControls() != null ? saved.getAccountControls().getId() : null;
            if (newPlanId != null && !newPlanId.equals(oldPlanId)) {
                var plan = saved.getAccountControls();
                if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
                    // Assigned a trial plan: start its clock
                    saved.setTrialExpires(java.time.LocalDate.now().plusDays(plan.getTrialDays()));
                    saved.setPaidToDate(null);
                    saved.setServicesDeactivated(false);
                    saved = userRepository.save(saved);
                } else {
                    // Priced plan: charge the first month now if unpaid and a
                    // card is on file (activation happens inside on success).
                    // No card / charge failed => user keeps their current
                    // trial state; use Mark Paid in the billing panel for
                    // off-platform payments.
                    billingService.chargeFirstMonthIfDue(saved);
                }
            }
            return saved;
        }).orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public void deleteById(Long id) { userRepository.deleteById(id); }
}
