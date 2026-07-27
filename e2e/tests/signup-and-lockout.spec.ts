import { test, expect } from '@playwright/test';
import { mutatingSpec } from './helpers';

/**
 * Full signup + account-lockout journey against the live app.
 *
 * WARNING: this creates a real user row (e2e+<timestamp>@example.com) in the
 * database the app is connected to — run against dev/test only. Each run uses
 * a fresh timestamped email, so it is repeatable without cleanup, but you may
 * want to periodically purge e2e+* users.
 *
 * Requires reCAPTCHA to be unconfigured (the default in dev), since signups
 * are captcha-checked when a secret key is present.
 */
test.describe.serial('Signup and lockout journey', () => {
  mutatingSpec();

  const email = `e2e+${Date.now()}@example.com`;
  const password = 'CorrectHorse1!';

  test('brand-new email signs up successfully', async ({ request }) => {
    const res = await request.post('/portal/api/auth/email-signin', {
      data: { email, password, firstName: 'E2E', surname: 'Test' },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.email).toBe(email);

    // Same request context now carries the session cookie
    const status = await request.get('/portal/api/auth/email-status');
    expect((await status.json())).toEqual({ authenticated: true, email });
  });

  test('the email is no longer reported as new', async ({ request }) => {
    const res = await request.post('/portal/api/auth/check-email', { data: { email } });

    expect(await res.json()).toEqual({ newUser: false });
  });

  test('wrong password is rejected with attempts remaining', async ({ request }) => {
    const res = await request.post('/portal/api/auth/email-signin', {
      data: { email, password: 'WrongPass1!' },
    });

    expect(res.status()).toBe(401);
    expect((await res.json()).error).toContain('attempt(s) remaining');
  });

  test('third wrong password locks the account', async ({ request }) => {
    // Attempt 2 of 3
    const second = await request.post('/portal/api/auth/email-signin', {
      data: { email, password: 'WrongPass1!' },
    });
    expect(second.status()).toBe(401);

    // Attempt 3 of 3 — lock
    const third = await request.post('/portal/api/auth/email-signin', {
      data: { email, password: 'WrongPass1!' },
    });
    expect(third.status()).toBe(403);
    expect((await third.json()).error).toContain('locked');
  });

  test('even the correct password is refused once locked', async ({ request }) => {
    const res = await request.post('/portal/api/auth/email-signin', {
      data: { email, password },
    });

    expect(res.status()).toBe(403);
    expect((await res.json()).error.toLowerCase()).toContain('locked');
  });
});
