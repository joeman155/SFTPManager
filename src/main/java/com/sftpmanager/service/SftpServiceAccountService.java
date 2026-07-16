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
    private final SftpCredentialService credentialService;

    public SftpServiceAccountService(SftpServiceAccountRepository repository,
                                     SftpServiceRepository sftpServiceRepository,
                                     SftpCredentialService credentialService) {
        this.repository = repository;
        this.sftpServiceRepository = sftpServiceRepository;
        this.credentialService = credentialService;
    }

    public List<SftpServiceAccount> findAll() { return repository.findAll(); }
    public List<SftpServiceAccount> findBySftpServiceId(Long sftpServiceId) { return repository.findBySftpServiceId(sftpServiceId); }
    public Optional<SftpServiceAccount> findById(Long id) { return repository.findById(id); }

    public SftpServiceAccount save(SftpServiceAccount account, Long sftpServiceId) {
        String err = credentialService.usernameTakenError(account.getUsername(), null);
        if (err != null) throw new IllegalArgumentException(err);
        if (sftpServiceId != null) {
            sftpServiceRepository.findById(sftpServiceId).ifPresent(account::setSftpService);
        }
        credentialService.applyCredentials(account, account.getPassword(), account.getPublicKey());
        return repository.save(account);
    }

    public SftpServiceAccount update(Long id, SftpServiceAccount updated, Long sftpServiceId) {
        String err = credentialService.usernameTakenError(updated.getUsername(), id);
        if (err != null) throw new IllegalArgumentException(err);
        return repository.findById(id).map(existing -> {
            existing.setAuthenticationType(updated.getAuthenticationType());
            existing.setUsername(updated.getUsername());
            existing.setEmail(updated.getEmail());
            credentialService.applyCredentials(existing, updated.getPassword(), updated.getPublicKey());
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
