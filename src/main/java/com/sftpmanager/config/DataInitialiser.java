package com.sftpmanager.config;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.repository.AccountControlsRepository;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitialiser implements CommandLineRunner {

    private final AccountControlsRepository accountControlsRepository;
    private final RuntimeSettingsRepository runtimeSettingsRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitialiser(AccountControlsRepository accountControlsRepository,
                           RuntimeSettingsRepository runtimeSettingsRepository,
                           JdbcTemplate jdbcTemplate) {
        this.accountControlsRepository = accountControlsRepository;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {

        createProftpdViews();

        // Seed plans (account controls) if empty
        if (accountControlsRepository.count() == 0) {
            AccountControls trial = new AccountControls();
            trial.setPlan("7 Day Trial");
            trial.setDescription("Try SFTP Manager free for 7 days. 1 SFTP service. 100MB storage. Email support. No credit card required.");
            trial.setMonthlyPriceCents(null); // free — never billed
            trial.setMaxUsers(5);
            trial.setMaxServers(1);
            trial.setMaxStorageMb(100L); // 100MB, as the description promises
            trial.setTrialDays(7);
            trial.setCreatedBy("system");
            trial.setLastUpdatedBy("system");
            accountControlsRepository.save(trial);

            AccountControls basic = new AccountControls();
            basic.setPlan("Basic");
            basic.setDescription("1 SFTP service. 100MB storage. Email support. Perfect for individuals and small teams.");
            basic.setMonthlyPriceCents(2900L);
            basic.setMaxUsers(5);
            basic.setMaxServers(1);
            basic.setMaxStorageMb(100L); // 100MB
            basic.setCreatedBy("system");
            basic.setLastUpdatedBy("system");
            accountControlsRepository.save(basic);

            AccountControls enterprise = new AccountControls();
            enterprise.setPlan("Enterprise");
            enterprise.setDescription("Unlimited SFTP services. 1GB storage. Priority 24/7 support. Advanced security features. Ideal for growing businesses.");
            enterprise.setMonthlyPriceCents(9900L);
            enterprise.setMaxStorageMb(1024L); // 1GB
            enterprise.setCreatedBy("system");
            enterprise.setLastUpdatedBy("system");
            accountControlsRepository.save(enterprise);
        }

        // Seed trial-abuse cap: max accounts per signup IP that get a free trial
        if (runtimeSettingsRepository.findByName("trialiplimit").isEmpty()) {
            RuntimeSettings cap = new RuntimeSettings();
            cap.setName("trialiplimit");
            cap.setValue("5");
            cap.setCreatedBy("system");
            cap.setLastUpdatedBy("system");
            runtimeSettingsRepository.save(cap);
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

    /**
     * Views consumed by ProFTPD (mod_sql_postgres) on the SFTP hosts —
     * see PROFTPD-SETUP.md. Recreated on every start since ddl-auto=create
     * rebuilds the schema. Only ENABLED accounts of users in good standing
     * (not deactivated / locked / closed) are visible to the SFTP server,
     * so every kill-switch in the app instantly applies to SFTP logins.
     */
    private void createProftpdViews() {
        jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW proftpd_users AS
            SELECT a.login_username                                      AS userid,
                   a.password                                            AS passwd,
                   2001                                                  AS uid,
                   2001                                                  AS gid,
                   '/srv/sftp/svc' || s.id                               AS homedir,
                   '/usr/sbin/nologin'                                   AS shell,
                   a.public_key_rfc4716                                  AS ssh_key,
                   a.permissions                                         AS permissions
            FROM sftp_service_account a
            JOIN sftp_service s ON s.id = a.sftp_service_id
            JOIN users u        ON u.id = s.user_id
            WHERE COALESCE(a.enabled, false) = true
              AND COALESCE(u.services_deactivated, false) = false
              AND COALESCE(u.locked, false) = false
              AND COALESCE(u.account_closed, false) = false
            """);

        // mod_wrap2 speaks tcp-wrappers syntax, which needs a dotted-decimal
        // netmask ("172.105.168.0/255.255.255.0") — it rejects CIDR prefix
        // notation ("172.105.168.0/24") with "bad net/mask expression" and
        // silently drops the rule. The app validates/stores CIDR (the format
        // admins actually expect to type), so convert it here at read time.
        // A /32 is a single exact host, and tcp-wrappers syntax for that is
        // just the bare address (no net/mask pair) — mod_wrap2 doesn't accept
        // "x.x.x.x/255.255.255.255" the way it accepts a real subnet mask.
        jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW proftpd_allowed_ips AS
            SELECT a.login_username AS name,
                   CASE WHEN w.ip_address LIKE '%/%' AND masklen(w.ip_address::inet) < 32
                        THEN host(w.ip_address::inet) || '/' || split_part(netmask(w.ip_address::inet)::text, '/', 1)
                        ELSE host(w.ip_address::inet)
                   END AS allowed
            FROM sftp_service_account a
            JOIN sftp_service_ipwhitelist w ON w.sftp_service_id = a.sftp_service_id
            WHERE COALESCE(w.enabled, false) = true
            """);

        // Synthetic groups mapping the app's READ/WRITE/DELETE permissions to
        // ProFTPD <Limit AllowGroup> rules. These groups exist only in SQL —
        // no /etc/group entries needed; filesystem ownership stays uid 2001.
        jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW proftpd_groups AS
            SELECT g.groupname,
                   g.gid,
                   COALESCE(string_agg(v.userid, ','), '') AS members
            FROM (VALUES ('sftpread', 3001), ('sftpwrite', 3002), ('sftpdelete', 3003))
                 AS g(groupname, gid)
            LEFT JOIN proftpd_users v ON (
                   (g.groupname = 'sftpread'   AND v.permissions LIKE '%READ%')
                OR (g.groupname = 'sftpwrite'  AND v.permissions LIKE '%WRITE%')
                OR (g.groupname = 'sftpdelete' AND v.permissions LIKE '%DELETE%'))
            GROUP BY g.groupname, g.gid
            """);

        // Storage quotas are enforced by XFS PROJECT QUOTAS on the SFTP host
        // (kernel-level, exact — see PROFTPD-SETUP.md §8), not by ProFTPD.
        // This view tells the host's sftp-quota-sync reconciler what limit
        // each service directory should carry (from the owner's plan);
        // quota_mb 0 = unlimited.
        jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW proftpd_service_quotas AS
            SELECT s.id                             AS service_id,
                   COALESCE(ac.max_storage_mb, 0)   AS quota_mb
            FROM sftp_service s
            JOIN users u              ON u.id  = s.user_id
            LEFT JOIN account_controls ac ON ac.id = u.account_controls_id
            """);

        // Real disk usage per service, written back by the host's
        // sftp-quota-sync timer from xfs_quota's project report (~1/min).
        // Not Hibernate-managed, so it survives ddl-auto=create rebuilds.
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sftp_service_usage (
                sftp_service_id BIGINT PRIMARY KEY,
                used_bytes      BIGINT NOT NULL DEFAULT 0,
                updated_at      TIMESTAMP NOT NULL DEFAULT now()
            )
            """);

        // ddl-auto=create rebuilds the schema on every start, which destroys
        // any previously granted privileges on these views. Re-grant to the
        // ProFTPD read-only role each time (no-op if the role doesn't exist,
        // e.g. on dev machines without an SFTP host).
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'proftpd') THEN
                    GRANT SELECT ON proftpd_users, proftpd_allowed_ips, proftpd_groups,
                                    proftpd_service_quotas TO proftpd;
                    -- the ONLY write access the SFTP host has: reporting the
                    -- real disk usage its quota reconciler measured
                    GRANT SELECT, INSERT, UPDATE ON sftp_service_usage TO proftpd;
                END IF;
            END $$
            """);
    }
}
