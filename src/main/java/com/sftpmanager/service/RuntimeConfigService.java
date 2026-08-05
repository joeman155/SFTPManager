package com.sftpmanager.service;

import com.sftpmanager.repository.RuntimeSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Typed reads of admin-tunable settings stored in runtime_settings — the
 * live, no-deploy-needed alternative to @Value-bound application.properties
 * for values that are business decisions rather than secrets or
 * infrastructure. See DataInitialiser for the seeded defaults, and the
 * Runtime Settings screen in the admin UI for editing them.
 *
 * Every read hits the database fresh (no caching), so a change an admin
 * makes takes effect on the very next call — including mid-request.
 */
@Service
public class RuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigService.class);

    private final RuntimeSettingsRepository repository;

    public RuntimeConfigService(RuntimeSettingsRepository repository) {
        this.repository = repository;
    }

    public String getString(String name, String defaultValue) {
        return repository.findByName(name)
            .map(s -> s.getValue() != null ? s.getValue().trim() : "")
            .filter(v -> !v.isBlank())
            .orElse(defaultValue);
    }

    public long getLong(String name, long defaultValue) {
        String raw = rawValue(name);
        if (raw == null) return defaultValue;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("Runtime setting '{}' has non-numeric value '{}', using default {}", name, raw, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        String raw = rawValue(name);
        if (raw == null) return defaultValue;
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        log.warn("Runtime setting '{}' has non-boolean value '{}', using default {}", name, raw, defaultValue);
        return defaultValue;
    }

    private String rawValue(String name) {
        return repository.findByName(name)
            .map(s -> s.getValue() != null ? s.getValue().trim() : "")
            .filter(v -> !v.isBlank())
            .orElse(null);
    }
}
