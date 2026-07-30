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

    private SftpService sftpService(long id) {
        SftpService s = new SftpService();
        s.setId(id);
        return s;
    }

    // ── save ──

    @Test
    void saveRejectsMissingServiceId() {
        assertThatThrownBy(() -> service.save(account("alice"), null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void saveRejectsUnknownServiceId() {
        when(sftpServiceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(account("alice"), 1L))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void saveRejectsTakenLoginUsername() {
        SftpService sftp = sftpService(1L);
        when(sftpServiceRepository.findById(1L)).thenReturn(Optional.of(sftp));
        when(credentialService.composeLoginUsername("alice", sftp)).thenReturn("alice.svc1");
        when(credentialService.loginUsernameTakenError("alice.svc1", null)).thenReturn("taken");

        assertThatThrownBy(() -> service.save(account("alice"), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("taken");
        verify(repository, never()).save(any());
    }

    @Test
    void saveAttachesServiceComposesLoginUsernameAndAppliesCredentials() {
        SftpService sftp = sftpService(1L);
        when(sftpServiceRepository.findById(1L)).thenReturn(Optional.of(sftp));
        when(credentialService.composeLoginUsername("alice", sftp)).thenReturn("alice.svc1");
        when(credentialService.loginUsernameTakenError("alice.svc1", null)).thenReturn(null);
        when(repository.save(any(SftpServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        SftpServiceAccount a = account("alice");

        SftpServiceAccount saved = service.save(a, 1L);

        assertThat(saved.getSftpService()).isEqualTo(sftp);
        assertThat(saved.getLoginUsername()).isEqualTo("alice.svc1");
        verify(credentialService).applyCredentials(a, "plaintext", "ssh-rsa AAAA");
    }

    // ── update ──

    @Test
    void updateThrowsWhenAccountNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, account("alice"), null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found: 99");
    }

    @Test
    void updateRejectsWhenNeitherExistingNorSuppliedServiceIsAvailable() {
        SftpServiceAccount existing = new SftpServiceAccount();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(5L, account("alice"), null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsLoginUsernameTakenByAnotherAccount() {
        SftpService sftp = sftpService(1L);
        SftpServiceAccount existing = new SftpServiceAccount();
        existing.setSftpService(sftp);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(credentialService.composeLoginUsername("alice", sftp)).thenReturn("alice.svc1");
        when(credentialService.loginUsernameTakenError("alice.svc1", 5L)).thenReturn("taken");

        assertThatThrownBy(() -> service.update(5L, account("alice"), null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateCopiesFieldsComposesLoginUsernameAndAppliesCredentials() {
        SftpService sftp = sftpService(1L);
        SftpServiceAccount existing = new SftpServiceAccount();
        existing.setUsername("old");
        existing.setSftpService(sftp);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(credentialService.composeLoginUsername("alice", sftp)).thenReturn("alice.svc1");
        when(credentialService.loginUsernameTakenError("alice.svc1", 5L)).thenReturn(null);
        when(repository.save(any(SftpServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        SftpServiceAccount updated = account("alice");
        updated.setEmail("alice@example.com");
        updated.setPermissions("rw");
        updated.setLastUpdatedBy("admin");

        SftpServiceAccount result = service.update(5L, updated, null);

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getLoginUsername()).isEqualTo("alice.svc1");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getPermissions()).isEqualTo("rw");
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin");
        verify(credentialService).applyCredentials(existing, "plaintext", "ssh-rsa AAAA");
    }

    @Test
    void updateCanMoveAccountToADifferentSuppliedService() {
        SftpService oldSvc = sftpService(1L);
        SftpService newSvc = sftpService(2L);
        SftpServiceAccount existing = new SftpServiceAccount();
        existing.setUsername("old");
        existing.setSftpService(oldSvc);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(sftpServiceRepository.findById(2L)).thenReturn(Optional.of(newSvc));
        when(credentialService.composeLoginUsername("alice", newSvc)).thenReturn("alice.svc2");
        when(credentialService.loginUsernameTakenError("alice.svc2", 5L)).thenReturn(null);
        when(repository.save(any(SftpServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        SftpServiceAccount result = service.update(5L, account("alice"), 2L);

        assertThat(result.getSftpService()).isEqualTo(newSvc);
        assertThat(result.getLoginUsername()).isEqualTo("alice.svc2");
    }

    @Test
    void deleteDelegatesToRepository() {
        service.deleteById(3L);
        verify(repository).deleteById(3L);
    }
}
