# 3.5 Account Lifecycle (lock / deactivate / close)

See `DOCUMENTATION.md` §3.5 for the element list. Three independent boolean
flags on `User`, each with its own trigger and its own reversal path.

```mermaid
flowchart TD
    subgraph LOCKED["locked"]
        A1[3 failed password attempts] --> A2["locked = true"]
        A3[Admin toggles Locked switch] --> A2
        A2 --> A4[Only Admin can unlock — toggle switch<br/>also resets failed_login_attempts]
    end

    subgraph DEACTIVATED["servicesDeactivated"]
        B1["Trial/payment expiry<br/>(TrialExpiryScheduler)"] --> B2["servicesDeactivated = true"]
        B3[Admin toggles Active switch off] --> B2
        B4[Successful payment] --> B5["servicesDeactivated = false<br/>(automatic reactivation)"]
        B2 --> B6[Admin can also toggle back on manually]
    end

    subgraph CLOSED["accountClosed"]
        C1["User clicks 'Close my account'<br/>in the portal Account panel"] --> C2["Confirmation warning shown:<br/>services stop, immediate sign-out,<br/>no login until reopened"]
        C2 --> C3{Confirmed?}
        C3 -->|Yes| C4["accountClosed = true<br/>session invalidated immediately"]
        C3 -->|No| C5[Cancelled — nothing happens]
        C4 --> C6[Only Admin can reopen — toggle switch]
    end

    D["Any login/session check:<br/>email sign-in, Google sign-in,<br/>OR existing session's next API call"] --> E{locked OR accountClosed?}
    E -->|Yes| F["Refused —<br/>support-contact message shown<br/>session invalidated if mid-session"]
    E -->|No| G[Allowed to proceed]
```

**Key points**
- `locked` and `accountClosed` both **block login entirely**;
  `servicesDeactivated` only blocks *service access* — a deactivated user can
  still sign in to pay/reactivate.
- Every check happens at **three points**: email sign-in, the Google OAuth2
  success handler (redirects with a distinct error message per flag), and on
  every authenticated portal API call — so a flag flipped mid-session takes
  effect immediately, not just at next login.
- `accountClosed` also excludes the user from the nightly billing scheduler
  (see [process-billing-subscription.md](process-billing-subscription.md)).
