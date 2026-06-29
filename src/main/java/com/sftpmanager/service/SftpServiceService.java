package com.sftpmanager.service;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.User;
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

    public SftpServiceService(SftpServiceRepository sftpServiceRepository, UserRepository userRepository) {
        this.sftpServiceRepository = sftpServiceRepository;
        this.userRepository = userRepository;
    }

    public List<SftpService> findAll() { return sftpServiceRepository.findAll(); }
    public List<SftpService> findByUserId(Long userId) { return sftpServiceRepository.findByUserId(userId); }
    public Optional<SftpService> findById(Long id) { return sftpServiceRepository.findById(id); }

    public SftpService save(SftpService service, Long userId) {
        if (userId != null) {
            userRepository.findById(userId).ifPresent(service::setUser);
        }
        return sftpServiceRepository.save(service);
    }

    public SftpService update(Long id, SftpService updated, Long userId) {
        return sftpServiceRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setHost(updated.getHost());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            if (userId != null) {
                userRepository.findById(userId).ifPresent(existing::setUser);
            }
            return sftpServiceRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("SftpService not found: " + id));
    }

    public void deleteById(Long id) { sftpServiceRepository.deleteById(id); }
}
