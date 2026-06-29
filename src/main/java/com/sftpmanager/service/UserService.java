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

    public UserService(UserRepository userRepository, AccountControlsRepository accountControlsRepository) {
        this.userRepository = userRepository;
        this.accountControlsRepository = accountControlsRepository;
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
            existing.setCcNumber(updated.getCcNumber());
            existing.setCcName(updated.getCcName());
            existing.setCcExpiry(updated.getCcExpiry());
            existing.setBackupCcNumber(updated.getBackupCcNumber());
            existing.setBackupCcName(updated.getBackupCcName());
            existing.setBackupCcExpiry(updated.getBackupCcExpiry());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            if (accountControlsId != null) {
                accountControlsRepository.findById(accountControlsId).ifPresent(existing::setAccountControls);
            }
            return userRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public void deleteById(Long id) { userRepository.deleteById(id); }
}
