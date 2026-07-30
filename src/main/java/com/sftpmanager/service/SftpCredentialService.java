package com.sftpmanager.service;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.model.SftpServiceAccount;
import com.sftpmanager.repository.SftpServiceAccountRepository;
import com.sftpmanager.util.SshKeyUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Prepares SFTP account credentials for storage in a form a real SFTP server
 * (ProFTPD mod_sql / crypt(3)) can authenticate against:
 *  - passwords are bcrypt-hashed, never stored in plaintext
 *  - public keys get an RFC 4716 copy for ProFTPD's SFTPAuthorizedUserKeys
 *  - the actual login username (loginUsername) must be globally unique (one Linux
 *    host serves all services) — it's derived from the plain `username` the customer
 *    picks plus their service id, so the same plain username can be reused across
 *    different services without colliding.
 */
@Service
public class SftpCredentialService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SftpServiceAccountRepository repository;

    public SftpCredentialService(SftpServiceAccountRepository repository) {
        this.repository = repository;
    }

    /** Deterministic, globally-unique SFTP login credential for an account within a service. */
    public String composeLoginUsername(String username, SftpService sftpService) {
        if (sftpService == null || sftpService.getId() == null) {
            throw new IllegalArgumentException("Account must belong to a valid SFTP service.");
        }
        return username + ".svc" + sftpService.getId();
    }

    /** Returns an error message if the login username is taken, else null. selfId excludes the account being edited. */
    public String loginUsernameTakenError(String loginUsername, Long selfId) {
        if (loginUsername == null || loginUsername.isBlank()) return null;
        boolean taken = selfId == null
            ? repository.existsByLoginUsernameIgnoreCase(loginUsername)
            : repository.existsByLoginUsernameIgnoreCaseAndIdNot(loginUsername, selfId);
        return taken ? "This username is already taken on this service. Please choose a different username." : null;
    }

    /**
     * Applies incoming credentials onto the entity being saved.
     * Password: blank keeps the existing hash (edit forms send blank for
     * "unchanged"); an already-bcrypt value is kept as-is; anything else is
     * hashed. Public key: stored verbatim plus an RFC 4716 copy.
     */
    public void applyCredentials(SftpServiceAccount target, String incomingPassword, String incomingPublicKey) {
        if (incomingPassword != null && !incomingPassword.isBlank()) {
            target.setPassword(incomingPassword.startsWith("$2")
                ? incomingPassword
                : encoder.encode(incomingPassword));
        }
        target.setPublicKey(incomingPublicKey);
        target.setPublicKeyRfc4716(SshKeyUtil.toRfc4716(incomingPublicKey));
    }
}
