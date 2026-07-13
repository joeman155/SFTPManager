package com.sftpmanager.service.billing;

/**
 * Abstraction over the payment provider. Two implementations:
 * - StripeGateway: real Stripe REST API (test or live keys)
 * - MockGateway:   no keys configured; simulates everything in-memory
 *
 * Card numbers and CVV never pass through this interface — cards are
 * tokenized in the browser (Stripe Elements) or faked (mock mode).
 */
public interface PaymentGateway {

    /** "mock", "stripe-test" or "stripe-live" */
    String mode();

    /** Create (or return existing) gateway customer; returns customer id. */
    String ensureCustomer(String existingCustomerId, String email, String name) throws GatewayException;

    /** Start a save-card flow; the browser confirms it with the CVV. */
    SetupIntent createSetupIntent(String customerId) throws GatewayException;

    /** Fetch display details (brand/last4/expiry) of a saved card. */
    CardDetails getCard(String paymentMethodId) throws GatewayException;

    /** Charge a saved card (merchant-initiated, off-session). */
    ChargeResult charge(String customerId, String paymentMethodId, long amountCents,
                        String currency, String description, String idempotencyKey) throws GatewayException;

    /** Remove a saved card from the vault. */
    void detach(String paymentMethodId) throws GatewayException;

    record SetupIntent(String id, String clientSecret) {}

    record CardDetails(String brand, String last4, String expiry) {}

    record ChargeResult(boolean succeeded, String paymentId, String failureReason) {}

    class GatewayException extends Exception {
        public GatewayException(String message) { super(message); }
        public GatewayException(String message, Throwable cause) { super(message, cause); }
    }
}
