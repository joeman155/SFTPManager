package com.sftpmanager.controller;

import com.sftpmanager.model.User;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.BillingService;
import com.sftpmanager.service.billing.MockGateway;
import com.sftpmanager.service.billing.PaymentGateway;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Card management for the customer portal. Card numbers/CVV never reach these
 * endpoints in Stripe mode — the browser sends them straight to Stripe and we
 * only receive an opaque payment-method id. In mock mode a fake card number is
 * accepted (and immediately discarded) to simulate the same flow.
 */
@RestController
@RequestMapping("/portal/api/billing")
public class PortalBillingController {

    private final UserRepository userRepository;
    private final BillingService billingService;

    public PortalBillingController(UserRepository userRepository, BillingService billingService) {
        this.userRepository = userRepository;
        this.billingService = billingService;
    }

    private Optional<User> currentUser(OAuth2User principal, HttpSession session) {
        String email = principal != null ? principal.getAttribute("email")
            : (session != null ? (String) session.getAttribute("EMAIL_AUTH_USER") : null);
        if (email == null) return Optional.empty();
        return userRepository.findByEmail(email)
            .filter(u -> !Boolean.TRUE.equals(u.getLocked()));
    }

    @GetMapping("/config")
    public ResponseEntity<?> config(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of(
            "mode", billingService.gateway().mode(),
            "publishableKey", billingService.getPublishableKey(),
            "currency", billingService.getCurrency()
        ))).orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/cards")
    public ResponseEntity<?> cards(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            Map<String, Object> out = new HashMap<>();
            out.put("primary", user.getCcPmId() == null ? null : Map.of(
                "brand", nz(user.getCcBrand()), "last4", nz(user.getCcLast4()), "expiry", nz(user.getCcExpiry())));
            out.put("backup", user.getBackupCcPmId() == null ? null : Map.of(
                "brand", nz(user.getBackupCcBrand()), "last4", nz(user.getBackupCcLast4()), "expiry", nz(user.getBackupCcExpiry())));
            return ResponseEntity.ok(out);
        }).orElse(ResponseEntity.status(401).build());
    }

    /** Stripe mode: create a SetupIntent; browser confirms it with Stripe.js (CVV checked there). */
    @PostMapping("/setup-intent")
    public ResponseEntity<?> setupIntent(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            try {
                PaymentGateway.SetupIntent si = billingService.createSetupIntent(user);
                return ResponseEntity.ok(Map.of("clientSecret", si.clientSecret()));
            } catch (PaymentGateway.GatewayException e) {
                return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.status(401).build());
    }

    /** Stripe mode: after the browser confirmed the SetupIntent, attach the resulting payment method. */
    @PostMapping("/attach")
    public ResponseEntity<?> attach(@AuthenticationPrincipal OAuth2User principal,
                                    @RequestBody Map<String, String> body,
                                    HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            String pmId = body.get("paymentMethodId");
            String slot = normalizeSlot(body.get("slot"));
            if (pmId == null || pmId.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "paymentMethodId required"));
            try {
                billingService.attachCard(user, slot, pmId);
                return ResponseEntity.ok(Map.of("success", true));
            } catch (PaymentGateway.GatewayException e) {
                return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.status(401).build());
    }

    /** Mock mode only: simulate tokenization of a typed-in test card. */
    @PostMapping("/mock-save")
    public ResponseEntity<?> mockSave(@AuthenticationPrincipal OAuth2User principal,
                                      @RequestBody Map<String, String> body,
                                      HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            if (!(billingService.gateway() instanceof MockGateway mock)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Not in mock mode"));
            }
            String slot = normalizeSlot(body.get("slot"));
            try {
                // CVC is validated then discarded — never stored (PCI DSS)
                String pmId = mock.saveMockCard(nz(body.get("cardNumber")), nz(body.get("expiry")), nz(body.get("cvc")));
                billingService.attachCard(user, slot, pmId);
                return ResponseEntity.ok(Map.of("success", true));
            } catch (PaymentGateway.GatewayException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/cards/{slot}")
    public ResponseEntity<?> removeCard(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable String slot,
                                        HttpSession session) {
        return currentUser(principal, session).<ResponseEntity<?>>map(user -> {
            billingService.removeCard(user, normalizeSlot(slot));
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(401).build());
    }

    private static String normalizeSlot(String slot) {
        return BillingService.SLOT_BACKUP.equalsIgnoreCase(slot)
            ? BillingService.SLOT_BACKUP : BillingService.SLOT_PRIMARY;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
