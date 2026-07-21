# 3.1 Sign-up & Onboarding

See `DOCUMENTATION.md` §3.1 for the element list (controllers/services involved).

```mermaid
flowchart TD
    A[Visitor opens Portal Login] --> B{Sign-in method}

    B -->|Email/Password| C[Enter email]
    C --> D{Email already registered?}
    D -->|No - new user| E[Enter name + password + captcha]
    E --> F["POST /auth/email-signin<br/>creates User, authType=EMAIL"]
    D -->|Yes| G[Enter password]
    G --> H{Password correct?}
    H -->|No, under 3 attempts| G
    H -->|No, 3rd failure| I[Account LOCKED]
    H -->|Yes| J[Session established]

    B -->|Google| K[OAuth2 consent screen]
    K --> L["First login: getMe creates User<br/>authType=GOOGLE"]

    F --> M[sendSignupNotification to support]
    L --> M
    J --> N{Already onboarded?}
    M --> N

    N -->|Yes| Z[Straight to portal dashboard]
    N -->|No| O[Step 1: Choose Plan]
    O --> P{Plan type}
    P -->|Trial plan| Q[Step 3: Contact Details<br/>payment step is skipped entirely]
    P -->|Paid plan| R[Step 2: Payment<br/>add card, or Skip for 7-day grace]
    R --> S[Step 3: Contact Details]
    Q --> S
    S --> T[Step 4: Accept Terms & Conditions]
    T --> U["POST /onboarding"]

    U --> V{Selected plan is a trial?}
    V -->|Yes| W[Set trialExpires<br/>no charge, paidToDate = null]
    V -->|No, has card| X[chargeFirstMonthIfDue]
    X --> Y{Charge succeeded?}
    Y -->|Yes| AA[Activate: clear trial<br/>paidToDate = today + 1 month]
    Y -->|No| AB[7-day grace trial + payment warning shown]
    V -->|No, no card| AC[7-day grace trial]

    W --> AD[sendWelcomeEmail +<br/>sendSignupNotification with outcome]
    AA --> AD
    AB --> AD
    AC --> AD
    AD --> Z
```

**Key points**
- Support (`support@leederville.net`) gets notified twice per completed
  signup: once at account creation, once at onboarding completion (with the
  plan chosen and whether payment succeeded) — the gap between the two is
  itself useful signal (accounts created but never onboarded).
- A locked account (3 failed attempts) can only be unlocked by an admin —
  see [process-account-lifecycle.md](process-account-lifecycle.md).
- The "no card" and "card declined" paths converge on the same 7-day grace
  mechanism as a real trial plan — see
  [process-trial-management.md](process-trial-management.md).
