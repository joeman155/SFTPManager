package com.sftpmanager.scheduler;

import com.sftpmanager.model.User;
import com.sftpmanager.repository.UserRepository;
import com.sftpmanager.service.BillingService;
import com.sftpmanager.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Automated monthly billing.
 *
 * SAFETY: billing.scheduler.dry-run=true by default — the job only LOGS what
 * it would charge. Nothing is billed until you consciously set it to false.
 * On top of that, every real charge still passes through BillingService's
 * guardrails (test-key-only, amount cap, per-user and global daily limits).
 *
 * Billing rule: users who are onboarded, not locked, not deactivated, have a
 * plan with a price and a saved card, and whose paidToDate is today or earlier
 * get charged one month of their plan. Success extends paidToDate by 1 month;
 * failure (both cards) emails the user, and the existing TrialExpiryScheduler
 * deactivates them once paidToDate is past.
 */
@Component
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final UserRepository userRepository;
    private final BillingService billingService;
    private final EmailService emailService;

    @Value("${billing.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${billing.scheduler.dry-run:true}")
    private boolean dryRun;

    public BillingScheduler(UserRepository userRepository,
                            BillingService billingService,
                            EmailService emailService) {
        this.userRepository = userRepository;
        this.billingService = billingService;
        this.emailService = emailService;
    }

    // Daily at 02:30
    @Scheduled(cron = "0 30 2 * * *")
    public void runMonthlyBilling() {
        if (!enabled) return;
        billUsersDueToday();
    }

    /** Extracted so the admin "Run billing now" endpoint can trigger it too. */
    public String billUsersDueToday() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAll();
        int due = 0, charged = 0, failed = 0, skipped = 0;

        log.info("BILLING SCHEDULER: run starting for {} (dryRun={})", today, dryRun);

        for (User user : users) {
            if (!Boolean.TRUE.equals(user.getOnboarded())) continue;
            if (Boolean.TRUE.equals(user.getLocked())) continue;
            if (Boolean.TRUE.equals(user.getServicesDeactivated())) continue;
            if (user.getPaidToDate() == null || user.getPaidToDate().isAfter(today)) continue;
            if (user.getPlan() == null || user.getPlan().getMonthlyPriceCents() == null
                    || user.getPlan().getMonthlyPriceCents() <= 0) continue;
            if (user.getCcPmId() == null && user.getBackupCcPmId() == null) { skipped++; continue; }

            due++;
            long amount = user.getPlan().getMonthlyPriceCents();
            String desc = "SFTP Manager — " + user.getPlan().getName() + " plan, month starting " + user.getPaidToDate();

            if (dryRun) {
                log.info("BILLING SCHEDULER [DRY-RUN]: would charge {} {} cents ({})",
                    user.getEmail(), amount, desc);
                continue;
            }

            String idempotency = "sched-" + user.getId() + "-" + user.getPaidToDate();
            BillingService.ChargeOutcome outcome =
                billingService.chargeUser(user, amount, desc, "SCHEDULER", idempotency);

            if (outcome.succeeded()) {
                charged++;
                LocalDate base = user.getPaidToDate().isAfter(today) ? user.getPaidToDate() : today;
                user.setPaidToDate(base.plusMonths(1));
                userRepository.save(user);
                log.info("BILLING SCHEDULER: {} paid through {}", user.getEmail(), user.getPaidToDate());
            } else {
                failed++;
                log.warn("BILLING SCHEDULER: charge failed for {} — {}", user.getEmail(), outcome.message());
                emailService.sendPaymentFailedEmail(user.getEmail(), user.getFirstName(),
                    String.format("$%.2f", amount / 100.0));
            }
        }

        String summary = String.format("Billing run (dryRun=%s): %d due, %d charged, %d failed, %d skipped (no card)",
            dryRun, due, charged, failed, skipped);
        log.info("BILLING SCHEDULER: {}", summary);
        return summary;
    }

    public boolean isDryRun() { return dryRun; }
}
