package com.sftpmanager.service;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.model.Payment;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.PaymentRepository;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.BillingService.ChargeOutcome;
import com.sftpmanager.service.BillingService.PlanChangeOutcome;
import com.sftpmanager.service.billing.PaymentGateway;
import com.sftpmanager.service.billing.PaymentGateway.ChargeResult;
import com.sftpmanager.service.billing.PaymentGateway.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway gateway;

    private BillingService service;

    @BeforeEach
    void setUp() throws GatewayException {
        service = new BillingService(userRepository, paymentRepository);
        ReflectionTestUtils.setField(service, "gateway", gateway);
        ReflectionTestUtils.setField(service, "billingEnabled", true);
        ReflectionTestUtils.setField(service, "allowLiveCharges", false);
        ReflectionTestUtils.setField(service, "maxChargeCents", 50000L);
        ReflectionTestUtils.setField(service, "maxChargesPerUserPerDay", 2L);
        ReflectionTestUtils.setField(service, "maxChargesPerDay", 50L);
        ReflectionTestUtils.setField(service, "currency", "aud");

        when(gateway.mode()).thenReturn("mock");
        when(gateway.ensureCustomer(any(), any(), any())).thenReturn("cus_1");
    }

    // ── Test data helpers ──

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setEmail("test@example.com");
        u.setFirstName("Test");
        u.setSurname("User");
        u.setStripeCustomerId("cus_1");
        u.setCcPmId("pm_primary");
        u.setCcBrand("visa");
        u.setCcLast4("4242");
        return u;
    }

    private AccountControls plan(long id, String name, Long priceCents, Integer trialDays) {
        AccountControls p = new AccountControls();
        p.setId(id);
        p.setPlan(name);
        p.setMonthlyPriceCents(priceCents);
        p.setTrialDays(trialDays);
        return p;
    }

    private void stubChargeSuccess() throws GatewayException {
        when(gateway.charge(anyString(), anyString(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(new ChargeResult(true, "pi_1", null));
    }

    private void stubChargeDeclined() throws GatewayException {
        when(gateway.charge(anyString(), anyString(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(new ChargeResult(false, null, "Card declined"));
    }

    // ══ chargeUser: guardrails ══

    @Nested
    class ChargeUserGuardrails {

        @Test
        void refusesWhenBillingDisabled() {
            ReflectionTestUtils.setField(service, "billingEnabled", false);

            ChargeOutcome outcome = service.chargeUser(user(), 1000, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("billing.enabled=false");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void refusesLiveStripeKeyWithoutAllowLiveCharges() {
            when(gateway.mode()).thenReturn("stripe-live");

            ChargeOutcome outcome = service.chargeUser(user(), 1000, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("allow-live-charges");
        }

        @Test
        void allowsLiveStripeKeyWhenExplicitlyEnabled() throws GatewayException {
            when(gateway.mode()).thenReturn("stripe-live");
            ReflectionTestUtils.setField(service, "allowLiveCharges", true);
            stubChargeSuccess();

            assertThat(service.chargeUser(user(), 1000, "test", "TEST", null).succeeded()).isTrue();
        }

        @Test
        void refusesZeroOrNegativeAmount() {
            assertThat(service.chargeUser(user(), 0, "test", "TEST", null).succeeded()).isFalse();
            assertThat(service.chargeUser(user(), -500, "test", "TEST", null).succeeded()).isFalse();
        }

        @Test
        void refusesAmountAboveCap() {
            ChargeOutcome outcome = service.chargeUser(user(), 50001, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("max-charge-cents");
        }

        @Test
        void refusesWhenPerUserDailyLimitReached() {
            when(paymentRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(2L);

            ChargeOutcome outcome = service.chargeUser(user(), 1000, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("Daily charge limit reached for this user");
        }

        @Test
        void refusesWhenGlobalDailyLimitReached() {
            when(paymentRepository.countByCreatedAtAfter(any())).thenReturn(50L);

            ChargeOutcome outcome = service.chargeUser(user(), 1000, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("Global daily charge limit");
        }

        @Test
        void refusesWhenUserHasNoCard() {
            User u = user();
            u.setCcPmId(null);
            u.setBackupCcPmId(null);

            ChargeOutcome outcome = service.chargeUser(u, 1000, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("no saved card");
        }
    }

    // ══ chargeUser: charging & card fallback ══

    @Nested
    class ChargeUserCharging {

        @Test
        void chargesPrimaryCardAndRecordsSucceededPayment() throws GatewayException {
            stubChargeSuccess();

            ChargeOutcome outcome = service.chargeUser(user(), 2900, "first month", "SIGNUP", "key-1");

            assertThat(outcome.succeeded()).isTrue();
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment p = captor.getValue();
            assertThat(p.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(p.getCardUsed()).isEqualTo(BillingService.SLOT_PRIMARY);
            assertThat(p.getAmountCents()).isEqualTo(2900);
            assertThat(p.getGatewayPaymentId()).isEqualTo("pi_1");
        }

        @Test
        void fallsBackToBackupCardWhenPrimaryDeclined() throws GatewayException {
            User u = user();
            u.setBackupCcPmId("pm_backup");
            u.setBackupCcBrand("mastercard");
            u.setBackupCcLast4("4444");
            when(gateway.charge(anyString(), eq("pm_primary"), anyLong(), anyString(), anyString(), any()))
                .thenReturn(new ChargeResult(false, null, "Card declined"));
            when(gateway.charge(anyString(), eq("pm_backup"), anyLong(), anyString(), anyString(), any()))
                .thenReturn(new ChargeResult(true, "pi_backup", null));

            ChargeOutcome outcome = service.chargeUser(u, 2900, "test", "TEST", "key-1");

            assertThat(outcome.succeeded()).isTrue();
            // Both attempts recorded: one FAILED (primary), one SUCCEEDED (backup)
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("FAILED");
            assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("SUCCEEDED");
            assertThat(captor.getAllValues().get(1).getCardUsed()).isEqualTo(BillingService.SLOT_BACKUP);
            // Backup attempt uses a distinct idempotency key
            verify(gateway).charge(anyString(), eq("pm_backup"), anyLong(), anyString(), anyString(), eq("key-1-backup"));
        }

        @Test
        void returnsFailureWhenPrimaryDeclinedAndNoBackup() throws GatewayException {
            stubChargeDeclined();

            ChargeOutcome outcome = service.chargeUser(user(), 2900, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(outcome.message()).contains("Card declined");
            verify(paymentRepository, times(1)).save(any());
        }

        @Test
        void gatewayExceptionIsRecordedAsFailedPayment() throws GatewayException {
            when(gateway.charge(anyString(), anyString(), anyLong(), anyString(), anyString(), any()))
                .thenThrow(new GatewayException("network error"));

            ChargeOutcome outcome = service.chargeUser(user(), 2900, "test", "TEST", null);

            assertThat(outcome.succeeded()).isFalse();
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
            assertThat(captor.getValue().getFailureReason()).isEqualTo("network error");
        }
    }

    // ══ chargeFirstMonthIfDue ══

    @Nested
    class ChargeFirstMonthIfDue {

        @Test
        void returnsNullWhenUserHasNoPlan() {
            assertThat(service.chargeFirstMonthIfDue(user())).isNull();
        }

        @Test
        void returnsNullForTrialPlan() {
            User u = user();
            u.setAccountControls(plan(1, "Trial", 0L, 14));

            assertThat(service.chargeFirstMonthIfDue(u)).isNull();
        }

        @Test
        void returnsNullForUnpricedPlan() {
            User u = user();
            u.setAccountControls(plan(2, "Free", null, null));

            assertThat(service.chargeFirstMonthIfDue(u)).isNull();
        }

        @Test
        void returnsNullWhenAlreadyPaidUp() {
            User u = user();
            u.setAccountControls(plan(3, "Standard", 2900L, null));
            u.setPaidToDate(LocalDate.now().plusDays(10));

            assertThat(service.chargeFirstMonthIfDue(u)).isNull();
        }

        @Test
        void returnsNullWhenNoCardOnFile() {
            User u = user();
            u.setCcPmId(null);
            u.setAccountControls(plan(3, "Standard", 2900L, null));

            assertThat(service.chargeFirstMonthIfDue(u)).isNull();
        }

        @Test
        void successfulChargeActivatesAccount() throws GatewayException {
            stubChargeSuccess();
            User u = user();
            u.setAccountControls(plan(3, "Standard", 2900L, null));
            u.setTrialExpires(LocalDate.now().plusDays(5));
            u.setServicesDeactivated(true);

            ChargeOutcome outcome = service.chargeFirstMonthIfDue(u);

            assertThat(outcome.succeeded()).isTrue();
            assertThat(u.getTrialExpires()).isNull();
            assertThat(u.getServicesDeactivated()).isFalse();
            assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
            verify(userRepository).save(u);
        }

        @Test
        void failedChargeDoesNotActivateAccount() throws GatewayException {
            stubChargeDeclined();
            User u = user();
            u.setAccountControls(plan(3, "Standard", 2900L, null));
            LocalDate trialEnd = LocalDate.now().plusDays(5);
            u.setTrialExpires(trialEnd);

            ChargeOutcome outcome = service.chargeFirstMonthIfDue(u);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(u.getTrialExpires()).isEqualTo(trialEnd);
            assertThat(u.getPaidToDate()).isNull();
            verify(userRepository, never()).save(u);
        }
    }

    // ══ switchPaidPlan ══

    @Nested
    class SwitchPaidPlan {

        @Test
        void reselectingSamePlanIsNoOp() {
            User u = user();
            AccountControls current = plan(3, "Standard", 2900L, null);
            u.setAccountControls(current);

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(3, "Standard", 2900L, null), "TEST", false);

            assertThat(outcome.samePlan()).isTrue();
            verify(userRepository, never()).save(any());
        }

        @Test
        void selfServiceDowngradeNeedsSupport() {
            User u = user();
            u.setAccountControls(plan(3, "Pro", 5000L, null));
            u.setPaidToDate(LocalDate.now().plusDays(20));

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(2, "Basic", 2000L, null), "PORTAL:test", false);

            assertThat(outcome.downgradeNeedsSupport()).isTrue();
            assertThat(u.getAccountControls().getPlan()).isEqualTo("Pro"); // unchanged
            verify(userRepository, never()).save(any());
        }

        @Test
        void adminDirectDowngradeAppliesImmediatelyWithoutCharge() throws GatewayException {
            User u = user();
            u.setAccountControls(plan(3, "Pro", 5000L, null));
            LocalDate paidTo = LocalDate.now().plusDays(20);
            u.setPaidToDate(paidTo);

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(2, "Basic", 2000L, null), "ADMIN:a@b.com", true);

            assertThat(outcome.downgradedDirect()).isTrue();
            assertThat(u.getAccountControls().getPlan()).isEqualTo("Basic");
            assertThat(u.getPaidToDate()).isEqualTo(paidTo); // untouched
            verify(gateway, never()).charge(any(), any(), anyLong(), any(), any(), any());
        }

        @Test
        void upgradeChargesProratedDifference() throws GatewayException {
            stubChargeSuccess();
            User u = user();
            u.setAccountControls(plan(2, "Basic", 1000L, null));
            u.setPaidToDate(LocalDate.now().plusDays(15));

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(3, "Pro", 3000L, null), "TEST", false);

            // 15 unused days of a 1000c plan = 500c credit; 3000 - 500 = 2500 due
            assertThat(outcome.amountDueCents()).isEqualTo(2500);
            assertThat(outcome.charge().succeeded()).isTrue();
            assertThat(u.getAccountControls().getPlan()).isEqualTo("Pro");
            assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
            verify(gateway).charge(anyString(), anyString(), eq(2500L), anyString(), anyString(), anyString());
        }

        @Test
        void switchFullyCoveredByCreditChargesNothing() throws GatewayException {
            User u = user();
            u.setAccountControls(plan(3, "Pro", 3000L, null));
            u.setPaidToDate(LocalDate.now().plusDays(40)); // capped at 30 days = 3000c credit

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(4, "Pro-Annual", 3000L, null), "TEST", false);

            assertThat(outcome.amountDueCents()).isZero();
            assertThat(outcome.charge().succeeded()).isTrue();
            assertThat(outcome.charge().message()).contains("No charge");
            assertThat(u.getAccountControls().getPlan()).isEqualTo("Pro-Annual");
            assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
            verify(gateway, never()).charge(any(), any(), anyLong(), any(), any(), any());
        }

        @Test
        void failedUpgradeChargeLeavesPlanUnchanged() throws GatewayException {
            stubChargeDeclined();
            User u = user();
            u.setAccountControls(plan(2, "Basic", 1000L, null));
            LocalDate paidTo = LocalDate.now().plusDays(15);
            u.setPaidToDate(paidTo);

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(3, "Pro", 3000L, null), "TEST", false);

            assertThat(outcome.charge().succeeded()).isFalse();
            assertThat(u.getAccountControls().getPlan()).isEqualTo("Basic");
            assertThat(u.getPaidToDate()).isEqualTo(paidTo);
        }

        @Test
        void userNotPaidUpGetsFirstMonthBilling() throws GatewayException {
            stubChargeSuccess();
            User u = user();
            u.setAccountControls(plan(1, "Trial", 0L, 14)); // trial => not a paid cycle
            u.setTrialExpires(LocalDate.now().plusDays(7));

            PlanChangeOutcome outcome = service.switchPaidPlan(u, plan(3, "Pro", 3000L, null), "TEST", false);

            assertThat(u.getAccountControls().getPlan()).isEqualTo("Pro");
            assertThat(outcome.charge()).isNotNull();
            assertThat(outcome.charge().succeeded()).isTrue();
            assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
            assertThat(u.getTrialExpires()).isNull(); // trial ended by activation
            verify(gateway).charge(anyString(), anyString(), eq(3000L), anyString(), anyString(), anyString());
        }
    }

    // ══ activatePaidMonth / recordManualPaid ══

    @Nested
    class Activation {

        @Test
        void activateExtendsFromTodayWhenNotPaidUp() {
            User u = user();
            u.setPaidToDate(LocalDate.now().minusDays(10)); // lapsed

            service.activatePaidMonth(u);

            assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
        }

        @Test
        void activateExtendsFromFuturePaidToDate() {
            User u = user();
            LocalDate future = LocalDate.now().plusDays(10);
            u.setPaidToDate(future);

            service.activatePaidMonth(u);

            assertThat(u.getPaidToDate()).isEqualTo(future.plusMonths(1));
        }

        @Test
        void activateClearsTrialAndReactivatesServices() {
            User u = user();
            u.setTrialExpires(LocalDate.now().plusDays(3));
            u.setServicesDeactivated(true);

            service.activatePaidMonth(u);

            assertThat(u.getTrialExpires()).isNull();
            assertThat(u.getServicesDeactivated()).isFalse();
            verify(userRepository).save(u);
        }

        @Test
        void recordManualPaidSavesZeroAmountPaymentAndActivates() {
            User u = user();

            service.recordManualPaid(u, "admin@example.com");

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment p = captor.getValue();
            assertThat(p.getAmountCents()).isZero();
            assertThat(p.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(p.getInitiatedBy()).isEqualTo("ADMIN:admin@example.com");
            assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
        }
    }

    // ══ attachCard ══

    @Nested
    class AttachCard {

        @Test
        void backupSlotStoresCardWithoutCharging() throws GatewayException {
            when(gateway.getCard("pm_new")).thenReturn(new PaymentGateway.CardDetails("visa", "1111", "12/30"));
            User u = user();

            ChargeOutcome outcome = service.attachCard(u, BillingService.SLOT_BACKUP, "pm_new");

            assertThat(outcome).isNull();
            assertThat(u.getBackupCcPmId()).isEqualTo("pm_new");
            assertThat(u.getBackupCcLast4()).isEqualTo("1111");
            verify(gateway, never()).charge(any(), any(), anyLong(), any(), any(), any());
        }

        @Test
        void primarySlotStoresCardAndTriggersFirstMonthChargeWhenDue() throws GatewayException {
            when(gateway.getCard("pm_new")).thenReturn(new PaymentGateway.CardDetails("visa", "1111", "12/30"));
            stubChargeSuccess();
            User u = user();
            u.setAccountControls(plan(3, "Standard", 2900L, null)); // priced, unpaid => charge due

            ChargeOutcome outcome = service.attachCard(u, BillingService.SLOT_PRIMARY, "pm_new");

            assertThat(u.getCcPmId()).isEqualTo("pm_new");
            assertThat(outcome).isNotNull();
            assertThat(outcome.succeeded()).isTrue();
            verify(userRepository, atLeastOnce()).save(u);
        }
    }
}
