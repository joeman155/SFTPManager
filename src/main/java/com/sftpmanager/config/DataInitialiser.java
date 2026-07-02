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

        // Seed SFTP host
        if (runtimeSettingsRepository.findByName("sftphost001").isEmpty()) {
            RuntimeSettings host = new RuntimeSettings();
            host.setName("sftphost001");
            host.setValue("sftphost001.leederville.net");
            host.setCreatedBy("system");
            host.setLastUpdatedBy("system");
            runtimeSettingsRepository.save(host);
        }

        // Seed welcome email template
        if (runtimeSettingsRepository.findByName("welcomeemail").isEmpty()) {
            RuntimeSettings welcome = new RuntimeSettings();
            welcome.setName("welcomeemail");
            welcome.setValue("""
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;padding:32px">
                    <h2 style="color:#1a1f36">Welcome to SFTP Manager, {{firstName}}!</h2>
                    <p>Your account is now active and ready to use. Here's what you can do:</p>
                    <ul style="line-height:2">
                        <li>🖥️ Create and manage your SFTP services</li>
                        <li>🔑 Add service accounts with password or public key auth</li>
                        <li>🛡️ Configure IP whitelists to secure your connections</li>
                        <li>📱 Access your dashboard anytime from any device</li>
                    </ul>
                    <a href="https://sftp.leederville.net/portal"
                       style="display:inline-block;background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">
                        Go to my dashboard
                    </a>
                    <p style="color:#6b7280;font-size:.85rem">
                        Need help? Reply to this email and we'll be happy to assist.
                    </p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                    <p style="color:#9ca3af;font-size:.78rem">
                        SFTP Manager &middot; <a href="https://sftp.leederville.net">sftp.leederville.net</a>
                    </p>
                </div>""");
            welcome.setCreatedBy("system");
            welcome.setLastUpdatedBy("system");
            runtimeSettingsRepository.save(welcome);
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
