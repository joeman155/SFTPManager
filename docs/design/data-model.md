# Data Model

Entity-relationship diagram of every JPA entity in the app. Audit columns
(`creation_date`/`created_by`/`last_updated_date`/`last_updated_by`) are
carried by most tables but omitted from the boxes below for readability —
see the source model classes in `src/main/java/com/sftpmanager/model/` for
the exact list per entity.

```mermaid
erDiagram
    ACCOUNT_CONTROLS ||--o{ USERS : "assigned plan"
    USERS ||--o{ SFTP_SERVICE : owns
    USERS ||--o{ PAYMENTS : "billed to"
    USERS ||--o{ PORTAL_USERS : "google identity"
    SFTP_SERVICE ||--o{ SFTP_SERVICE_ACCOUNT : contains
    SFTP_SERVICE ||--o{ SFTP_SERVICE_IPWHITELIST : contains

    USERS {
        bigint id PK
        string first_name
        string surname
        string company
        string email UK
        string auth_type "GOOGLE or EMAIL"
        string password_hash "bcrypt, EMAIL users only"
        int role "1=user, 10=admin"
        boolean onboarded
        boolean services_deactivated
        boolean locked
        boolean account_closed
        string signup_ip
        int failed_login_attempts
        date trial_expires
        boolean trial_warning_sent
        date paid_to_date
        string stripe_customer_id
        string cc_pm_id "primary card token"
        string cc_brand
        string cc_last4
        string cc_expiry
        string backup_cc_pm_id "backup card token"
        string backup_cc_brand
        string backup_cc_last4
        string backup_cc_expiry
        bigint account_controls_id FK
    }

    ACCOUNT_CONTROLS {
        bigint id PK
        string plan "display name, e.g. Basic"
        string description
        bigint monthly_price_cents "null/0 = never billed"
        int max_users
        int max_servers "enforced on service creation"
        int trial_days "null = not a trial plan"
    }

    SFTP_SERVICE {
        bigint id PK
        string name
        string host "auto-assigned, not user-editable"
        string description
        bigint user_id FK "owner"
    }

    SFTP_SERVICE_ACCOUNT {
        bigint id PK
        string authentication_type "PASSWORD or PUBLIC_KEY"
        string username UK "globally unique across ALL services"
        string email
        string password "bcrypt hash, write-only in API"
        string public_key "OpenSSH format, as entered"
        string public_key_rfc4716 "auto-derived, for ProFTPD"
        boolean enabled
        string permissions "CSV: READ,WRITE,DELETE"
        bigint sftp_service_id FK
    }

    SFTP_SERVICE_IPWHITELIST {
        bigint id PK
        string ip_address "IPv4 or CIDR"
        boolean enabled
        bigint sftp_service_id FK
    }

    PAYMENTS {
        bigint id PK
        bigint user_id FK
        bigint amount_cents
        string currency
        string status "SUCCEEDED or FAILED"
        string card_used "PRIMARY or BACKUP"
        string card_display "e.g. visa •••• 4242"
        string description
        string gateway_payment_id
        string failure_reason
        string initiated_by "SIGNUP / SCHEDULER / ADMIN:email / PORTAL:email"
        datetime created_at
    }

    PORTAL_USERS {
        bigint id PK
        string google_email UK
        string google_name
        string google_picture
        bigint user_id FK
        datetime last_login
    }

    EMAIL_VERIFICATIONS {
        bigint id PK
        string email "matched by string, no FK"
        string code "6-digit"
        boolean verified
        datetime expires_at
    }

    PASSWORD_RESETS {
        bigint id PK
        string email "matched by string, no FK"
        string token UK
        boolean used
        datetime expires_at
    }

    RUNTIME_SETTINGS {
        bigint id PK
        string name UK "e.g. trialiplimit, sftphost001"
        text value
    }
```

## Notes

- **`EMAIL_VERIFICATIONS`** and **`PASSWORD_RESETS`** are intentionally
  **not** foreign-keyed to `USERS` — they're matched by the email address
  string. This is why they have no relationship line to `USERS` above; it's
  a deliberate simplification (verification/reset flows only need "does a
  code/token exist for this email", not a hard row link).
- **`RUNTIME_SETTINGS`** is a standalone key-value store with no
  relationships — used for `sftphost001` (SFTP host auto-assignment),
  `trialiplimit` (signup abuse cap), `welcomeemail`/`termsandconditions`
  (HTML email/T&C templates), among others.
- **Payment cards are never stored as raw numbers** — `USERS.cc_pm_id` /
  `backup_cc_pm_id` are opaque tokens from the payment gateway (Stripe, or
  the mock gateway in dev); `cc_brand`/`cc_last4`/`cc_expiry` are display-only
  metadata returned by the gateway.
- Three **Postgres views** (not JPA entities — created directly by
  `DataInitialiser` at startup) sit downstream of `SFTP_SERVICE_ACCOUNT`,
  `SFTP_SERVICE`, `USERS` and `SFTP_SERVICE_IPWHITELIST` for the ProFTPD SFTP
  host to query: `proftpd_users`, `proftpd_allowed_ips`, `proftpd_groups`.
  See [`process-sftp-provisioning.md`](process-sftp-provisioning.md) and
  `PROFTPD-SETUP.md`.
