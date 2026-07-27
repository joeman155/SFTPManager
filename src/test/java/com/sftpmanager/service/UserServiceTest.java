package com.sftpmanager.service;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.model.User;
import com.sftpmanager.repository.AccountControlsRepository;
import com.sftpmanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountControlsRepository accountControlsRepository;
    @Mock private BillingService billingService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, accountControlsRepository, billingService);
    }

    private User existingUser() {
        User u = new User();
        u.setId(1L);
        u.setFirstName("Old");
        u.setSurname("Name");
        u.setEmail("old@example.com");
        return u;
    }

    private User updatedDetails() {
        User u = new User();
        u.setFirstName("New");
        u.setSurname("Person");
        u.setEmail("new@example.com");
        u.setCompany("Acme");
        u.setPhone("+61 400 000 000");
        u.setLastUpdatedBy("admin@example.com");
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

    // ── save ──

    @Test
    void saveAttachesPlanWhenIdProvided() {
        AccountControls trial = plan(1, "Trial", 0L, 14);
        when(accountControlsRepository.findById(1L)).thenReturn(Optional.of(trial));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User u = existingUser();

        User saved = service.save(u, 1L);

        assertThat(saved.getAccountControls()).isEqualTo(trial);
    }

    @Test
    void saveWithoutPlanIdLeavesPlanUntouched() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.save(existingUser(), null);

        assertThat(saved.getAccountControls()).isNull();
        verifyNoInteractions(accountControlsRepository);
    }

    // ── update: basics ──

    @Test
    void updateThrowsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, updatedDetails(), null, "admin@example.com"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("User not found: 99");
    }

    @Test
    void updateCopiesProfileFields() {
        User existing = existingUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.update(1L, updatedDetails(), null, "admin@example.com");

        assertThat(result.getFirstName()).isEqualTo("New");
        assertThat(result.getSurname()).isEqualTo("Person");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getCompany()).isEqualTo("Acme");
        assertThat(result.getPhone()).isEqualTo("+61 400 000 000");
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin@example.com");
    }

    // ── update: plan change paths ──

    @Test
    void noPlanIdMeansNoPlanChangeAndNoBilling() {
        User existing = existingUser();
        existing.setAccountControls(plan(5, "Pro", 5000L, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, updatedDetails(), null, "admin@example.com");

        assertThat(existing.getAccountControls().getPlan()).isEqualTo("Pro");
        verifyNoInteractions(billingService);
    }

    @Test
    void unknownPlanIdLeavesPlanUntouched() {
        User existing = existingUser();
        existing.setAccountControls(plan(5, "Pro", 5000L, null));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountControlsRepository.findById(999L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, updatedDetails(), 999L, "admin@example.com");

        assertThat(existing.getAccountControls().getPlan()).isEqualTo("Pro");
        verifyNoInteractions(billingService);
    }

    @Test
    void reselectingSamePlanDoesNotTriggerBilling() {
        User existing = existingUser();
        existing.setAccountControls(plan(5, "Pro", 5000L, null));
        AccountControls samePlanFresh = plan(5, "Pro", 5000L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountControlsRepository.findById(5L)).thenReturn(Optional.of(samePlanFresh));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, updatedDetails(), 5L, "admin@example.com");

        assertThat(existing.getAccountControls()).isEqualTo(samePlanFresh);
        verifyNoInteractions(billingService);
    }

    @Test
    void switchingToTrialPlanStartsTrialClock() {
        User existing = existingUser();
        existing.setAccountControls(plan(5, "Pro", 5000L, null));
        existing.setPaidToDate(LocalDate.now().plusDays(20));
        existing.setServicesDeactivated(true);
        AccountControls trial = plan(1, "Trial", 0L, 14);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountControlsRepository.findById(1L)).thenReturn(Optional.of(trial));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, updatedDetails(), 1L, "admin@example.com");

        assertThat(existing.getAccountControls()).isEqualTo(trial);
        assertThat(existing.getTrialExpires()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(existing.getPaidToDate()).isNull();
        assertThat(existing.getServicesDeactivated()).isFalse();
        verifyNoInteractions(billingService);
    }

    @Test
    void switchingToPaidPlanDelegatesToBillingWithDirectDowngradeAllowed() {
        User existing = existingUser();
        existing.setAccountControls(plan(1, "Trial", 0L, 14));
        AccountControls paid = plan(5, "Pro", 5000L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountControlsRepository.findById(5L)).thenReturn(Optional.of(paid));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, updatedDetails(), 5L, "admin@example.com");

        // Admin plan changes always allow direct downgrade and are attributed
        verify(billingService).switchPaidPlan(eq(existing), eq(paid), eq("ADMIN:admin@example.com"), eq(true));
    }

    @Test
    void firstPlanAssignmentToPaidPlanAlsoGoesThroughBilling() {
        User existing = existingUser(); // no current plan at all
        AccountControls paid = plan(5, "Pro", 5000L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountControlsRepository.findById(5L)).thenReturn(Optional.of(paid));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, updatedDetails(), 5L, "admin@example.com");

        verify(billingService).switchPaidPlan(eq(existing), eq(paid), eq("ADMIN:admin@example.com"), eq(true));
    }

    // ── delete ──

    @Test
    void deleteDelegatesToRepository() {
        service.deleteById(7L);
        verify(userRepository).deleteById(7L);
        verify(userRepository, never()).deleteAll();
    }
}
