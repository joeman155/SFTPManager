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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeSettingsServiceTest {

    @Mock private RuntimeSettingsRepository repository;

    private RuntimeSettingsService service;

    @BeforeEach
    void setUp() {
        service = new RuntimeSettingsService(repository);
    }

    @Test
    void findByNameDelegatesToRepository() {
        RuntimeSettings setting = new RuntimeSettings();
        setting.setName("sftphost001");
        when(repository.findByName("sftphost001")).thenReturn(Optional.of(setting));

        assertThat(service.findByName("sftphost001")).contains(setting);
    }

    @Test
    void updateCopiesNameValueAndAuditFields() {
        RuntimeSettings existing = new RuntimeSettings();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(RuntimeSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        RuntimeSettings updated = new RuntimeSettings();
        updated.setName("welcomeemail");
        updated.setValue("<html>hi {{firstName}}</html>");
        updated.setDescription("Sent to new users on signup");
        updated.setLastUpdatedBy("admin");

        RuntimeSettings result = service.update(1L, updated);

        assertThat(result.getName()).isEqualTo("welcomeemail");
        assertThat(result.getValue()).isEqualTo("<html>hi {{firstName}}</html>");
        assertThat(result.getDescription()).isEqualTo("Sent to new users on signup");
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateThrowsWhenSettingNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new RuntimeSettings()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found: 99");
    }

    @Test
    void deleteDelegatesToRepository() {
        service.deleteById(3L);
        verify(repository).deleteById(3L);
    }
}
