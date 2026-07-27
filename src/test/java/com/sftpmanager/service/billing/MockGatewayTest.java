package com.sftpmanager.service.billing;

import com.sftpmanager.service.billing.PaymentGateway.CardDetails;
import com.sftpmanager.service.billing.PaymentGateway.ChargeResult;
import com.sftpmanager.service.billing.PaymentGateway.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockGatewayTest {

    private static final String GOOD_CARD = "4242424242424242";
    private static final String DECLINE_CARD = "4000000000000002";
    private static final String CVV_FAIL_CARD = "4000000000000101";
    private static final String FUTURE_EXPIRY = "12/99";

    private MockGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockGateway();
    }

    // ── ensureCustomer ──

    @Test
    void ensureCustomerReturnsExistingIdUnchanged() {
        assertThat(gateway.ensureCustomer("cus_existing", "a@b.com", "A B"))
            .isEqualTo("cus_existing");
    }

    @Test
    void ensureCustomerCreatesNewIdWhenNoneExists() {
        assertThat(gateway.ensureCustomer(null, "a@b.com", "A B")).startsWith("cus_mock_");
        assertThat(gateway.ensureCustomer("", "a@b.com", "A B")).startsWith("cus_mock_");
    }

    // ── saveMockCard validation ──

    @Test
    void savesValidCardAndReturnsPaymentMethodId() throws GatewayException {
        String pmId = gateway.saveMockCard(GOOD_CARD, FUTURE_EXPIRY, "123");

        assertThat(pmId).startsWith("pm_mock_");
        CardDetails card = gateway.getCard(pmId);
        assertThat(card.brand()).isEqualTo("visa");
        assertThat(card.last4()).isEqualTo("4242");
        assertThat(card.expiry()).isEqualTo(FUTURE_EXPIRY);
    }

    @Test
    void acceptsCardNumberWithSpaces() throws GatewayException {
        String pmId = gateway.saveMockCard("4242 4242 4242 4242", FUTURE_EXPIRY, "123");
        assertThat(gateway.getCard(pmId).last4()).isEqualTo("4242");
    }

    @Test
    void rejectsNonLuhnCardNumber() {
        assertThatThrownBy(() -> gateway.saveMockCard("4242424242424241", FUTURE_EXPIRY, "123"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("Invalid card number");
    }

    @Test
    void rejectsTooShortCardNumber() {
        assertThatThrownBy(() -> gateway.saveMockCard("42424242", FUTURE_EXPIRY, "123"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("Invalid card number");
    }

    @Test
    void rejectsMalformedExpiry() {
        assertThatThrownBy(() -> gateway.saveMockCard(GOOD_CARD, "1299", "123"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("Expiry must be MM/YY");
    }

    @Test
    void rejectsInvalidExpiryMonth() {
        assertThatThrownBy(() -> gateway.saveMockCard(GOOD_CARD, "13/99", "123"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("Invalid expiry month");
    }

    @Test
    void rejectsExpiredCard() {
        assertThatThrownBy(() -> gateway.saveMockCard(GOOD_CARD, "01/20", "123"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("Card has expired");
    }

    @Test
    void rejectsInvalidCvcFormat() {
        assertThatThrownBy(() -> gateway.saveMockCard(GOOD_CARD, FUTURE_EXPIRY, "12"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("Invalid security code");
    }

    @Test
    void rejectsCvcOfAllZeros() {
        assertThatThrownBy(() -> gateway.saveMockCard(GOOD_CARD, FUTURE_EXPIRY, "000"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("security code (CVV) is incorrect");
    }

    @Test
    void rejectsCvvFailTestCardAtSaveTime() {
        assertThatThrownBy(() -> gateway.saveMockCard(CVV_FAIL_CARD, FUTURE_EXPIRY, "123"))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("security code (CVV) is incorrect");
    }

    // ── charge ──

    @Test
    void chargeSucceedsForNormalCard() throws GatewayException {
        String pmId = gateway.saveMockCard(GOOD_CARD, FUTURE_EXPIRY, "123");

        ChargeResult result = gateway.charge("cus_1", pmId, 2900, "aud", "test", "key-1");

        assertThat(result.succeeded()).isTrue();
        assertThat(result.paymentId()).startsWith("pi_mock_");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void chargeIsDeclinedForDeclineTestCard() throws GatewayException {
        String pmId = gateway.saveMockCard(DECLINE_CARD, FUTURE_EXPIRY, "123");

        ChargeResult result = gateway.charge("cus_1", pmId, 2900, "aud", "test", "key-1");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.paymentId()).isNull();
        assertThat(result.failureReason()).contains("declined");
    }

    // ── getCard / detach ──

    @Test
    void getCardFabricatesDetailsForUnknownPaymentMethod() throws GatewayException {
        CardDetails card = gateway.getCard("pm_unknown");

        assertThat(card.brand()).isEqualTo("visa");
        assertThat(card.last4()).isEqualTo("0000");
    }

    @Test
    void detachRemovesCardAndItsDecliningFlag() throws GatewayException {
        String pmId = gateway.saveMockCard(DECLINE_CARD, FUTURE_EXPIRY, "123");

        gateway.detach(pmId);

        // Card is gone from the vault (fabricated details returned)...
        assertThat(gateway.getCard(pmId).last4()).isEqualTo("0000");
        // ...and it no longer declines
        assertThat(gateway.charge("cus_1", pmId, 100, "aud", "test", null).succeeded()).isTrue();
    }

    @Test
    void detectsCardBrandFromLeadingDigit() throws GatewayException {
        String mastercard = gateway.saveMockCard("5555555555554444", FUTURE_EXPIRY, "123");
        String amex = gateway.saveMockCard("378282246310005", FUTURE_EXPIRY, "1234");

        assertThat(gateway.getCard(mastercard).brand()).isEqualTo("mastercard");
        assertThat(gateway.getCard(amex).brand()).isEqualTo("amex");
    }
}
