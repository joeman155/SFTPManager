package com.sftpmanager.controller;

import com.sftpmanager.model.*;
import com.sftpmanager.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/portal/api")
public class PortalController {

    private final PortalUserRepository portalUserRepository;
    private final UserRepository userRepository;
    private final SftpServiceRepository sftpServiceRepository;
    private final SftpServiceAccountRepository accountRepository;
    private final SftpServiceIpWhitelistRepository whitelistRepository;

    public PortalController(PortalUserRepository portalUserRepository,
                            UserRepository userRepository,
                            SftpServiceRepository sftpServiceRepository,
                            SftpServiceAccountRepository accountRepository,
                            SftpServiceIpWhitelistRepository whitelistRepository) {
        this.portalUserRepository = portalUserRepository;
        this.userRepository = userRepository;
        this.sftpServiceRepository = sftpServiceRepository;
        this.accountRepository = accountRepository;
        this.whitelistRepository = whitelistRepository;
    }

    // ── Helper: get current User from OAuth principal ──
    private Optional<User> currentUser(OAuth2User principal) {
        if (principal == null) return Optional.empty();
        String email = principal.getAttribute("email");
        return portalUserRepository.findByGoogleEmail(email)
                .map(PortalUser::getUser);
    }

    // ── Me ──
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        String email      = principal.getAttribute("email");
        String name       = principal.getAttribute("name");
        String picture    = principal.getAttribute("picture");
        String givenName  = principal.getAttribute("given_name");
        String familyName = principal.getAttribute("family_name");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFirstName(givenName  != null ? givenName  : (name != null ? name : email));
            u.setSurname(familyName != null ? familyName : "");
            u.setCreatedBy("google-oauth");
            u.setLastUpdatedBy("google-oauth");
            return userRepository.save(u);
        });

        PortalUser portalUser = portalUserRepository.findByGoogleEmail(email).orElse(new PortalUser());
        portalUser.setGoogleEmail(email);
        portalUser.setGoogleName(name);
        portalUser.setGooglePicture(picture);
        portalUser.setUser(user);
        portalUserRepository.save(portalUser);

        return ResponseEntity.ok(Map.of(
            "email",   email,
            "name",    name    != null ? name    : "",
            "picture", picture != null ? picture : "",
            "userId",  user.getId()
        ));
    }

    // ── Services ──
    @GetMapping("/services")
    public ResponseEntity<?> getServices(@AuthenticationPrincipal OAuth2User principal) {
        return currentUser(principal)
            .map(user -> ResponseEntity.ok(Map.of(
                "linked", true,
                "services", sftpServiceRepository.findByUserId(user.getId())
            )))
            .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services")
    public ResponseEntity<?> createService(@AuthenticationPrincipal OAuth2User principal,
                                           @RequestBody SftpService service) {
        return currentUser(principal).map(user -> {
            service.setUser(user);
            service.setCreatedBy(user.getEmail());
            service.setLastUpdatedBy(user.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(sftpServiceRepository.save(service));
        }).orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<?> updateService(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id,
                                           @RequestBody SftpService updated) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> {
                    s.setName(updated.getName());
                    s.setHost(updated.getHost());
                    s.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(sftpServiceRepository.save(s));
                })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<?> deleteService(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(id)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> { sftpServiceRepository.delete(s); return ResponseEntity.noContent().build(); })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    // ── Service Accounts ──
    @GetMapping("/services/{svcId}/accounts")
    public ResponseEntity<?> getAccounts(@AuthenticationPrincipal OAuth2User principal,
                                         @PathVariable Long svcId) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> ResponseEntity.ok(accountRepository.findBySftpServiceId(svcId)))
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services/{svcId}/accounts")
    public ResponseEntity<?> createAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long svcId,
                                           @RequestBody SftpServiceAccount account) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> {
                    account.setSftpService(s);
                    account.setCreatedBy(user.getEmail());
                    account.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(accountRepository.save(account));
                })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/services/{svcId}/accounts/{id}")
    public ResponseEntity<?> updateAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long svcId,
                                           @PathVariable Long id,
                                           @RequestBody SftpServiceAccount updated) {
        return currentUser(principal).map(user ->
            accountRepository.findById(id)
                .filter(a -> a.getSftpService() != null && a.getSftpService().getId().equals(svcId))
                .map(a -> {
                    a.setUsername(updated.getUsername());
                    a.setAuthenticationType(updated.getAuthenticationType());
                    a.setPassword(updated.getPassword());
                    a.setPublicKey(updated.getPublicKey());
                    a.setEnabled(updated.getEnabled());
                    a.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(accountRepository.save(a));
                })
                .orElse(ResponseEntity.status(404).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable Long id) {
        return currentUser(principal).map(user ->
            accountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal OAuth2User principal,
                                           @PathVariable Long id) {
        return currentUser(principal).map(user -> {
            accountRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.status(401).build());
    }

    // ── IP Whitelist ──
    @GetMapping("/services/{svcId}/whitelist")
    public ResponseEntity<?> getWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                          @PathVariable Long svcId) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> ResponseEntity.ok(whitelistRepository.findBySftpServiceId(svcId)))
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/services/{svcId}/whitelist")
    public ResponseEntity<?> createWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long svcId,
                                             @RequestBody SftpServiceIpWhitelist entry) {
        return currentUser(principal).map(user ->
            sftpServiceRepository.findById(svcId)
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(s -> {
                    entry.setSftpService(s);
                    entry.setCreatedBy(user.getEmail());
                    entry.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(whitelistRepository.save(entry));
                })
                .orElse(ResponseEntity.status(403).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/services/{svcId}/whitelist/{id}")
    public ResponseEntity<?> updateWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long svcId,
                                             @PathVariable Long id,
                                             @RequestBody SftpServiceIpWhitelist updated) {
        return currentUser(principal).map(user ->
            whitelistRepository.findById(id)
                .map(e -> {
                    e.setIpAddress(updated.getIpAddress());
                    e.setEnabled(updated.getEnabled());
                    e.setLastUpdatedBy(user.getEmail());
                    return ResponseEntity.ok(whitelistRepository.save(e));
                })
                .orElse(ResponseEntity.status(404).build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/whitelist/{id}")
    public ResponseEntity<?> getWhitelistEntry(@AuthenticationPrincipal OAuth2User principal,
                                               @PathVariable Long id) {
        return currentUser(principal).map(user ->
            whitelistRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/whitelist/{id}")
    public ResponseEntity<?> deleteWhitelist(@AuthenticationPrincipal OAuth2User principal,
                                             @PathVariable Long id) {
        return currentUser(principal).map(user -> {
            whitelistRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.status(401).build());
    }
}
