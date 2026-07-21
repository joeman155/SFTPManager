# SFTP Manager — Solution Documentation

Covers: pages, forms and their fields, and the business processes that tie
them together. For payment-specific testing steps see `BILLING-TESTING.md`;
for the Linux SFTP server integration see `PROFTPD-SETUP.md`; for the data
model ER diagram and a flowchart of every business process below, see
[`docs/design/`](docs/design/README.md).

---

## 1. Pages

### 1.1 Customer Portal

| Page | File | Purpose |
|---|---|---|
| Portal Login | `portal-login.html` | Email/password sign-in + sign-up, Google sign-in, forgot-password link |
| Reset Password | `reset-password.html` | Set a new password from an emailed reset link |
| Email Verification | `portal-verify.html` | Landing page for the email-verification link's outcomes (success/expired/invalid/already-used) |
| **Portal (main app)** | `portal.html` | Single-page app: SFTP services list, service accounts, IP whitelist, account settings, billing/plan, onboarding wizard |

### 1.2 Admin Console

| Page | File | Purpose |
|---|---|---|
| Admin Login | `admin-login.html` | Google sign-in only (no email/password) |
| Access Denied | `admin-denied.html` | Shown when a non-admin Google account tries to sign in |
| **Admin (main app)** | `index.html` | Single-page app: Users, SFTP Services, Service Accounts, IP Whitelist, Account Controls (plans), Runtime Settings, Billing |

Both main apps are client-rendered: one HTML file per app, sections toggled
by JS (`page-*` divs in Admin; the whole `portal.html` for the Portal), all
data fetched from REST APIs (`/api/**` for Admin, `/portal/api/**` for
Portal).

---

## 2. Forms

Each entry lists: fields (name, type), required/validated fields, and the
endpoint it submits to.

### 2.1 Portal forms

#### Sign up / Sign in (`portal-login.html`)
| Field | Type | Notes |
|---|---|---|
| `email` | email | required |
| `password` | password | required, min 8 chars (sign-up only) |
| `firstName`, `surname` | text | shown only for new-user sign-up |
| Turnstile/reCAPTCHA token | hidden | verified server-side on sign-up |

→ `POST /portal/api/auth/email-signin` (also serves sign-up — server decides
new vs. existing by email). Google sign-in is a separate OAuth2 button
(`/oauth2/authorization/google-portal`), no form fields.

#### Forgot / Reset Password
| Field | Type |
|---|---|
| `email` | email (forgot-password request) |
| `password`, confirm | password (reset form, from emailed token link) |

→ `POST /portal/api/auth/forgot-password`, `POST /portal/api/auth/reset-password`

#### New/Edit SFTP Service
| Field | id | Type | Required |
|---|---|---|---|
| Service Name | `svcName` | text | yes |
| Purpose / Description | `svcDesc` | textarea | no |
| SFTP Host | — | read-only display | auto-assigned server-side, not submitted |

→ `POST` / `PUT /portal/api/services/{id}`. Host is never client-supplied —
assigned from the `sftphost001` runtime setting.

#### New/Edit Service Account
| Field | id | Type | Required |
|---|---|---|---|
| Username | `acctUser` | text | yes, alphanumeric only |
| Email | `acctEmail` | email | no |
| Permissions | `pRead`, `pWrite`, `pDelete` | checkboxes | at least conceptually independent; stored as `READ,WRITE,DELETE` CSV |
| Auth Type | `acctAuth` | select (PASSWORD / PUBLIC_KEY) | yes |
| Password | `acctPw` | password | required on create; blank on edit = keep current (stored bcrypt-hashed, never returned) |
| Public Key | `acctPk` | textarea | required if auth type is PUBLIC_KEY (OpenSSH format) |
| Enabled | `acctEnabled` | checkbox | — |

→ `POST` / `PUT /portal/api/services/{svcId}/accounts[/{id}]`. Username must
be globally unique across the whole system (enforced server-side, 409 on
conflict) since one Linux SFTP host serves every customer's accounts.

#### New/Edit IP Whitelist Entry
| Field | id | Type | Required |
|---|---|---|---|
| IP Address / CIDR | `ipAddr` | text | yes, validated IPv4/CIDR pattern |
| Enabled | `ipEnabled` | checkbox | — |

→ `POST` / `PUT /portal/api/services/{svcId}/whitelist[/{id}]`

#### My Account panel
| Field | id | Type |
|---|---|---|
| First Name | `ac-first-name` | text, required |
| Surname | `ac-surname` | text, required |
| Mobile Number | `ac-phone` | tel, required |
| Company | `ac-company` | text |
| Address Search / Line 1 / Line 2 / State / Postcode / Country | `ac-address-search`, `ac-address1`, `ac-address2`, `ac-state`, `ac-postcode`, `ac-country` | text (address search autocompletes via OpenStreetMap Nominatim) |
| Payment Cards | — | see §2.1 Card widget — not part of this form, saved separately |
| Change Password | `cp-current`, `cp-new`, `cp-confirm` | password (email/password users only) |
| Close Account | — | button, triggers confirm dialog, not a form |

→ `GET`/`PUT /portal/api/account`; password change via
`POST /portal/api/auth/change-password`; account closure via
`POST /portal/api/account/close`.

#### Card widget (Stripe Elements or mock form)
Reused in: onboarding step 2, Account panel (primary/backup), Change Plan
(when no card on file yet).
| Field | Mode | Notes |
|---|---|---|
| Card number | Stripe Element / `mock-cc-number` | Stripe mode: card digits never reach the server. Mock mode: client + server Luhn-validated |
| Expiry (MM/YY) | Stripe Element / `mock-cc-expiry` | validated not-expired |
| CVC | Stripe Element / `mock-cc-cvc` | 3–4 digits; verified then discarded, never stored (PCI DSS) |

→ Stripe mode: `POST /portal/api/billing/setup-intent` then
`POST /portal/api/billing/attach`. Mock mode: `POST /portal/api/billing/mock-save`.
Button reads **"Save card"** or **"Save card and Pay"** depending on whether
saving will trigger an immediate charge.

#### Onboarding Wizard (4 steps, shown once per new user)
1. **Choose Plan** — clickable plan cards (from Account Controls), no form fields
2. **Payment** — Card widget (above), or "Skip — start free trial"; hidden entirely for trial plans
3. **Contact Details** — `ob-phone` (tel, required)
4. **Terms & Conditions** — `ob-tc-accept` (checkbox, required; label text is clickable)

→ `GET`/`POST /portal/api/onboarding`

#### Change Plan modal
Not a data-entry form — a plan picker (cards, one per non-trial
`AccountControls` row) plus, depending on selection:
- **Same plan**: card is disabled, no action possible.
- **Upgrade / lateral**: a confirm button showing the (server-authoritative,
  client-estimated) prorated amount due.
- **Downgrade**: a `downgrade-message` textarea (required) instead of a
  charge button.

→ `POST /portal/api/account/plan` (upgrade/lateral),
`POST /portal/api/account/plan-request` (downgrade — emails support, changes nothing)

### 2.2 Admin forms

#### New/Edit User
| Field | id | Type | Required |
|---|---|---|---|
| First Name, Surname | `firstName`, `surname` | text | yes |
| Email | `email` | email | yes |
| Phone | `phone` | tel | validated format |
| Company, Company Size | `company`, `companySize` | text | no |
| Address Line 1/2, State, Postcode, Country | — | text | postcode format-validated |
| Account Controls / Plan | `accountControlsId` | select | no — triggers billing logic on change (see §3.4) |
| Payment Cards | — | read-only display only (`Primary:` / `Backup:` brand+last4+expiry) | managed via the portal or the Billing panel, not this form |
| Active | `servicesDeactivated` (inverted) | toggle switch | in the users table, not this form |
| Locked | `locked` | toggle switch | in the users table |
| Account (Open/Closed) | `accountClosed` | toggle switch | in the users table |

→ `POST` / `PUT /api/users[/{id}]`

#### New/Edit SFTP Service
| Field | id | Type | Required |
|---|---|---|---|
| Service Name | `sftpName` | text | yes |
| Owner (User) | `sftpUserId` | select | yes |
| Purpose / Description | `sftpDesc` | textarea | no |
| SFTP Host | — | read-only | auto-assigned, not submitted |

→ `POST` / `PUT /api/sftpservices[/{id}]`

#### New/Edit Service Account
Same fields as the portal's Service Account form (§2.1), plus an explicit
**SFTP Service** select (`acctSftpId`, required — admin can create accounts
under any customer's service). Same username-uniqueness and
blank-password-keeps-current rules.

→ `POST` / `PUT /api/sftpserviceaccounts[/{id}]`

#### New/Edit IP Whitelist Entry
Same as portal's, plus an explicit **SFTP Service** select.
→ `POST` / `PUT /api/sftpserviceipwhitelists[/{id}]`

#### New/Edit Account Controls (Plan)
| Field | id | Type | Required |
|---|---|---|---|
| Plan Name | `ctrlPlan` | text | yes |
| Monthly Price ($) | `ctrlPrice` | number | no — blank/0 = never billed |
| Max Users | `ctrlMaxUsers` | number | yes |
| Max Servers | `ctrlMaxServers` | number | yes — enforced against portal service creation |
| Trial Days | `ctrlTrialDays` | number | no — blank = not a trial plan; set = marks it as a free, time-limited, unbilled plan |
| Description | `ctrlDesc` | textarea | shown to customers when choosing a plan |

→ `POST` / `PUT /api/accountcontrols[/{id}]`

#### New/Edit Runtime Setting
| Field | id | Type |
|---|---|---|
| Name | `stgName` | text, required |
| Value | `stgValue` | textarea |

→ `POST` / `PUT /api/runtimesettings[/{id}]`. Key-value store used for:
`sftphost001` (host auto-assignment), `welcomeemail` / `termsandconditions`
(HTML templates), `trialiplimit` (see §3.7).

#### Billing panel (per user)
| Field | id | Type |
|---|---|---|
| Amount ($) | `chg-amount` | number, required |
| Description | `chg-desc` | text |
| Counts as subscription payment | `chg-extend` | checkbox, checked by default |
| Card entry (Add/Replace) | — | same Card widget as the portal |

Three actions: **Charge card** (`POST /api/billing/charge/{userId}`),
**Mark paid +1 month, no charge** (`POST /api/billing/mark-paid/{userId}`),
**Run billing** (site-wide, `POST /api/billing/run-billing`).

---

## 3. Business Processes

Each process below has a matching flowchart in
[`docs/design/`](docs/design/README.md) (e.g. §3.1 ↔
`docs/design/process-signup-onboarding.md`).

### 3.1 Sign-up & Onboarding
**Elements:** `portal-login.html` → `PortalAuthController` (email path) or
Google OAuth2 (`PortalSecurityConfig`) → `PortalController.getMe` (creates
the `User` row on first Google login) → onboarding wizard → `AccountControls`
(plan selection) → `BillingService` (card + first charge) →
`EmailService.sendWelcomeEmail` + `sendSignupNotification` (to support).

Flow: user authenticates (email+password or Google) → if not yet onboarded,
the 4-step wizard runs → plan choice determines the path (trial vs. paid) →
`user.onboarded = true` → welcome email sent → support notified of the
signup outcome (trial / paid / payment failed).

### 3.2 Trial Management
**Elements:** `User.trialExpires`, `AccountControls.trialDays`,
`TrialExpiryScheduler` (hourly), trial banner in `portal.html` header.

- A trial plan (`trialDays > 0`) sets `trialExpires = today + trialDays`,
  `paidToDate = null`, never billed.
- A paid plan chosen with no card, or a failed first charge, gets a 7-day
  **grace trial** (same mechanism, different origin).
- `TrialExpiryScheduler` runs hourly: warns the day before expiry
  (`sendTrialWarningEmail`), deactivates (`servicesDeactivated = true`) on
  expiry (`sendTrialExpiredEmail`).
- The portal shows a dismissable-by-upgrade banner with days remaining and
  an "Upgrade now" button once `trialExpires` is set.

### 3.3 Billing & Subscription (Stripe or mock gateway)
**Elements:** `BillingService`, `PaymentGateway` (`StripeGateway` /
`MockGateway`), `Payment` entity + `PaymentRepository`, `BillingScheduler`
(nightly), guardrail settings in `application.properties`.

- **Card storage:** tokenized (Stripe payment-method id, or a mock token) —
  card numbers/CVV never touch the app's database. Brand/last4/expiry kept
  for display only. Primary + backup card slots per user.
- **First charge:** happens immediately at onboarding / when a card is added
  to an unpaid priced-plan account (`chargeFirstMonthIfDue`) — not on a
  delay.
- **Renewals:** `BillingScheduler` (02:30 daily) charges any user whose
  `paidToDate` has arrived, primary card then backup on failure, extends
  `paidToDate` by one month on success. Starts in **dry-run** (log only).
- **Guardrails** (all in `application.properties`): master enable switch,
  `allow-live-charges` (blocks a live Stripe key from taking real money until
  explicitly flipped), max charge amount, max charges per user/day, max
  charges globally/day.
- **Admin manual actions:** Charge card (optionally extends the
  subscription), Mark Paid (extends without charging — for off-platform
  payments), Run Billing (triggers the scheduler on demand).

### 3.4 Plan Changes (upgrade / downgrade)
**Elements:** `BillingService.switchPaidPlan`, Change Plan modal (portal),
Account Controls dropdown (admin), `EmailService.sendPlanChangeRequest`.

Decision tree (same logic for portal self-service and admin-driven changes):
1. **Same plan reselected** → no-op, nothing charged.
2. **Currently paid up, moving to a higher/equal price** → prorated: unused
   value of the current cycle (days remaining, capped at 30) is credited
   against the new plan's price; only the difference is charged now;
   `paidToDate` resets to one month from today on success.
3. **Currently paid up, moving to a lower price** →
   - Portal (self-service): **not automatic** — routes to a message box that
     emails support@leederville.net; nothing changes until a human actions it.
   - Admin: applied immediately, no charge, existing `paidToDate` left as-is.
4. **Not currently in a paid, active cycle** (trial/expired/none) → normal
   first-month billing (as in onboarding).
5. **Assigned a trial plan** (admin only) → starts a fresh trial clock.

### 3.5 Account Lifecycle (lock / deactivate / close)
**Elements:** `User.locked`, `User.servicesDeactivated`, `User.accountClosed`,
admin toggle switches, `PortalSecurityConfig` (Google login gate),
`PortalAuthController` (email login gate), `PortalController.currentUser`
(defense-in-depth on every API call).

Three independent flags, each blocking login for a different reason:
- **Locked** — auto-set after 3 failed password attempts, or manually by
  admin. Only admin can unlock (toggle in Users table).
- **Deactivated** (`servicesDeactivated`) — set by the trial/billing
  schedulers on non-payment, or manually. Reversed by payment or admin toggle.
- **Closed** (`accountClosed`) — user-initiated, self-service, with a
  confirmation warning (`POST /portal/api/account/close` — flags the account
  and invalidates the session immediately). Only admin can reopen (toggle in
  Users table). Excluded from the billing scheduler.

All three are checked at: email sign-in, Google sign-in success handler
(redirects with a distinct error + support-email message), and on every
authenticated portal API call (so an already-open session is cut off
immediately, not just at next login).

### 3.6 SFTP Provisioning (ProFTPD integration)
**Elements:** `SftpService`, `SftpServiceAccount`, `SftpServiceIpWhitelist`,
`SftpCredentialService` (bcrypt hashing, RFC 4716 key conversion, username
uniqueness), `DataInitialiser` (creates/grants three Postgres views at
startup), ProFTPD on the Linux SFTP host (see `PROFTPD-SETUP.md`).

- One SFTP Service = one shared home directory (`/srv/sftp/svc<id>`); every
  account under it sees the same files (all run as uid/gid 2001).
- Passwords stored bcrypt-hashed; public keys stored in both OpenSSH and
  RFC 4716 form.
- Three SQL views ProFTPD reads directly: `proftpd_users` (login table,
  filtered to enabled accounts of non-deactivated/locked/closed owners —
  every admin kill-switch applies to real SFTP logins with no extra
  wiring), `proftpd_allowed_ips` (IP whitelist), `proftpd_groups`
  (synthetic READ/WRITE/DELETE groups mapped from the `permissions` column,
  enforced by ProFTPD `<Limit>` blocks — filesystem ownership stays uniform,
  permissions are enforced at the protocol layer).
- Grants on those views are re-applied automatically on every app restart
  (schema is rebuilt by `ddl-auto=create`, which would otherwise silently
  drop them).

### 3.7 Signup Abuse Prevention
**Elements:** `User.signupIp`, `RequestIp` util, `runtime_settings` row
`trialiplimit` (default 5), `PortalController.isTrialBlocked`.

- Every new account's IP is captured at signup (both email and Google paths).
- Once more than `trialiplimit` accounts share a signup IP, further accounts
  from that IP silently don't see the trial plan and can't use the no-card
  grace period — they can still sign up and become paying customers.
  Deliberately non-descript: no error reveals the mechanism.
- Admin-visible: signup IP shown under each user's email in the Users table
  and in the Edit User form; cap editable live via Runtime Settings.

### 3.8 Admin Operations
**Elements:** all admin forms in §2.2, `AdminSecurityConfig`
(Google-OAuth-only, role-gated), `Payment` history views, Billing panel.

Covers day-to-day management: user CRUD, SFTP service/account/whitelist CRUD
across all customers, plan/pricing definition (Account Controls), runtime
configuration, and the billing actions described in §3.3–3.4. Admin access
requires `role = 10` on the `User` row (checked in the OAuth2 success
handler; non-admins are redirected to `admin-denied.html`).
