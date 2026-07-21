# 3.3 Billing & Subscription

See `DOCUMENTATION.md` §3.3 for the element list. Three independent
entry points feed the same `chargeUser()` guardrail path in `BillingService`.

```mermaid
flowchart TD
    subgraph FIRST["First charge (onboarding / card added while unpaid)"]
        A1[Card saved] --> A2{"Priced, non-trial plan AND<br/>not already paid up AND has a card?"}
        A2 -->|No| A3[No charge triggered]
        A2 -->|Yes| A4[chargeUser guardrail path]
    end

    subgraph GUARD["chargeUser guardrails - shared by all three entry points"]
        A4 --> G1{"billing.enabled?<br/>live key needs allow-live-charges=true"}
        G1 -->|No| G2[Refused, logged]
        G1 -->|Yes| G3{Amount under max-charge-cents?}
        G3 -->|No| G2
        G3 -->|Yes| G4{Under per-user daily limit?}
        G4 -->|No| G2
        G4 -->|Yes| G5{Under global daily limit?}
        G5 -->|No| G2
        G5 -->|Yes| G6[Charge PRIMARY card via gateway]
        G6 --> G7{Success?}
        G7 -->|No| G8[Try BACKUP card]
        G8 --> G9{Success?}
        G7 -->|Yes| G10[Payment row: SUCCEEDED]
        G9 -->|Yes| G10
        G9 -->|No| G11[Payment row: FAILED both cards]
    end

    G10 --> A5[Activate: clear trial<br/>paidToDate = today + 1 month]
    G11 --> A6[7-day grace + payment-failed warning/email]

    subgraph NIGHTLY["Nightly renewal — BillingScheduler, 02:30 daily"]
        B1[Find users: onboarded, active,<br/>not locked/closed, priced plan,<br/>paidToDate <= today, has card] --> B2{scheduler dry-run mode?}
        B2 -->|Yes, default| B3["LOG ONLY: 'would charge $X' — no real action"]
        B2 -->|No| B4[chargeUser for each — same guardrail path above]
        B4 --> B5{Success?}
        B5 -->|Yes| B6[paidToDate += 1 month]
        B5 -->|No| B7[sendPaymentFailedEmail]
    end

    subgraph ADMIN["Admin manual actions"]
        C1["'Charge card' button, typed amount"] --> C2[chargeUser guardrail path]
        C2 --> C3{"'Counts as subscription payment'<br/>checked AND charge succeeded?"}
        C3 -->|Yes| C4[paidToDate += 1 month, trial cleared]
        C3 -->|No| C5[Payment recorded only, nothing else changes]
        C6["'Mark paid +1 month' button"] --> C7["$0 payment record<br/>paidToDate += 1 month<br/>NO card charged"]
    end
```

**Key points**
- The guardrails (`billing.enabled`, `allow-live-charges`, per-charge cap,
  per-user/day cap, global/day cap) are the **only** gate between a live
  Stripe key and real money — `allow-live-charges` defaults to `false`
  specifically so switching to live keys can't accidentally start charging.
- The nightly scheduler defaults to **dry-run** — it only logs what it would
  do until deliberately switched off.
- Full test walkthrough (mock-mode test cards, Stripe test mode, go-live
  checklist) is in `BILLING-TESTING.md`.
