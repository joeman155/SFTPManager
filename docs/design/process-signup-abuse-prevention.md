# 3.7 Signup Abuse Prevention

See `DOCUMENTATION.md` §3.7 for the element list.

```mermaid
flowchart TD
    A["New account created<br/>(email/password OR first Google login)"] --> B["Capture request IP<br/>into User.signup_ip"]
    B --> C[Later: user reaches onboarding Step 1]
    C --> D["countBySignupIp(ip)"]
    D --> E{"count > trialiplimit?<br/>(runtime_settings, default 5)"}

    E -->|No| F["Trial plan shown normally<br/>7-day grace period also available"]
    E -->|Yes: trialBlocked| G["Trial plan SILENTLY OMITTED<br/>from the plan list — no error, no clue"]

    G --> H{"User tries to bypass —<br/>e.g. calls the onboarding API directly<br/>requesting the trial plan id?"}
    H -->|Yes| I["Non-descript error:<br/>'Unable to complete signup. Please contact support.'<br/>(deliberately reveals nothing about WHY)"]
    H -->|No — picks a paid plan + valid card| J[Normal paid signup proceeds]

    F --> K["Admin visibility:<br/>signup_ip shown under each user's email<br/>in the Users table + Edit User form"]
    G --> K
    K --> L["Admin can raise/lower the cap<br/>live via Runtime Settings — no restart"]
```

**Key points**
- The block is on the **trial offer**, not on signup itself — nobody who
  intends to pay is ever turned away, which is why the cap (5) can be set
  tight without risking false positives on shared IPs (offices, mobile
  carrier NAT).
- The limit counts **all** accounts from an IP (paid or trial), so deleting
  stale test accounts frees up headroom for that IP.
- Known limitation: a determined abuser can rotate IPs (VPN); this raises
  the effort bar substantially rather than closing the door completely.
