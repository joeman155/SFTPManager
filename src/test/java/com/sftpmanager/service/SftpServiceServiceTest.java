package com.sftpmanager.service;

import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import com.sftpmanager.repository.SftpServiceAccountRepository;
import com.sftpmanager.repository.SftpServiceIpWhitelistRepository;
import com.sftpmanager.repository.SftpServiceRepository;
import com.sftpmanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpServiceServiceTest {

    @Mock private SftpServiceRepository sftpServiceRepository;
    @Mock private UserRepository userRepository;
    @Mock private RuntimeSettingsRepository runtimeSettingsRepository;
    @Mock private SftpServiceAccountRepository sftpServiceAccountRepository;
    @Mock private SftpServiceIpWhitelistRepository sftpServiceIpWhitelistRepository;

    private SftpServiceService service;

    @BeforeEach
    void setUp() {
        service = new SftpServiceService(sftpServiceRepository, userRepository, runtimeSettingsRepository,
                sftpServiceAccountRepository, sftpServiceIpWhitelistRepository);
    }

    @Test
    void saveAssignsHostFromRuntimeSettings() {
        RuntimeSettings setting = new RuntimeSettings();
        setting.setName("sftphost001");
        setting.setValue("custom-host.example.net");
        when(runtimeSettingsRepository.findByName("sftphost001")).thenReturn(Optional.of(setting));
        when(sftpServiceRepository.save(any(SftpService.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpService saved = service.save(new SftpService(), null);

        assertThat(saved.getHost()).isEqualTo("custom-host.example.net");
    }

    @Test
    void saveFallsBackToDefaultHostWhenSettingMissing() {
        when(runtimeSettingsRepository.findByName("sftphost001")).thenReturn(Optional.empty());
        when(sftpServiceRepository.save(any(SftpService.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpService saved = service.save(new SftpService(), null);

        assertThat(saved.getHost()).isEqualTo("sftphost001.proficiens.io");
    }

    @Test
    void saveAttachesOwningUser() {
        User owner = new User();
        owner.setId(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(owner));
        when(runtimeSettingsRepository.findByName("sftphost001")).thenReturn(Optional.empty());
        when(sftpServiceRepository.save(any(SftpService.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpService saved = service.save(new SftpService(), 9L);

        assertThat(saved.getUser()).isEqualTo(owner);
    }

    @Test
    void updateCopiesFields() {
        SftpService existing = new SftpService();
        when(sftpServiceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sftpServiceRepository.save(any(SftpService.class))).thenAnswer(inv -> inv.getArgument(0));
        SftpService updated = new SftpService();
        updated.setName("prod-sftp");
        updated.setDescription("production");
        updated.setLastUpdatedBy("admin");

        SftpService result = service.update(1L, updated, null);

        assertThat(result.getName()).isEqualTo("prod-sftp");
        assertThat(result.getDescription()).isEqualTo("production");
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateThrowsWhenServiceNotFound() {
        when(sftpServiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new SftpService(), null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found: 99");
    }

    @Test
    void deleteDelegatesToRepository() {
        when(sftpServiceAccountRepository.findBySftpServiceId(4L)).thenReturn(Collections.emptyList());
        when(sftpServiceIpWhitelistRepository.findBySftpServiceId(4L)).thenReturn(Collections.emptyList());

        service.deleteById(4L);

        verify(sftpServiceRepository).deleteById(4L);
    }

    @Test
    void deleteCascadesAccountsAndWhitelistFirst() {
        com.sftpmanager.model.SftpServiceAccount account = new com.sftpmanager.model.SftpServiceAccount();
        com.sftpmanager.model.SftpServiceIpWhitelist rule = new com.sftpmanager.model.SftpServiceIpWhitelist();
        when(sftpServiceAccountRepository.findBySftpServiceId(4L)).thenReturn(java.util.List.of(account));
        when(sftpServiceIpWhitelistRepository.findBySftpServiceId(4L)).thenReturn(java.util.List.of(rule));

        service.deleteById(4L);

        verify(sftpServiceAccountRepository).deleteAll(java.util.List.of(account));
        verify(sftpServiceIpWhitelistRepository).deleteAll(java.util.List.of(rule));
        verify(sftpServiceRepository).deleteById(4L);
    }
}
