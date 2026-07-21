# 3.4 Plan Changes (upgrade / downgrade)

See `DOCUMENTATION.md` §3.4 for the element list. Same decision logic
(`BillingService.switchPaidPlan`) runs whether the change is initiated from
the portal's Change Plan modal or the admin's Account Controls dropdown.

```mermaid
flowchart TD
    A[User or Admin selects a different plan] --> B{Same plan as current?}
    B -->|Yes| C[No-op — nothing charged, nothing changed]

    B -->|No| D{"Currently paid up on a priced,<br/>non-trial plan? (paidToDate in future)"}
    D -->|No| E["Not in an active paid cycle —<br/>treat as a first charge<br/>(chargeFirstMonthIfDue on the new plan)"]

    D -->|Yes| F{New plan price >= old plan price?}

    F -->|Yes: upgrade or lateral| G["Prorate:<br/>daysRemaining = min(30, days to paidToDate)<br/>unusedCredit = oldPrice x daysRemaining / 30"]
    G --> H["amountDue = max(0, newPrice - unusedCredit)"]
    H --> I{amountDue == 0?}
    I -->|Yes| J["Switch plan now<br/>paidToDate = today + 1 month<br/>no charge needed"]
    I -->|No| K[Charge amountDue immediately]
    K --> L{Charge succeeded?}
    L -->|Yes| J
    L -->|No| M[Plan NOT changed — error returned to caller]

    F -->|No: downgrade| N{Who initiated this?}
    N -->|Admin| O["Switch plan immediately<br/>NO charge, paidToDate left AS-IS"]
    N -->|Portal self-service| P["Refused automatically —<br/>UI swaps to a message box"]
    P --> Q[User types a message, submits]
    Q --> R["Email to support@leederville.net:<br/>name, current plan, requested plan, message"]
    R --> S["Nothing changes automatically —<br/>a human actions it within 1 business day"]
```

**Key points**
- "Same plan" is blocked at both the UI (card greyed out, no click handler)
  and the server (`samePlan` short-circuit) — this closes the original bug
  report ("clicking Basic while already on Basic charges another $29").
- Proration uses a flat 30-day month for simplicity, capped so a user who
  has pre-paid several months ahead can't accumulate an oversized credit.
- Downgrades are the one case where **portal and admin genuinely diverge**:
  self-service downgrades always go to a human (fairness/anti-abuse); an
  admin's authority is trusted to apply one directly.
