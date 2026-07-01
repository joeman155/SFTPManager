package com.sftpmanager.config;

import com.sftpmanager.model.Plan;
import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.repository.PlanRepository;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitialiser implements CommandLineRunner {

    private final PlanRepository planRepository;
    private final RuntimeSettingsRepository runtimeSettingsRepository;

    public DataInitialiser(PlanRepository planRepository,
                           RuntimeSettingsRepository runtimeSettingsRepository) {
        this.planRepository = planRepository;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
    }

    @Override
    public void run(String... args) {

        // Seed plans if empty
        if (planRepository.count() == 0) {
            Plan basic = new Plan();
            basic.setName("Basic");
            basic.setDescription("Up to 5 SFTP services. 10GB storage. Email support. Perfect for individuals and small teams.");
            planRepository.save(basic);

            Plan enterprise = new Plan();
            enterprise.setName("Enterprise");
            enterprise.setDescription("Unlimited SFTP services. 1TB storage. Priority 24/7 support. Advanced security features. Ideal for growing businesses.");
            planRepository.save(enterprise);
        }

        // Seed default T&C if not present
        if (runtimeSettingsRepository.findByName("termsandconditions").isEmpty()) {
            RuntimeSettings tc = new RuntimeSettings();
            tc.setName("termsandconditions");
            tc.setValue("""
                <h5>Terms and Conditions</h5>
                <p>By using SFTP Manager you agree to the following terms:</p>
                <ol>
                    <li>You are responsible for all activity on your account.</li>
                    <li>You must not use this service for illegal purposes.</li>
                    <li>We may suspend accounts that violate these terms.</li>
                    <li>Trial accounts are valid for 7 days. A valid credit card is required to continue after the trial period.</li>
                    <li>Subscription fees are billed monthly and are non-refundable.</li>
                    <li>We reserve the right to change these terms with 30 days notice.</li>
                </ol>
                <p>For questions contact <a href="mailto:support@leederville.net">support@leederville.net</a></p>
            """);
            tc.setCreatedBy("system");
            tc.setLastUpdatedBy("system");
            runtimeSettingsRepository.save(tc);
        }
    }
}
