# 3.2 Trial Management

See `DOCUMENTATION.md` §3.2 for the element list.

```mermaid
flowchart TD
    A[User has trialExpires set<br/>real trial plan OR grace period] --> B["TrialExpiryScheduler<br/>runs every hour"]
    B --> C{servicesDeactivated already true?}
    C -->|Yes| Z1[Skip - already deactivated]
    C -->|No| D{On a paid cycle? paidToDate set}

    D -->|Yes, paidToDate has passed| E[Deactivate services<br/>sendTrialExpiredEmail]
    D -->|No - trial/grace user| F{trialExpires today or earlier?}
    F -->|Yes| E
    F -->|No| G{trialExpires is TOMORROW<br/>AND warning not yet sent?}
    G -->|Yes| H[sendTrialWarningEmail<br/>set trialWarningSent = true]
    G -->|No| Z2[No action this run]

    E --> I[Portal shows deactivated banner<br/>services inaccessible]
    H --> J["Portal shows trial banner:<br/>'expires in N days' + Upgrade now button"]
    J --> K{User upgrades before expiry?}
    K -->|Yes| L["switchPaidPlan / chargeFirstMonthIfDue<br/>see process-plan-changes.md"]
    K -->|No| D
    L --> M[trialExpires cleared, paidToDate set]
```

**Key points**
- One scheduler, two distinct origins of `trialExpires`: a genuine free
  trial plan (`AccountControls.trialDays`), or a 7-day grace period granted
  when onboarding/upgrade happens with no card or a declined charge. The
  scheduler treats both identically.
- `trialWarningSent` prevents duplicate warning emails across the scheduler's
  hourly runs.
- The portal banner (shown on every page load while `trialExpires` is set)
  is the main visible surface of this process — see
  `DOCUMENTATION.md` §2.1 "Onboarding Wizard" / topbar banner.
