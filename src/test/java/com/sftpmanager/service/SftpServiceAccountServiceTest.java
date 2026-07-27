package com.sftpmanager.service;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.SftpServiceAccount;
import com.sftpmanager.repository.SftpServiceAccountRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpServiceAccountServiceTest {

    @Mock private SftpServiceAccountRepository repository;
    @Mock private SftpServiceRepository sftpServiceRepository;
    @Mock private SftpCredentialService credentialService;

    private SftpServiceAccountService service;

    @BeforeEach
    void setUp() {
        service = new SftpServiceAccountService(repository, sftpServiceRepository, credentialService);
    }

    private SftpServiceAccount account(String username) {
        SftpServiceAccount a = new SftpServiceAccount();
        a.setUsername(username);
        a.setPassword("plaintext");
        a.setPublicKey("ssh-rsa AAAA");
        a.setEnabled(true);
        a.setAuthenticationType("PASSWORD");
        return a;
    }

    // ── save ──

    @Test
    void saveRejectsTakenUsername() {
        when(credentialService.usernameTakenError("alice", null)).thenReturn("taken");

        assertThatThrownBy(() -> service.save(account("alice"), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("taken");
        verify(repository, never()).save(any());
    }

    @Test
    void saveAttachesServiceAndAppliesCredentials() {
        SftpService sftp = new SftpService();
        when(credentialService.usernameTakenError("alice", null)).thenReturn(null);
        when(sftpServiceRepository.findById(1L)).thenReturn(Optional.of(sftp));
        when(repository.save(any(SftpServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        SftpServiceAccount a = account("alice");

        SftpServiceAccount saved = service.save(a, 1L);

        assertThat(saved.getSftpService()).isEqualTo(sftp);
        verify(credentialService).applyCredentials(a, "plaintext", "ssh-rsa AAAA");
    }

    @Test
    void saveWithoutServiceIdSkipsAttachment() {
        when(credentialService.usernameTakenError("alice", null)).thenReturn(null);
        when(repository.save(any(SftpServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpServiceAccount saved = service.save(account("alice"), null);

        assertThat(saved.getSftpService()).isNull();
    }

    // ── update ──

    @Test
    void updateRejectsUsernameTakenByAnotherAccount() {
        when(credentialService.usernameTakenError("alice", 5L)).thenReturn("taken");

        assertThatThrownBy(() -> service.update(5L, account("alice"), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateThrowsWhenAccountNotFound() {
        when(credentialService.usernameTakenError("alice", 99L)).thenReturn(null);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, account("alice"), null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found: 99");
    }

    @Test
    void updateCopiesFieldsAndAppliesCredentials() {
        SftpServiceAccount existing = new SftpServiceAccount();
        existing.setUsername("old");
        when(credentialService.usernameTakenError("alice", 5L)).thenReturn(null);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(SftpServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        SftpServiceAccount updated = account("alice");
        updated.setEmail("alice@example.com");
        updated.setPermissions("rw");
        updated.setLastUpdatedBy("admin");

        SftpServiceAccount result = service.update(5L, updated, null);

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getPermissions()).isEqualTo("rw");
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin");
        verify(credentialService).applyCredentials(existing, "plaintext", "ssh-rsa AAAA");
    }

    @Test
    void deleteDelegatesToRepository() {
        service.deleteById(3L);
        verify(repository).deleteById(3L);
    }
}
