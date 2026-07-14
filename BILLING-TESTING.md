# Billing — how to test safely, and how to go live

The app now takes payments through Stripe, with a built-in **mock mode** so you
can test everything with **zero cost and zero risk**. Card numbers and CVV
**never touch your server or database** — in Stripe mode they go from the
customer's browser straight to Stripe; we store only opaque IDs plus
brand / last-4 / expiry for display.

---

## The three modes

| Mode | When | Real money? |
|---|---|---|
| **Mock** | No Stripe keys configured (default right now) | Never — everything simulated in-app |
| **Stripe TEST** | `sk_test_...` / `pk_test_...` keys set | Never — Stripe simulates with test cards |
| **Stripe LIVE** | `sk_live_...` / `pk_live_...` keys set **AND** `billing.allow-live-charges=true` | Yes |

The admin Users page shows the current mode next to the "Run billing" button
(e.g. `Billing: mock · scheduler DRY-RUN`).

## Built-in abuse guardrails (application.properties)

| Setting | Default | What it protects against |
|---|---|---|
| `billing.enabled` | `true` | Master switch — set `false` to stop ALL charging instantly |
| `billing.allow-live-charges` | **`false`** | A live Stripe key **cannot take real money** until you flip this. This is the main "can't accidentally go live" protection. |
| `billing.max-charge-cents` | `50000` ($500) | A typo like $2900 instead of $29.00 gets refused |
| `billing.max-charges-per-user-per-day` | `2` | Double-billing loops |
| `billing.max-charges-per-day` | `50` | A runaway job spamming charges globally |
| `billing.scheduler.dry-run` | **`true`** | The nightly billing job only LOGS what it would charge until you set `false` |
| `billing.scheduler.enabled` | `true` | Set `false` to turn the nightly job off entirely |

Every charge — manual or scheduled — passes through the same guardrails.
To later disable/relax them, edit these values and restart. Nothing needs code changes.

---

## Phase 1 — test in MOCK mode (no Stripe account needed)

Just run the app with no Stripe env vars (as now).

**Test cards (mimic Stripe's):**
| Card number | Behaviour |
|---|---|
| `4242 4242 4242 4242` | Saves fine, charges succeed |
| `4000 0000 0000 0002` | Saves fine, every charge is **declined** (tests backup-card fallback) |
| `4000 0000 0000 0101` | Rejected at save time — simulates **CVV check failure** |

Any expiry like `12/27` and any 3-digit CVC.

Cards can be entered from **either side**: the portal (Account → Payment Cards
or onboarding step 2) or the admin screen (Users → `$` button → Add card /
Replace / Remove, for both primary and backup). In both cases the card number
goes from that browser directly to the payment provider — never via the server.

**Walkthrough:**
1. Portal → sign up / log in → onboarding step 2 (or Account → Payment Cards).
2. Save `4242…4242` as Primary — should show "VISA •••• 4242".
3. Save `4000…0002` as Primary and `4242…4242` as Backup on another test user.
4. Admin → Users → `$` button on a user → enter $29.00 → **Charge card**.
   - User from step 2: payment history shows SUCCEEDED on the primary card.
   - User from step 3: history shows FAILED (primary) then SUCCEEDED (backup).
5. Try charging $600 → refused by the max-charge cap.
6. Charge the same user 3 times in a day → third refused (per-user daily limit).
7. Admin → **Run billing** → alert shows a DRY-RUN summary; nothing is charged
   (check the app log for `BILLING SCHEDULER [DRY-RUN]` lines).
8. Set `billing.scheduler.dry-run=false`, restart, set a test user's
   `paid_to_date` to today, **Run billing** → real (mock) charge appears in
   history and `paid_to_date` advances one month.

Note: mock-mode saved cards live in memory — restarting the app forgets the
"declined" flag (display data persists in the DB).

## Phase 2 — Stripe TEST mode (still $0, but the real integration)

1. Sign up free at https://dashboard.stripe.com (no card required to use test mode).
2. Dashboard → Developers → API keys → copy the **test** keys.
3. Set environment variables and restart:
   ```
   STRIPE_SECRET_KEY=sk_test_xxxxxxxx
   STRIPE_PUBLISHABLE_KEY=pk_test_xxxxxxxx
   ```
4. The portal card form becomes a real Stripe Element. Use Stripe's test cards:
   - `4242 4242 4242 4242` — success
   - `4000 0000 0000 0002` — declined at charge time
   - `4000 0000 0000 0101` — fails the CVC check
   - `4000 0025 0000 3155` — requires 3-D Secure authentication
   (any future expiry, any CVC — full list: https://docs.stripe.com/testing)
5. Repeat the Phase-1 walkthrough. Charges appear in the Stripe dashboard
   (test mode) so you can cross-check.
6. **CVV enforcement:** in the Stripe dashboard go to Settings → Radar → Rules
   and make sure "Decline payments that fail CVC verification" is ON.

## Phase 3 — going LIVE (only when ready)

1. Activate your Stripe account (business details + bank account for payouts).
2. Swap env vars to the `sk_live_` / `pk_live_` keys.
3. Set `billing.allow-live-charges=true` (until you do, the app refuses every
   charge and logs an ERROR reminding you).
4. Keep `billing.scheduler.dry-run=true` for the first night and read the log
   to sanity-check who would be billed and how much; then set it `false`.
5. Optionally raise/lower the caps (`billing.max-charge-cents` etc.).

Costs: no monthly fee; ~1.7% + 30¢ per successful domestic AU transaction,
deducted from the payment itself. Test mode and mock mode are free forever.

---

## What we store vs. what we never store

| Stored in our DB | Never stored anywhere by us |
|---|---|
| Stripe customer ID (`cus_…`) | Full card number (PAN) |
| Payment method IDs (`pm_…`) primary + backup | CVV / CVC (PCI forbids storing it — it's verified at save time, then discarded) |
| Brand, last 4, expiry (display only) | Cardholder name (Stripe keeps it) |
| Payment records (amount, status, failure reason) | |

## How automated billing works

Nightly at 02:30 the `BillingScheduler` finds users who are onboarded, active,
not locked, have a plan with a price and a saved card, and whose `paid_to_date`
is today or earlier. Each gets charged one month of their plan (primary card,
backup on failure). Success advances `paid_to_date` by one month; failure emails
the user, and the existing trial-expiry job deactivates them the following day
if still unpaid. Plan prices live in the `account_controls` table
(`monthly_price_cents`, seeded: Basic $29, Enterprise $99) — editable from the
admin screen's Account Controls page.
