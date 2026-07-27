package com.sftpmanager.service;

import com.sftpmanager.model.SftpServiceAccount;
import com.sftpmanager.repository.SftpServiceAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpCredentialServiceTest {

    @Mock private SftpServiceAccountRepository repository;

    private SftpCredentialService service;

    @BeforeEach
    void setUp() {
        service = new SftpCredentialService(repository);
    }

    // ── usernameTakenError ──

    @Test
    void nullOrBlankUsernameIsNeverTaken() {
        assertThat(service.usernameTakenError(null, null)).isNull();
        assertThat(service.usernameTakenError("  ", null)).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void newAccountWithTakenUsernameReturnsError() {
        when(repository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        assertThat(service.usernameTakenError("alice", null))
            .contains("already taken");
    }

    @Test
    void newAccountWithFreeUsernameReturnsNull() {
        when(repository.existsByUsernameIgnoreCase("alice")).thenReturn(false);

        assertThat(service.usernameTakenError("alice", null)).isNull();
    }

    @Test
    void editingAccountExcludesItselfFromUniquenessCheck() {
        when(repository.existsByUsernameIgnoreCaseAndIdNot("alice", 7L)).thenReturn(false);

        assertThat(service.usernameTakenError("alice", 7L)).isNull();
    }

    @Test
    void editingAccountStillDetectsClashWithOtherAccount() {
        when(repository.existsByUsernameIgnoreCaseAndIdNot("alice", 7L)).thenReturn(true);

        assertThat(service.usernameTakenError("alice", 7L)).contains("already taken");
    }

    // ── applyCredentials ──

    @Test
    void plaintextPasswordIsBcryptHashed() {
        SftpServiceAccount account = new SftpServiceAccount();

        service.applyCredentials(account, "secret123", null);

        assertThat(account.getPassword()).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches("secret123", account.getPassword())).isTrue();
    }

    @Test
    void alreadyBcryptHashedPasswordIsKeptVerbatim() {
        SftpServiceAccount account = new SftpServiceAccount();
        String existingHash = "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUV1234567890";

        service.applyCredentials(account, existingHash, null);

        assertThat(account.getPassword()).isEqualTo(existingHash);
    }

    @Test
    void blankPasswordKeepsExistingHash() {
        SftpServiceAccount account = new SftpServiceAccount();
        account.setPassword("$2a$10$existinghash");

        service.applyCredentials(account, "  ", null);

        assertThat(account.getPassword()).isEqualTo("$2a$10$existinghash");
    }

    @Test
    void nullPasswordKeepsExistingHash() {
        SftpServiceAccount account = new SftpServiceAccount();
        account.setPassword("$2a$10$existinghash");

        service.applyCredentials(account, null, null);

        assertThat(account.getPassword()).isEqualTo("$2a$10$existinghash");
    }

    @Test
    void publicKeyIsStoredVerbatimWithRfc4716Copy() {
        SftpServiceAccount account = new SftpServiceAccount();
        String key = "ssh-rsa AAAAB3NzaC1yc2E user@host";

        service.applyCredentials(account, null, key);

        assertThat(account.getPublicKey()).isEqualTo(key);
        assertThat(account.getPublicKeyRfc4716())
            .startsWith("---- BEGIN SSH2 PUBLIC KEY ----")
            .contains("AAAAB3NzaC1yc2E");
    }

    @Test
    void nullPublicKeyClearsBothKeyFields() {
        SftpServiceAccount account = new SftpServiceAccount();
        account.setPublicKey("old");
        account.setPublicKeyRfc4716("old-rfc");

        service.applyCredentials(account, null, null);

        assertThat(account.getPublicKey()).isNull();
        assertThat(account.getPublicKeyRfc4716()).isNull();
    }
}
