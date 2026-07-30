package com.sftpmanager.service;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.SftpServiceAccount;
import com.sftpmanager.repository.SftpServiceAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ── composeLoginUsername ──

    @Test
    void composesLoginUsernameFromUsernameAndServiceId() {
        SftpService svc = new SftpService();
        svc.setId(42L);

        assertThat(service.composeLoginUsername("alice", svc)).isEqualTo("alice.svc42");
    }

    @Test
    void composeLoginUsernameRejectsMissingService() {
        assertThatThrownBy(() -> service.composeLoginUsername("alice", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.composeLoginUsername("alice", new SftpService()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── loginUsernameTakenError ──

    @Test
    void nullOrBlankLoginUsernameIsNeverTaken() {
        assertThat(service.loginUsernameTakenError(null, null)).isNull();
        assertThat(service.loginUsernameTakenError("  ", null)).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void newAccountWithTakenLoginUsernameReturnsError() {
        when(repository.existsByLoginUsernameIgnoreCase("alice.svc1")).thenReturn(true);

        assertThat(service.loginUsernameTakenError("alice.svc1", null))
            .contains("already taken");
    }

    @Test
    void newAccountWithFreeLoginUsernameReturnsNull() {
        when(repository.existsByLoginUsernameIgnoreCase("alice.svc1")).thenReturn(false);

        assertThat(service.loginUsernameTakenError("alice.svc1", null)).isNull();
    }

    @Test
    void editingAccountExcludesItselfFromUniquenessCheck() {
        when(repository.existsByLoginUsernameIgnoreCaseAndIdNot("alice.svc1", 7L)).thenReturn(false);

        assertThat(service.loginUsernameTakenError("alice.svc1", 7L)).isNull();
    }

    @Test
    void editingAccountStillDetectsClashWithOtherAccount() {
        when(repository.existsByLoginUsernameIgnoreCaseAndIdNot("alice.svc1", 7L)).thenReturn(true);

        assertThat(service.loginUsernameTakenError("alice.svc1", 7L)).contains("already taken");
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
