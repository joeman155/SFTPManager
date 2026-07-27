package com.sftpmanager.service;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.SftpServiceIpWhitelist;
import com.sftpmanager.repository.SftpServiceIpWhitelistRepository;
import com.sftpmanager.repository.SftpServiceRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpServiceIpWhitelistServiceTest {

    @Mock private SftpServiceIpWhitelistRepository repository;
    @Mock private SftpServiceRepository sftpServiceRepository;

    private SftpServiceIpWhitelistService service;

    @BeforeEach
    void setUp() {
        service = new SftpServiceIpWhitelistService(repository, sftpServiceRepository);
    }

    @Test
    void saveAttachesParentSftpService() {
        SftpService sftp = new SftpService();
        when(sftpServiceRepository.findById(2L)).thenReturn(Optional.of(sftp));
        when(repository.save(any(SftpServiceIpWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpServiceIpWhitelist saved = service.save(new SftpServiceIpWhitelist(), 2L);

        assertThat(saved.getSftpService()).isEqualTo(sftp);
    }

    @Test
    void saveWithoutServiceIdSkipsLookup() {
        when(repository.save(any(SftpServiceIpWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(new SftpServiceIpWhitelist(), null);

        verifyNoInteractions(sftpServiceRepository);
    }

    @Test
    void updateCopiesFields() {
        SftpServiceIpWhitelist existing = new SftpServiceIpWhitelist();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(SftpServiceIpWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpServiceIpWhitelist updated = new SftpServiceIpWhitelist();
        updated.setIpAddress("203.0.113.7");
        updated.setEnabled(true);
        updated.setLastUpdatedBy("admin");

        SftpServiceIpWhitelist result = service.update(1L, updated, null);

        assertThat(result.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateThrowsWhenEntryNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new SftpServiceIpWhitelist(), null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found: 99");
    }

    @Test
    void deleteDelegatesToRepository() {
        service.deleteById(5L);
        verify(repository).deleteById(5L);
    }
}
