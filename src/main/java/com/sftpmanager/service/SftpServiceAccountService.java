package com.sftpmanager.service;

import com.sftpmanager.model.SftpServiceAccount;
import com.sftpmanager.repository.SftpServiceAccountRepository;
import com.sftpmanager.repository.SftpServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SftpServiceAccountService {

    private final SftpServiceAccountRepository repository;
    private final SftpServiceRepository sftpServiceRepository;

    public SftpServiceAccountService(SftpServiceAccountRepository repository, SftpServiceRepository sftpServiceRepository) {
        this.repository = repository;
        this.sftpServiceRepository = sftpServiceRepository;
    }

    public List<SftpServiceAccount> findAll() { return repository.findAll(); }
    public List<SftpServiceAccount> findBySftpServiceId(Long sftpServiceId) { return repository.findBySftpServiceId(sftpServiceId); }
    public Optional<SftpServiceAccount> findById(Long id) { return repository.findById(id); }

    public SftpServiceAccount save(SftpServiceAccount account, Long sftpServiceId) {
        if (sftpServiceId != null) {
            sftpServiceRepository.findById(sftpServiceId).ifPresent(account::setSftpService);
        }
        return repository.save(account);
    }

    public SftpServiceAccount update(Long id, SftpServiceAccount updated, Long sftpServiceId) {
        return repository.findById(id).map(existing -> {
            existing.setAuthenticationType(updated.getAuthenticationType());
            existing.setUsername(updated.getUsername());
            existing.setEmail(updated.getEmail());
            existing.setPassword(updated.getPassword());
            existing.setPublicKey(updated.getPublicKey());
            existing.setEnabled(updated.getEnabled());
            existing.setPermissions(updated.getPermissions());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            if (sftpServiceId != null) {
                sftpServiceRepository.findById(sftpServiceId).ifPresent(existing::setSftpService);
            }
            return repository.save(existing);
        }).orElseThrow(() -> new RuntimeException("SftpServiceAccount not found: " + id));
    }

    public void deleteById(Long id) { repository.deleteById(id); }
}
