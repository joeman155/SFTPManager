package com.sftpmanager.service;

import com.sftpmanager.model.Payment;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.PaymentRepository;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.billing.MockGateway;
import com.sftpmanager.service.billing.PaymentGateway;
import com.sftpmanager.service.billing.PaymentGateway.GatewayException;
import com.sftpmanager.service.billing.StripeGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All charging goes through chargeUser(), which enforces the abuse guardrails:
 *
 *  1. billing.enabled            — master switch; false = nothing can charge
 *  2. billing.allow-live-charges — false (default) = charges only allowed in
 *                                  mock mode or with a Stripe TEST key. A live
 *                                  key cannot take money until this is true.
 *  3. billing.max-charge-cents   — hard cap per single charge
 *  4. billing.max-charges-per-user-per-day — stops double-billing loops
 *  5. billing.max-charges-per-day— global cap; a runaway job can't spam charges
 *
 * To go live later: set STRIPE keys to sk_live/pk_live AND billing.allow-live-charges=true.
 */
@Service
@Transactional
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    public static final String SLOT_PRIMARY = "PRIMARY";
    public static final String SLOT_BACKUP = "BACKUP";

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    @Value("${billing.enabled:true}")
    private boolean billingEnabled;

    @Value("${billing.allow-live-charges:false}")
    private boolean allowLiveCharges;

    @Value("${billing.max-charge-cents:50000}")
    private long maxChargeCents;

    @Value("${billing.max-charges-per-user-per-day:2}")
    private long maxChargesPerUserPerDay;

    @Value("${billing.max-charges-per-day:50}")
    private long maxChargesPerDay;

    @Value("${billing.currency:aud}")
    private String currency;

    private PaymentGateway gateway;

    public BillingService(UserRepository userRepository, PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    void initGateway() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            gateway = new MockGateway();
            log.warn("BILLING: no Stripe key configured — running in MOCK mode (no real payments possible)");
        } else {
            gateway = new StripeGateway(stripeSecretKey);
            log.info("BILLING: Stripe gateway active, mode={}", gateway.mode());
            if ("stripe-live".equals(gateway.mode()) && !allowLiveCharges) {
                log.error("BILLING: LIVE Stripe key configured but billing.allow-live-charges=false — ALL charges will be refused until it is enabled");
            }
        }
    }

    public PaymentGateway gateway() { return gateway; }

    public String getPublishableKey() { return stripePublishableKey; }

    public String getCurrency() { return currency; }

    /** Guardrail + mode info, shown in admin and used by the test plan. */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", gateway.mode());
        m.put("billingEnabled", billingEnabled);
        m.put("allowLiveCharges", allowLiveCharges);
        m.put("chargingPossible", chargeBlockReason() == null);
        m.put("chargeBlockReason", chargeBlockReason());
        m.put("maxChargeCents", maxChargeCents);
        m.put("maxChargesPerUserPerDay", maxChargesPerUserPerDay);
        m.put("maxChargesPerDay", maxChargesPerDay);
        m.put("currency", currency);
        return m;
    }

    private String chargeBlockReason() {
        if (!billingEnabled) return "billing.enabled=false";
        if ("stripe-live".equals(gateway.mode()) && !allowLiveCharges)
            return "Live Stripe key present but billing.allow-live-charges=false";
        return null;
    }

    // ── Cards ──

    public String ensureCustomer(User user) throws GatewayException {
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                     + (user.getSurname() != null ? user.getSurname() : "")).trim();
        String customerId = gateway.ensureCustomer(user.getStripeCustomerId(), user.getEmail(), name);
        if (!customerId.equals(user.getStripeCustomerId())) {
            user.setStripeCustomerId(customerId);
            userRepository.save(user);
        }
        return customerId;
    }

    public PaymentGateway.SetupIntent createSetupIntent(User user) throws GatewayException {
        return gateway.createSetupIntent(ensureCustomer(user));
    }

    /** Store a tokenized card against the user (slot = PRIMARY or BACKUP). */
    public void attachCard(User user, String slot, String paymentMethodId) throws GatewayException {
        PaymentGateway.CardDetails card = gateway.getCard(paymentMethodId);
        if (SLOT_BACKUP.equals(slot)) {
            user.setBackupCcPmId(paymentMethodId);
            user.setBackupCcBrand(card.brand());
            user.setBackupCcLast4(card.last4());
            user.setBackupCcExpiry(card.expiry());
        } else {
            user.setCcPmId(paymentMethodId);
            user.setCcBrand(card.brand());
            user.setCcLast4(card.last4());
            user.setCcExpiry(card.expiry());
            // Same business rule as the old raw-CC flow: adding a payment card
            // ends the trial and (re)activates services.
            user.setTrialExpires(null);
            user.setServicesDeactivated(false);
            if (user.getPaidToDate() == null || user.getPaidToDate().isBefore(LocalDate.now())) {
                user.setPaidToDate(LocalDate.now().plusDays(30));
            }
        }
        userRepository.save(user);
        log.info("BILLING: {} card saved for {} ({} •••• {})", slot, user.getEmail(), card.brand(), card.last4());
    }

    public void removeCard(User user, String slot) {
        String pmId = SLOT_BACKUP.equals(slot) ? user.getBackupCcPmId() : user.getCcPmId();
        if (pmId != null) {
            try { gateway.detach(pmId); }
            catch (GatewayException e) { log.warn("BILLING: detach failed for {}: {}", pmId, e.getMessage()); }
        }
        if (SLOT_BACKUP.equals(slot)) {
            user.setBackupCcPmId(null); user.setBackupCcBrand(null);
            user.setBackupCcLast4(null); user.setBackupCcExpiry(null);
        } else {
            user.setCcPmId(null); user.setCcBrand(null);
            user.setCcLast4(null); user.setCcExpiry(null);
        }
        userRepository.save(user);
    }

    // ── Charging ──

    public record ChargeOutcome(boolean succeeded, String message, Payment payment) {}

    /**
     * Charge a user: tries the primary card, falls back to the backup card.
     * Every attempt is recorded in the payments table. Guardrails enforced.
     */
    public ChargeOutcome chargeUser(User user, long amountCents, String description,
                                    String initiatedBy, String idempotencyKey) {
        // ── Guardrails ──
        String block = chargeBlockReason();
        if (block != null) {
            log.warn("BILLING: charge refused for {} — {}", user.getEmail(), block);
            return new ChargeOutcome(false, "Charging disabled: " + block, null);
        }
        if (amountCents <= 0) return new ChargeOutcome(false, "Amount must be positive", null);
        if (amountCents > maxChargeCents) {
            log.warn("BILLING: charge refused for {} — amount {} exceeds cap {}", user.getEmail(), amountCents, maxChargeCents);
            return new ChargeOutcome(false, "Amount exceeds billing.max-charge-cents (" + maxChargeCents + ")", null);
        }
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        if (paymentRepository.countByUserIdAndCreatedAtAfter(user.getId(), startOfDay) >= maxChargesPerUserPerDay) {
            log.warn("BILLING: charge refused for {} — per-user daily limit reached", user.getEmail());
            return new ChargeOutcome(false, "Daily charge limit reached for this user (billing.max-charges-per-user-per-day)", null);
        }
        if (paymentRepository.countByCreatedAtAfter(startOfDay) >= maxChargesPerDay) {
            log.warn("BILLING: charge refused — GLOBAL daily limit reached");
            return new ChargeOutcome(false, "Global daily charge limit reached (billing.max-charges-per-day)", null);
        }
        if (user.getCcPmId() == null && user.getBackupCcPmId() == null) {
            return new ChargeOutcome(false, "User has no saved card", null);
        }

        String customerId;
        try { customerId = ensureCustomer(user); }
        catch (GatewayException e) { return new ChargeOutcome(false, "Gateway error: " + e.getMessage(), null); }

        // Primary first, then backup
        if (user.getCcPmId() != null) {
            ChargeOutcome primary = attempt(user, customerId, user.getCcPmId(), SLOT_PRIMARY,
                cardDisplay(user.getCcBrand(), user.getCcLast4()),
                amountCents, description, initiatedBy, idempotencyKey);
            if (primary.succeeded()) return primary;
            if (user.getBackupCcPmId() == null) return primary;
            log.info("BILLING: primary card failed for {}, trying backup", user.getEmail());
        }
        return attempt(user, customerId, user.getBackupCcPmId(), SLOT_BACKUP,
            cardDisplay(user.getBackupCcBrand(), user.getBackupCcLast4()),
            amountCents, description, initiatedBy,
            idempotencyKey != null ? idempotencyKey + "-backup" : null);
    }

    private ChargeOutcome attempt(User user, String customerId, String pmId, String slot, String display,
                                  long amountCents, String description, String initiatedBy, String idempotencyKey) {
        Payment p = new Payment();
        p.setUser(user);
        p.setAmountCents(amountCents);
        p.setCurrency(currency);
        p.setCardUsed(slot);
        p.setCardDisplay(display);
        p.setDescription(description);
        p.setInitiatedBy(initiatedBy);

        PaymentGateway.ChargeResult result;
        try {
            result = gateway.charge(customerId, pmId, amountCents, currency, description, idempotencyKey);
        } catch (GatewayException e) {
            result = new PaymentGateway.ChargeResult(false, null, e.getMessage());
        }

        p.setStatus(result.succeeded() ? "SUCCEEDED" : "FAILED");
        p.setGatewayPaymentId(result.paymentId());
        p.setFailureReason(result.failureReason());
        paymentRepository.save(p);

        log.info("BILLING: charge {} for {} — {} {} on {} card ({})",
            p.getStatus(), user.getEmail(), amountCents, currency, slot,
            result.succeeded() ? result.paymentId() : result.failureReason());

        return new ChargeOutcome(result.succeeded(),
            result.succeeded() ? "Payment succeeded" : "Payment failed: " + result.failureReason(), p);
    }

    private static String cardDisplay(String brand, String last4) {
        return (brand != null ? brand : "card") + " •••• " + (last4 != null ? last4 : "????");
    }
}
