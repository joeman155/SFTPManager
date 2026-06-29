package com.sftpmanager.service;

import com.sftpmanager.model.SftpServiceIpWhitelist;
import com.sftpmanager.repository.SftpServiceIpWhitelistRepository;
import com.sftpmanager.repository.SftpServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SftpServiceIpWhitelistService {

    private final SftpServiceIpWhitelistRepository repository;
    private final SftpServiceRepository sftpServiceRepository;

    public SftpServiceIpWhitelistService(SftpServiceIpWhitelistRepository repository, SftpServiceRepository sftpServiceRepository) {
        this.repository = repository;
        this.sftpServiceRepository = sftpServiceRepository;
    }

    public List<SftpServiceIpWhitelist> findAll() { return repository.findAll(); }
    public List<SftpServiceIpWhitelist> findBySftpServiceId(Long sftpServiceId) { return repository.findBySftpServiceId(sftpServiceId); }
    public Optional<SftpServiceIpWhitelist> findById(Long id) { return repository.findById(id); }

    public SftpServiceIpWhitelist save(SftpServiceIpWhitelist entry, Long sftpServiceId) {
        if (sftpServiceId != null) {
            sftpServiceRepository.findById(sftpServiceId).ifPresent(entry::setSftpService);
        }
        return repository.save(entry);
    }

    public SftpServiceIpWhitelist update(Long id, SftpServiceIpWhitelist updated, Long sftpServiceId) {
        return repository.findById(id).map(existing -> {
            existing.setIpAddress(updated.getIpAddress());
            existing.setEnabled(updated.getEnabled());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            if (sftpServiceId != null) {
                sftpServiceRepository.findById(sftpServiceId).ifPresent(existing::setSftpService);
            }
            return repository.save(existing);
        }).orElseThrow(() -> new RuntimeException("IpWhitelist not found: " + id));
    }

    public void deleteById(Long id) { repository.deleteById(id); }
}
