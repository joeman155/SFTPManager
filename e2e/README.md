# SFTP Manager — Playwright E2E tests

End-to-end tests that drive the running application over HTTP: the admin and
portal security boundaries, the portal auth API, and a full signup → lockout
journey.

## One-time setup

```powershell
cd e2e
npm install
npx playwright install chromium
```

## Running

1. **Start the app** (NetBeans Run, or `mvn spring-boot:run`) with its
   PostgreSQL database and env vars available. Default target is
   `http://localhost:8080`.
2. Run the tests:

```powershell
npm test              # headless run
npm run test:headed   # watch the browser
npm run test:ui       # Playwright's interactive UI mode
npm run report        # open the HTML report of the last run
```

Target a different environment:

```powershell
$env:E2E_BASE_URL = "https://staging.example.net"; npm test
```

## What's covered

| Spec | Covers |
|---|---|
| `health.spec.ts` | `/healthz` liveness endpoint |
| `admin-security.spec.ts` | Anonymous users bounced off `/`, `/api/**`, `/admin/**`; login/denied pages public |
| `portal-login.spec.ts` | `/portal` redirect, sign-in form renders, logout round-trip |
| `portal-auth-api.spec.ts` | Auth config, check-email, signup validation rules, forgot-password privacy |
| `signup-and-lockout.spec.ts` | Real signup, session cookie, 3-strikes account lockout |
| `portal-authenticated.spec.ts` | Post-login journeys: profile, verification codes, onboarding, SFTP service CRUD + ownership isolation, service accounts, IP whitelist, plan rules & server limits, account update, account close |
| `onboarding.spec.ts` | Trial clock start, no-card 7-day grace, mock-card first-month charge, declined-card grace + warning, invalid card rejection |
| `plan-changes.spec.ts` | Paid-up user: invalid/trial/same-plan rejections, prorated upgrade with real (mock) charge, downgrade blocked → support request |
| `account-close.spec.ts` | Close kills session + all API access, re-login refused, data retained, forgot-password stays neutral |
| `field-validation.spec.ts` | Bad email/IP/username/phone/postcode rejected AND not persisted; valid values accepted; duplicate usernames refused case-insensitively |

## Cautions

- `signup-and-lockout.spec.ts` and `portal-authenticated.spec.ts` **write to
  the database** (throwaway `e2e+*@example.com` users, services, accounts).
  They are **auto-skipped unless the target is localhost**; to force them
  against a remote environment set `E2E_ALLOW_REMOTE_WRITES=1`. Purge `e2e+*`
  users whenever.
- After ~5 runs from the same IP the trial-abuse guard (`signup.trial-ip-limit`)
  hides trial plans and refuses onboarding for new signups — the onboarding
  test recognises the guard's refusal and treats it as a pass. Raise the
  `trialiplimit` runtime setting (or purge `e2e+*` users) if it gets in the way.
- Signup tests assume reCAPTCHA is unconfigured (`RECAPTCHA_SECRET_KEY` empty,
  the dev default); with a real key the captcha check will fail them.
- Google OAuth sign-in itself is not automated (third-party login pages are
  out of scope for Playwright); the tests verify the app's own email/password
  auth and that protected pages redirect correctly.
