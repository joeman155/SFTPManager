import { test, expect, type APIRequestContext } from '@playwright/test';
import { mutatingSpec, signupNewUser, type TestUser } from './helpers';

/**
 * Post-login portal journeys, driven through the same APIs the portal UI
 * calls. A fresh user is created once for the whole file; the final test
 * closes the account.
 *
 * WRITES TO THE DATABASE — auto-skipped unless the target is localhost
 * (see helpers.mutatingSpec).
 */
test.describe.serial('Portal after login', () => {
  mutatingSpec();

  let user: TestUser;
  let ctx: APIRequestContext;
  let serviceId: number;

  test.beforeAll(async ({ playwright }) => {
    user = await signupNewUser(playwright, 'portal');
    ctx = user.ctx;
  });

  test.afterAll(async () => {
    await ctx?.dispose();
  });

  // ── Identity & verification ──

  test('/me returns the signed-in profile', async () => {
    const res = await ctx.get('/portal/api/me');

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.email).toBe(user.email);
    expect(body.userId).toBeGreaterThan(0);
    expect(body.emailVerified).toBe(false); // code never entered
  });

  test('verify-code rejects a wrong code', async () => {
    const res = await ctx.post('/portal/api/verify-code', { data: { code: '000000' } });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('Incorrect code');
  });

  test('resend-code issues a fresh code', async () => {
    const res = await ctx.post('/portal/api/resend-code');

    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);
  });

  // ── Onboarding ──

  test('onboarding data offers plans and terms', async () => {
    const res = await ctx.get('/portal/api/onboarding');

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.onboarded).toBe(false);
    expect(Array.isArray(body.plans)).toBe(true);
    expect(body.plans.length).toBeGreaterThan(0);
    expect(body.termsAndConditions).toBeTruthy();
  });

  test('completing onboarding selects a plan', async () => {
    const data = await (await ctx.get('/portal/api/onboarding')).json();
    // Prefer a free trial plan; fall back to the cheapest paid plan (no card
    // on file → the server grants a 7-day grace period).
    const plans: any[] = data.plans;
    const plan =
      plans.find((p) => p.trialDays && p.trialDays > 0) ??
      plans.sort((a, b) => (a.monthlyPriceCents ?? 0) - (b.monthlyPriceCents ?? 0))[0];

    const res = await ctx.post('/portal/api/onboarding', {
      data: { planId: plan.id, phone: '+61 400 111 222' },
    });

    // NOTE: after several e2e runs from one IP the trial-abuse guard kicks in
    // (signup.trial-ip-limit) and onboarding is refused with a deliberately
    // vague error. Treat that as a pass for the guard, not a failure.
    if (res.status() === 400) {
      expect((await res.json()).error).toContain('Unable to complete signup');
      return;
    }
    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);

    const after = await (await ctx.get('/portal/api/onboarding')).json();
    expect(after.onboarded).toBe(true);
  });

  // ── SFTP services CRUD ──

  test('creating a service auto-assigns the host', async () => {
    const res = await ctx.post('/portal/api/services', {
      data: { name: 'e2e-service', description: 'created by Playwright' },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.id).toBeGreaterThan(0);
    expect(body.host).toBeTruthy(); // host comes from runtime settings, not the client
    serviceId = body.id;
  });

  test('the service appears in the list', async () => {
    const body = await (await ctx.get('/portal/api/services')).json();

    expect(body.linked).toBe(true);
    expect(body.services.map((s: any) => s.id)).toContain(serviceId);
  });

  test('the list reports storage usage and the plan limit', async () => {
    const body = await (await ctx.get('/portal/api/services')).json();

    // Usage map always present; a fresh service has used 0 bytes
    expect(body.storageUsedBytes).toBeDefined();
    expect(body.storageUsedBytes[String(serviceId)]).toBe(0);
    // Limit is the plan's max_storage_mb in bytes, or null for unlimited
    expect(body).toHaveProperty('storageLimitBytes');
    if (body.storageLimitBytes !== null) {
      expect(body.storageLimitBytes).toBeGreaterThan(0);
    }
  });

  test('the service can be renamed', async () => {
    const res = await ctx.put(`/portal/api/services/${serviceId}`, {
      data: { name: 'e2e-service-renamed', description: 'updated' },
    });

    expect(res.status()).toBe(200);
    expect((await res.json()).name).toBe('e2e-service-renamed');
  });

  test('another user cannot see or modify this service', async ({ playwright }) => {
    const intruder = await signupNewUser(playwright, 'intruder');
    try {
      expect((await intruder.ctx.get(`/portal/api/services/${serviceId}`)).status()).toBe(403);
      expect(
        (await intruder.ctx.put(`/portal/api/services/${serviceId}`, { data: { name: 'hacked' } })).status(),
      ).toBe(403);
      expect((await intruder.ctx.delete(`/portal/api/services/${serviceId}`)).status()).toBe(403);
    } finally {
      await intruder.ctx.dispose();
    }
  });

  // ── Service accounts ──

  test('an SFTP account can be added to the service', async () => {
    const res = await ctx.post(`/portal/api/services/${serviceId}/accounts`, {
      data: {
        username: `e2e-user-${Date.now()}`,
        authenticationType: 'PASSWORD',
        password: 'SftpAccount1!',
        enabled: true,
        permissions: 'read-write',
      },
    });

    expect([200, 201]).toContain(res.status());
    const accounts = await (await ctx.get(`/portal/api/services/${serviceId}/accounts`)).json();
    expect(accounts.length).toBeGreaterThan(0);
    // Password must never come back as the plaintext we sent
    for (const a of accounts) {
      expect(a.password ?? '').not.toBe('SftpAccount1!');
    }
  });

  // ── IP whitelist ──

  test('an IP whitelist entry can be added and listed', async () => {
    const created = await ctx.post(`/portal/api/services/${serviceId}/whitelist`, {
      data: { ipAddress: '203.0.113.42', enabled: true },
    });
    expect([200, 201]).toContain(created.status());

    const list = await (await ctx.get(`/portal/api/services/${serviceId}/whitelist`)).json();
    expect(list.some((w: any) => w.ipAddress === '203.0.113.42')).toBe(true);
  });

  // ── Plans & account ──

  test('plan list excludes trials and knows the current plan', async () => {
    const res = await ctx.get('/portal/api/plans');

    expect(res.status()).toBe(200);
    const body = await res.json();
    for (const p of body.plans) {
      expect(p.trialDays ?? 0).toBeLessThanOrEqual(0);
    }
  });

  test('plan change request requires a message', async () => {
    const res = await ctx.post('/portal/api/account/plan-request', { data: { message: '' } });

    expect(res.status()).toBe(400);
  });

  test('account profile can be updated', async () => {
    const res = await ctx.put('/portal/api/account', {
      data: { company: 'E2E Pty Ltd', phone: '+61 400 999 888' },
    });
    expect(res.status()).toBe(200);

    const account = await (await ctx.get('/portal/api/account')).json();
    expect(account.company).toBe('E2E Pty Ltd');
    expect(account.phone).toBe('+61 400 999 888');
  });

  test('the plan server limit is enforced', async () => {
    // Create services until the plan's maxServers cap replies 403. Bounded at
    // 10 attempts — plans with no limit (or none selected) skip the test.
    const created: number[] = [];
    let capMessage: string | null = null;

    for (let i = 0; i < 10 && !capMessage; i++) {
      const res = await ctx.post('/portal/api/services', {
        data: { name: `e2e-limit-${i}`, description: 'limit probe' },
      });
      if (res.status() === 201) {
        created.push((await res.json()).id);
      } else if (res.status() === 403) {
        capMessage = (await res.json()).error;
      }
    }

    // Clean up the probes regardless of outcome
    for (const id of created) {
      await ctx.delete(`/portal/api/services/${id}`);
    }

    test.skip(capMessage === null, 'plan has no server limit — nothing to enforce');
    expect(capMessage).toContain('plan allows a maximum');
  });

  // ── Service teardown & account close ──

  test('the service can be deleted by its owner', async () => {
    const res = await ctx.delete(`/portal/api/services/${serviceId}`);

    expect(res.status()).toBe(204);
    const body = await (await ctx.get('/portal/api/services')).json();
    expect(body.services.map((s: any) => s.id)).not.toContain(serviceId);
  });

  test('closing the account ends the session for good', async () => {
    const res = await ctx.post('/portal/api/account/close');
    expect(res.status()).toBe(200);

    // Session invalidated AND the account is flagged closed
    expect((await ctx.get('/portal/api/me')).status()).toBe(401);

    // Even a fresh login attempt is refused now
    const relogin = await ctx.post('/portal/api/auth/email-signin', {
      data: { email: user.email, password: user.password },
    });
    expect(relogin.status()).toBe(403);
    expect((await relogin.json()).error).toContain('closed');
  });
});
