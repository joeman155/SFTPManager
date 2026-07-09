package com.sftpmanager.scheduler;

import com.sftpmanager.model.User;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class TrialExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrialExpiryScheduler.class);

    private final UserRepository userRepository;
    private final EmailService emailService;

    public TrialExpiryScheduler(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    // Runs at the top of every hour
    @Scheduled(cron = "0 0 * * * *")
    public void checkTrialsAndPayments() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        log.info("Trial/payment expiry check running for {}", today);

        List<User> users = userRepository.findAll();

        for (User user : users) {
            if (Boolean.TRUE.equals(user.getServicesDeactivated())) continue; // already deactivated

            boolean isPaid = user.getPaidToDate() != null;

            if (isPaid) {
                // Paid user — deactivate if paid_to_date has passed
                if (user.getPaidToDate().isBefore(today)) {
                    log.info("Deactivating user {} — paid_to_date {} has passed", user.getEmail(), user.getPaidToDate());
                    user.setServicesDeactivated(true);
                    userRepository.save(user);
                    emailService.sendTrialExpiredEmail(user.getEmail(), user.getFirstName());
                }
            } else if (user.getTrialExpires() != null) {
                // Trial user
                if (user.getTrialExpires().isBefore(today) || user.getTrialExpires().isEqual(today)) {
                    // Trial has expired — deactivate + email
                    log.info("Deactivating user {} — trial expired {}", user.getEmail(), user.getTrialExpires());
                    user.setServicesDeactivated(true);
                    userRepository.save(user);
                    emailService.sendTrialExpiredEmail(user.getEmail(), user.getFirstName());
                } else if (user.getTrialExpires().isEqual(tomorrow)
                           && !Boolean.TRUE.equals(user.getTrialWarningSent())) {
                    // Trial expires tomorrow — send warning once
                    log.info("Sending trial warning to {} — expires {}", user.getEmail(), user.getTrialExpires());
                    user.setTrialWarningSent(true);
                    userRepository.save(user);
                    emailService.sendTrialWarningEmail(user.getEmail(), user.getFirstName());
                }
            }
        }
    }
}
