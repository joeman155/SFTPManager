package com.sftpmanager.scheduler;

import com.sftpmanager.model.User;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialExpirySchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private TrialExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TrialExpiryScheduler(userRepository, emailService);
    }

    private User user() {
        User u = new User();
        u.setEmail("test@example.com");
        u.setFirstName("Test");
        u.setServicesDeactivated(false);
        return u;
    }

    @Test
    void alreadyDeactivatedUserIsSkipped() {
        User u = user();
        u.setServicesDeactivated(true);
        u.setPaidToDate(LocalDate.now().minusDays(30));
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void lapsedPaidUserIsDeactivatedAndEmailed() {
        User u = user();
        u.setPaidToDate(LocalDate.now().minusDays(1));
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        assertThat(u.getServicesDeactivated()).isTrue();
        verify(userRepository).save(u);
        verify(emailService).sendTrialExpiredEmail("test@example.com", "Test");
    }

    @Test
    void paidUserStillCoveredTodayIsLeftAlone() {
        User u = user();
        u.setPaidToDate(LocalDate.now()); // today = still covered
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        assertThat(u.getServicesDeactivated()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void trialExpiringTodayIsDeactivatedAndEmailed() {
        User u = user();
        u.setTrialExpires(LocalDate.now());
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        assertThat(u.getServicesDeactivated()).isTrue();
        verify(emailService).sendTrialExpiredEmail("test@example.com", "Test");
    }

    @Test
    void trialExpiringTomorrowGetsWarningOnce() {
        User u = user();
        u.setTrialExpires(LocalDate.now().plusDays(1));
        u.setTrialWarningSent(false);
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        assertThat(u.getServicesDeactivated()).isFalse();
        assertThat(u.getTrialWarningSent()).isTrue();
        verify(emailService).sendTrialWarningEmail("test@example.com", "Test");
        verify(emailService, never()).sendTrialExpiredEmail(any(), any());
    }

    @Test
    void warningIsNotRepeatedOnceSent() {
        User u = user();
        u.setTrialExpires(LocalDate.now().plusDays(1));
        u.setTrialWarningSent(true);
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        verifyNoInteractions(emailService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void trialWithPlentyOfTimeLeftIsUntouched() {
        User u = user();
        u.setTrialExpires(LocalDate.now().plusDays(7));
        when(userRepository.findAll()).thenReturn(List.of(u));

        scheduler.checkTrialsAndPayments();

        assertThat(u.getServicesDeactivated()).isFalse();
        verifyNoInteractions(emailService);
    }

    @Test
    void userWithNoTrialAndNoPaidDateIsIgnored() {
        when(userRepository.findAll()).thenReturn(List.of(user()));

        scheduler.checkTrialsAndPayments();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }
}
