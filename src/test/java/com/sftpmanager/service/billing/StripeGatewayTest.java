package com.sftpmanager.service.billing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StripeGateway talks to the real Stripe REST API, so only the key-mode
 * detection is unit-testable — the abuse guardrails in BillingService depend
 * on it to distinguish live keys from test keys.
 */
class StripeGatewayTest {

    @Test
    void liveSecretKeyReportsLiveMode() {
        assertThat(new StripeGateway("sk_live_abc123").mode()).isEqualTo("stripe-live");
    }

    @Test
    void testSecretKeyReportsTestMode() {
        assertThat(new StripeGateway("sk_test_abc123").mode()).isEqualTo("stripe-test");
    }

    @Test
    void unrecognisedKeyDefaultsToTestMode() {
        // Anything that isn't sk_live is treated as test — charges stay blocked
        // by BillingService unless the key is genuinely live AND live charges
        // are explicitly enabled.
        assertThat(new StripeGateway("sk_whatever").mode()).isEqualTo("stripe-test");
    }
}
