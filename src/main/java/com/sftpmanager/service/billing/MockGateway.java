package com.sftpmanager.service.billing;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake gateway used when no Stripe key is configured. Lets the whole flow be
 * exercised with zero external dependencies. Validates like Stripe would
 * (Luhn check, expiry validity, CVC format) and mirrors Stripe's test cards:
 *   4242 4242 4242 4242  -> saves fine, charges succeed
 *   4000 0000 0000 0002  -> saves fine, every charge is DECLINED
 *   4000 0000 0000 0101  -> rejected at save time (CVV check failed)
 *   CVC "000"            -> rejected at save time (simulated incorrect CVV)
 * Saved cards live in memory only (lost on restart — fine for testing).
 */
public class MockGateway implements PaymentGateway {

    public static final String DECLINE_CARD_LAST4 = "0002";
    public static final String CVV_FAIL_LAST4 = "0101";

    private final Map<String, CardDetails> cards = new ConcurrentHashMap<>();
    private final Map<String, String> declining = new ConcurrentHashMap<>();

    @Override
    public String mode() { return "mock"; }

    @Override
    public String ensureCustomer(String existingCustomerId, String email, String name) {
        if (existingCustomerId != null && !existingCustomerId.isBlank()) return existingCustomerId;
        return "cus_mock_" + UUID.randomUUID().toString().substring(0, 12);
    }

    @Override
    public SetupIntent createSetupIntent(String customerId) {
        String id = "seti_mock_" + UUID.randomUUID().toString().substring(0, 12);
        return new SetupIntent(id, id + "_secret");
    }

    /** Mock-only entry point: "tokenize" a card typed into the mock form. */
    public String saveMockCard(String cardNumber, String expiry, String cvc) throws GatewayException {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19 || !luhnValid(digits)) {
            throw new GatewayException("Invalid card number");
        }
        if (expiry == null || !expiry.matches("\\d{2}/\\d{2}")) {
            throw new GatewayException("Expiry must be MM/YY");
        }
        int month = Integer.parseInt(expiry.substring(0, 2));
        int year = 2000 + Integer.parseInt(expiry.substring(3));
        if (month < 1 || month > 12) throw new GatewayException("Invalid expiry month");
        java.time.YearMonth exp = java.time.YearMonth.of(year, month);
        if (exp.isBefore(java.time.YearMonth.now())) throw new GatewayException("Card has expired");
        if (cvc == null || !cvc.matches("\\d{3,4}")) {
            throw new GatewayException("Invalid security code (CVC)");
        }
        String last4 = digits.substring(digits.length() - 4);
        if (CVV_FAIL_LAST4.equals(last4) || "000".equals(cvc)) {
            throw new GatewayException("Your card's security code (CVV) is incorrect.");
        }
        String pmId = "pm_mock_" + UUID.randomUUID().toString().substring(0, 12);
        cards.put(pmId, new CardDetails(brandOf(digits), last4, expiry));
        if (DECLINE_CARD_LAST4.equals(last4)) declining.put(pmId, "true");
        return pmId;
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (alt) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    @Override
    public CardDetails getCard(String paymentMethodId) throws GatewayException {
        CardDetails c = cards.get(paymentMethodId);
        if (c == null) {
            // After a restart the in-memory vault is empty; fabricate display data
            return new CardDetails("visa", "0000", "12/99");
        }
        return c;
    }

    @Override
    public ChargeResult charge(String customerId, String paymentMethodId, long amountCents,
                               String currency, String description, String idempotencyKey) {
        if (declining.containsKey(paymentMethodId)) {
            return new ChargeResult(false, null, "Your card was declined. (mock)");
        }
        return new ChargeResult(true, "pi_mock_" + UUID.randomUUID().toString().substring(0, 12), null);
    }

    @Override
    public void detach(String paymentMethodId) {
        cards.remove(paymentMethodId);
        declining.remove(paymentMethodId);
    }

    private static String brandOf(String digits) {
        if (digits.startsWith("4")) return "visa";
        if (digits.startsWith("5")) return "mastercard";
        if (digits.startsWith("3")) return "amex";
        return "card";
    }
}
