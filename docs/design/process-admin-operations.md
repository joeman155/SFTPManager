# 3.8 Admin Operations

See `DOCUMENTATION.md` §3.8 for the element list.

```mermaid
flowchart TD
    A[Admin browser] --> B["/oauth2/authorization/google-admin"]
    B --> C{"Google account's email matches<br/>a User row with role = 10?"}
    C -->|No| D["Redirect to admin-denied.html<br/>security context cleared"]
    C -->|Yes| E[Redirect into the Admin app]

    E --> F["Users:<br/>CRUD, lock/unlock, activate/deactivate,<br/>open/close, plan changes"]
    E --> G["SFTP Services / Service Accounts / IP Whitelist:<br/>CRUD across ALL customers"]
    E --> H["Account Controls:<br/>define plans — price, limits, trial days"]
    E --> I["Runtime Settings:<br/>key-value config — trialiplimit, sftphost001,<br/>email templates, terms & conditions"]
    E --> J["Billing panel (per user):<br/>payment history, Charge card,<br/>Mark Paid, Run Billing (site-wide)"]

    F --> K[Every change takes effect immediately]
    G --> K
    H --> K
    I --> K
    J --> K
    K --> L["No server restart needed for any of it<br/>(Runtime Settings changes are read live)"]
```

**Key points**
- Admin access is gated purely by `role = 10` on the `User` row — there's no
  separate admin-accounts table.
- Admin actions reuse the exact same services as the portal
  (`BillingService`, `SftpCredentialService`, etc.) rather than duplicating
  logic — e.g. an admin plan change runs through the identical proration
  code as a portal self-service upgrade (see
  [process-plan-changes.md](process-plan-changes.md)).
