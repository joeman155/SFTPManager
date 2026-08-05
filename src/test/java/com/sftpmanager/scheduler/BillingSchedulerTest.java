package com.sftpmanager.scheduler;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.BillingService;
import com.sftpmanager.service.BillingService.ChargeOutcome;
import com.sftpmanager.service.EmailService;
import com.sftpmanager.service.RuntimeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private BillingService billingService;
    @Mock private EmailService emailService;
    @Mock private RuntimeConfigService config;

    private BillingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BillingScheduler(userRepository, billingService, emailService, config);
        // Defaults mirroring the old application.properties fallbacks —
        // individual tests override via when(...) to exercise a non-default.
        when(config.getBoolean("billing.scheduler.enabled", true)).thenReturn(true);
        when(config.getBoolean("billing.scheduler.dry-run", true)).thenReturn(false);
    }

    /** A user who is due for billing today by every rule. */
    private User dueUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("due@example.com");
        u.setFirstName("Due");
        u.setOnboarded(true);
        u.setLocked(false);
        u.setAccountClosed(false);
        u.setServicesDeactivated(false);
        u.setPaidToDate(LocalDate.now());
        u.setCcPmId("pm_1");
        AccountControls plan = new AccountControls();
        plan.setId(3L);
        plan.setPlan("Standard");
        plan.setMonthlyPriceCents(2900L);
        u.setAccountControls(plan);
        return u;
    }

    @Test
    void scheduledRunDoesNothingWhenDisabled() {
        when(config.getBoolean("billing.scheduler.enabled", true)).thenReturn(false);

        scheduler.runMonthlyBilling();

        verifyNoInteractions(userRepository, billingService, emailService);
    }

    @Test
    void dryRunCountsDueUsersButChargesNothing() {
        when(userRepository.findAll()).thenReturn(List.of(dueUser()));
        when(config.getBoolean("billing.scheduler.dry-run", true)).thenReturn(true);

        String summary = scheduler.billUsersDueToday();

        assertThat(summary).contains("1 due", "0 charged");
        verifyNoInteractions(billingService);
    }

    @Test
    void successfulChargeExtendsPaidToDate() {
        User u = dueUser();
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(billingService.chargeUser(eq(u), eq(2900L), anyString(), eq("SCHEDULER"), anyString()))
            .thenReturn(new ChargeOutcome(true, "Payment succeeded", null));

        String summary = scheduler.billUsersDueToday();

        assertThat(summary).contains("1 due", "1 charged", "0 failed");
        assertThat(u.getPaidToDate()).isEqualTo(LocalDate.now().plusMonths(1));
        verify(userRepository).save(u);
        verifyNoInteractions(emailService);
    }

    @Test
    void failedChargeEmailsUserAndDoesNotExtendPaidToDate() {
        User u = dueUser();
        LocalDate originalPaidTo = u.getPaidToDate();
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(billingService.chargeUser(any(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(new ChargeOutcome(false, "Payment failed: declined", null));

        String summary = scheduler.billUsersDueToday();

        assertThat(summary).contains("1 failed");
        assertThat(u.getPaidToDate()).isEqualTo(originalPaidTo);
        verify(userRepository, never()).save(any());
        verify(emailService).sendPaymentFailedEmail(eq("due@example.com"), eq("Due"), eq("$29.00"));
    }

    @Test
    void usersNotYetDueAreSkipped() {
        User u = dueUser();
        u.setPaidToDate(LocalDate.now().plusDays(5));
        when(userRepository.findAll()).thenReturn(List.of(u));

        String summary = scheduler.billUsersDueToday();

        assertThat(summary).contains("0 due");
        verifyNoInteractions(billingService);
    }

    @Test
    void ineligibleUsersAreNeverBilled() {
        User notOnboarded = dueUser();
        notOnboarded.setOnboarded(false);
        User locked = dueUser();
        locked.setLocked(true);
        User closed = dueUser();
        closed.setAccountClosed(true);
        User deactivated = dueUser();
        deactivated.setServicesDeactivated(true);
        User freePlan = dueUser();
        freePlan.getAccountControls().setMonthlyPriceCents(0L);
        when(userRepository.findAll()).thenReturn(List.of(notOnboarded, locked, closed, deactivated, freePlan));

        String summary = scheduler.billUsersDueToday();

        assertThat(summary).contains("0 due");
        verifyNoInteractions(billingService);
    }

    @Test
    void dueUserWithoutAnyCardIsCountedAsSkipped() {
        User u = dueUser();
        u.setCcPmId(null);
        u.setBackupCcPmId(null);
        when(userRepository.findAll()).thenReturn(List.of(u));

        String summary = scheduler.billUsersDueToday();

        assertThat(summary).contains("1 skipped");
        verifyNoInteractions(billingService);
    }

    // ══ billUsersDueTodayWithDetails — surfaces WHY a charge failed ══

    @Test
    void withDetailsExposesTheFailureReasonPerUser() {
        User u = dueUser();
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(billingService.chargeUser(any(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(new ChargeOutcome(false, "Payment failed: Your card was declined. (mock)", null));

        BillingScheduler.BillingRunResult result = scheduler.billUsersDueTodayWithDetails();

        assertThat(result.summary()).contains("1 failed");
        assertThat(result.failureDetails())
            .containsExactly("due@example.com — Payment failed: Your card was declined. (mock)");
    }

    @Test
    void withDetailsExposesGuardrailRefusalsThatNeverCreateAPaymentRow() {
        // e.g. "User has no saved card" — chargeUser() returns before any
        // Payment row is written, so the reason would otherwise be visible
        // ONLY in the server log. This is exactly what the admin UI needs.
        User u = dueUser();
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(billingService.chargeUser(any(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(new ChargeOutcome(false, "Charging disabled: billing.enabled=false", null));

        BillingScheduler.BillingRunResult result = scheduler.billUsersDueTodayWithDetails();

        assertThat(result.failureDetails())
            .containsExactly("due@example.com — Charging disabled: billing.enabled=false");
    }

    @Test
    void withDetailsReturnsEmptyListWhenNothingFails() {
        User u = dueUser();
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(billingService.chargeUser(eq(u), eq(2900L), anyString(), eq("SCHEDULER"), anyString()))
            .thenReturn(new ChargeOutcome(true, "Payment succeeded", null));

        BillingScheduler.BillingRunResult result = scheduler.billUsersDueTodayWithDetails();

        assertThat(result.failureDetails()).isEmpty();
    }
}
