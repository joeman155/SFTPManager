package com.sftpmanager.service;

import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RuntimeSettingsService {

    private final RuntimeSettingsRepository repository;

    public RuntimeSettingsService(RuntimeSettingsRepository repository) {
        this.repository = repository;
    }


    public List<RuntimeSettings> findAll() { return repository.findAll(); }
    public Optional<RuntimeSettings> findById(Long id) { return repository.findById(id); }
    public Optional<RuntimeSettings> findByName(String name) { return repository.findByName(name); }
    public RuntimeSettings save(RuntimeSettings setting) { return repository.save(setting); }
    public void deleteById(Long id) { repository.deleteById(id); }

    public RuntimeSettings update(Long id, RuntimeSettings updated) {
        return repository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setValue(updated.getValue());
            existing.setLastUpdatedBy(updated.getLastUpdatedBy());
            return repository.save(existing);
        }).orElseThrow(() -> new RuntimeException("RuntimeSettings not found: " + id));
    }
}
