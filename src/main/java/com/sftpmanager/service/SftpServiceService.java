package com.sftpmanager.service;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import com.sftpmanager.repository.SftpServiceRepository;
import com.sftpmanager.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SftpServiceService {

    private final SftpServiceRepository sftpServiceRepository;
    private final UserRepository userRepository;
    private final RuntimeSettingsRepository runtimeSettingsRepository;

    public SftpServiceService(SftpServiceRepository sftpServiceRepository, UserRepository userRepository,
                               RuntimeSettingsRepository runtimeSettingsRepository) {
        this.sftpServiceRepository = sftpServiceRepository;
        this.userRepository = userRepository;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
    }

    public List<SftpService> findAll() { return sftpServiceRepository.findAll(); }
    public List<SftpService> findByUserId(Long userId) { return sftpServiceRepository.findByUserId(userId); }
    public Optional<SftpService> findById(Long id) { return sftpServiceRepository.findById(id); }

    public SftpService save(SftpService service, Long userId) {
        if (userId != null) {
            userRepository.findById(userId).ifPresent(service::setUser);
        }
        // Auto-assign host from runtime settings, same as the customer portal.
        String host = runtimeSettingsRepository.findByName("sftphost001")
            .map(s -> s.getValue())
            .orElse("sftphost001.leederville.net");
        service.setHost(host);
        return sftpServiceRepository.save(service);
    }

    public SftpService update(Long id, SftpService updated, Long userId) {
        return sftpServiceRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setDescription(updated.getDescription());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            if (userId != null) {
                userRepository.findById(userId).ifPresent(existing::setUser);
            }
            return sftpServiceRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("SftpService not found: " + id));
    }

    public void deleteById(Long id) { sftpServiceRepository.deleteById(id); }
}
