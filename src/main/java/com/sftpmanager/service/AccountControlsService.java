package com.sftpmanager.service;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.repository.AccountControlsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AccountControlsService {

    private final AccountControlsRepository repository;

    public AccountControlsService(AccountControlsRepository repository) {
        this.repository = repository;
    }


    public List<AccountControls> findAll() { return repository.findAll(); }
    public Optional<AccountControls> findById(Long id) { return repository.findById(id); }
    public AccountControls save(AccountControls controls) { return repository.save(controls); }
    public void deleteById(Long id) { repository.deleteById(id); }

    public AccountControls update(Long id, AccountControls updated) {
        return repository.findById(id).map(existing -> {
            existing.setPlan(updated.getPlan());
            existing.setDescription(updated.getDescription());
            existing.setMonthlyPriceCents(updated.getMonthlyPriceCents());
            existing.setMaxUsers(updated.getMaxUsers());
            existing.setMaxServers(updated.getMaxServers());
            existing.setTrialDays(updated.getTrialDays());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            return repository.save(existing);
        }).orElseThrow(() -> new RuntimeException("AccountControls not found: " + id));
    }
}
