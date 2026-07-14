package com.sftpmanager.controller;

import com.sftpmanager.model.Payment;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.PaymentRepository;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.scheduler.BillingScheduler;
import com.sftpmanager.service.BillingService;
import com.sftpmanager.service.billing.MockGateway;
import com.sftpmanager.service.billing.PaymentGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin billing endpoints. Protected by the admin security chain (/api/**).
 */
@RestController
@RequestMapping("/api/billing")
public class AdminBillingController {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final BillingScheduler billingScheduler;

    public AdminBillingController(UserRepository userRepository,
                                  PaymentRepository paymentRepository,
                                  BillingService billingService,
                                  BillingScheduler billingScheduler) {
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
        this.billingScheduler = billingScheduler;
    }

    /** Gateway mode + guardrail settings, so the admin UI can show what's active. */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> s = billingService.status();
        s.put("schedulerDryRun", billingScheduler.isDryRun());
        // Publishable key is public by design — the admin browser needs it for Stripe Elements
        s.put("publishableKey", billingService.getPublishableKey());
        return ResponseEntity.ok(s);
    }

    // ── Card management on behalf of a user (e.g. card details taken over the
    //    phone). Same flow as the portal: the admin's BROWSER sends the card
    //    to Stripe (or the mock gateway); the server never sees the number. ──

    @PostMapping("/setup-intent/{userId}")
    public ResponseEntity<?> setupIntent(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        try {
            PaymentGateway.SetupIntent si = billingService.createSetupIntent(user);
            return ResponseEntity.ok(Map.of("clientSecret", si.clientSecret()));
        } catch (PaymentGateway.GatewayException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/attach/{userId}")
    public ResponseEntity<?> attach(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        String pmId = body.get("paymentMethodId");
        if (pmId == null || pmId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "paymentMethodId required"));
        try {
            billingService.attachCard(user, normalizeSlot(body.get("slot")), pmId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (PaymentGateway.GatewayException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    /** Mock mode only: simulate tokenization of a typed-in test card. */
    @PostMapping("/mock-save/{userId}")
    public ResponseEntity<?> mockSave(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (!(billingService.gateway() instanceof MockGateway mock)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not in mock mode"));
        }
        try {
            // CVC is validated then discarded — never stored (PCI DSS)
            String pmId = mock.saveMockCard(
                body.getOrDefault("cardNumber", ""), body.getOrDefault("expiry", ""),
                body.getOrDefault("cvc", ""));
            billingService.attachCard(user, normalizeSlot(body.get("slot")), pmId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (PaymentGateway.GatewayException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/cards/{userId}/{slot}")
    public ResponseEntity<?> removeCard(@PathVariable Long userId, @PathVariable String slot) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        billingService.removeCard(user, normalizeSlot(slot));
        return ResponseEntity.ok(Map.of("success", true));
    }

    private static String normalizeSlot(String slot) {
        return BillingService.SLOT_BACKUP.equalsIgnoreCase(slot)
            ? BillingService.SLOT_BACKUP : BillingService.SLOT_PRIMARY;
    }

    @PostMapping("/charge/{userId}")
    public ResponseEntity<?> charge(@PathVariable Long userId,
                                    @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        long amountCents;
        try {
            amountCents = Long.parseLong(String.valueOf(body.get("amountCents")));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "amountCents must be a whole number of cents"));
        }
        String description = body.get("description") != null
            ? String.valueOf(body.get("description")) : "Manual charge via admin";
        String adminEmail = principal != null ? principal.getAttribute("email") : "unknown";

        BillingService.ChargeOutcome outcome = billingService.chargeUser(
            user, amountCents, description, "ADMIN:" + adminEmail, null);

        return ResponseEntity.ok(Map.of(
            "succeeded", outcome.succeeded(),
            "message", outcome.message()
        ));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<Payment>> recentPayments() {
        return ResponseEntity.ok(paymentRepository.findTop100ByOrderByCreatedAtDesc());
    }

    @GetMapping("/payments/byuser/{userId}")
    public ResponseEntity<List<Payment>> paymentsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** Manually trigger the monthly billing pass (honours dry-run setting). */
    @PostMapping("/run-billing")
    public ResponseEntity<?> runBilling() {
        String summary = billingScheduler.billUsersDueToday();
        return ResponseEntity.ok(Map.of("summary", summary));
    }
}
