package com.sftpmanager.service;

import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeConfigServiceTest {

    @Mock private RuntimeSettingsRepository repository;

    private RuntimeConfigService config;

    @BeforeEach
    void setUp() {
        config = new RuntimeConfigService(repository);
    }

    private RuntimeSettings settingWithValue(String value) {
        RuntimeSettings s = new RuntimeSettings();
        s.setValue(value);
        return s;
    }

    // ── getString ──

    @Test
    void getStringReturnsStoredValue() {
        when(repository.findByName("billing.currency")).thenReturn(Optional.of(settingWithValue("usd")));

        assertThat(config.getString("billing.currency", "aud")).isEqualTo("usd");
    }

    @Test
    void getStringFallsBackWhenRowMissing() {
        when(repository.findByName("billing.currency")).thenReturn(Optional.empty());

        assertThat(config.getString("billing.currency", "aud")).isEqualTo("aud");
    }

    @Test
    void getStringFallsBackWhenValueBlank() {
        when(repository.findByName("billing.currency")).thenReturn(Optional.of(settingWithValue("   ")));

        assertThat(config.getString("billing.currency", "aud")).isEqualTo("aud");
    }

    @Test
    void getStringTrimsWhitespace() {
        when(repository.findByName("billing.currency")).thenReturn(Optional.of(settingWithValue("  usd  ")));

        assertThat(config.getString("billing.currency", "aud")).isEqualTo("usd");
    }

    // ── getLong ──

    @Test
    void getLongParsesStoredNumber() {
        when(repository.findByName("billing.max-charge-cents")).thenReturn(Optional.of(settingWithValue("75000")));

        assertThat(config.getLong("billing.max-charge-cents", 50000L)).isEqualTo(75000L);
    }

    @Test
    void getLongFallsBackWhenRowMissing() {
        when(repository.findByName("billing.max-charge-cents")).thenReturn(Optional.empty());

        assertThat(config.getLong("billing.max-charge-cents", 50000L)).isEqualTo(50000L);
    }

    @Test
    void getLongFallsBackOnNonNumericValue() {
        when(repository.findByName("billing.max-charge-cents")).thenReturn(Optional.of(settingWithValue("not-a-number")));

        assertThat(config.getLong("billing.max-charge-cents", 50000L)).isEqualTo(50000L);
    }

    // ── getBoolean ──

    @Test
    void getBooleanParsesTrueCaseInsensitively() {
        when(repository.findByName("billing.enabled")).thenReturn(Optional.of(settingWithValue("TRUE")));

        assertThat(config.getBoolean("billing.enabled", false)).isTrue();
    }

    @Test
    void getBooleanParsesFalseCaseInsensitively() {
        when(repository.findByName("billing.enabled")).thenReturn(Optional.of(settingWithValue("False")));

        assertThat(config.getBoolean("billing.enabled", true)).isFalse();
    }

    @Test
    void getBooleanFallsBackWhenRowMissing() {
        when(repository.findByName("billing.enabled")).thenReturn(Optional.empty());

        assertThat(config.getBoolean("billing.enabled", true)).isTrue();
    }

    @Test
    void getBooleanFallsBackOnGarbageValue() {
        when(repository.findByName("billing.enabled")).thenReturn(Optional.of(settingWithValue("yes-ish")));

        assertThat(config.getBoolean("billing.enabled", true)).isTrue();
    }
}
