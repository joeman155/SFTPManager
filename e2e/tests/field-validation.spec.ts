import { test, expect, type APIRequestContext } from '@playwright/test';
import { mutatingSpec, signupNewUser, type TestUser } from './helpers';

/**
 * Field validation through the portal APIs.
 *
 * The portal endpoints validate request bodies (@Valid on entity bodies;
 * programmatic bean validation for Map-bound bodies), so bad input must come
 * back as a clean 400 with a field→message map — and must never persist.
 *
 * WRITES TO THE DATABASE — localhost only (helpers.mutatingSpec).
 */
test.describe.serial('Field validation', () => {
  mutatingSpec();

  let user: TestUser;
  let ctx: APIRequestContext;
  let serviceId: number;

  test.beforeAll(async ({ playwright }) => {
    user = await signupNewUser(playwright, 'valid');
    ctx = user.ctx;
    const svc = await ctx.post('/portal/api/services', {
      data: { name: 'e2e-validation', description: 'validation target' },
    });
    serviceId = (await svc.json()).id;
  });

  test.afterAll(async () => {
    await ctx?.post('/portal/api/account/close').catch(() => {});
    await ctx?.dispose();
  });

  // ── Signup fields ──

  test('signup rejects a malformed email address', async ({ playwright }) => {
    const anon = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    });
    try {
      const res = await anon.post('/portal/api/auth/email-signin', {
        data: { email: 'not-an-email', password: 'longenough1', firstName: 'A', surname: 'B' },
      });
      expect(res.status()).toBe(400);
      expect((await res.json()).error).toContain('Invalid email');

      // And no account came into existence
      const check = await anon.post('/portal/api/auth/check-email', {
        data: { email: 'not-an-email' },
      });
      expect(await check.json()).toEqual({ newUser: true });
    } finally {
      await anon.dispose();
    }
  });

  // ── IP whitelist ──

  test('whitelist rejects malformed IP addresses', async () => {
    const badIps = ['999.999.1.1', 'abc.def.ghi.jkl', '10.0.0.1/99', '1.2.3', ''];

    for (const ipAddress of badIps) {
      const res = await ctx.post(`/portal/api/services/${serviceId}/whitelist`, {
        data: { ipAddress, enabled: true },
      });
      expect(res.status(), `ip="${ipAddress}"`).toBe(400);
      expect((await res.json()).ipAddress, `ip="${ipAddress}"`).toBeTruthy(); // field→message map
    }

    const list = await (await ctx.get(`/portal/api/services/${serviceId}/whitelist`)).json();
    for (const ipAddress of badIps) {
      expect(list.some((w: any) => w.ipAddress === ipAddress)).toBe(false);
    }
  });

  test('whitelist accepts valid IPs including CIDR ranges', async () => {
    for (const ipAddress of ['192.168.1.10', '10.0.0.0/24']) {
      const res = await ctx.post(`/portal/api/services/${serviceId}/whitelist`, {
        data: { ipAddress, enabled: true },
      });
      expect([200, 201], `ip="${ipAddress}"`).toContain(res.status());
    }
  });

  // ── SFTP service name ──

  test('service creation rejects a blank name', async () => {
    for (const name of ['', '   ']) {
      const res = await ctx.post('/portal/api/services', {
        data: { name, description: 'no name' },
      });
      expect(res.status(), `name="${name}"`).toBe(400);
      expect((await res.json()).name, `name="${name}"`).toContain('required');
    }
  });

  // ── SFTP account username ──

  test('sftp account rejects non-alphanumeric usernames', async () => {
    const badUsernames = ['has space', 'dollar$ign', 'semi;colon', 'dash-user', ''];

    for (const username of badUsernames) {
      const res = await ctx.post(`/portal/api/services/${serviceId}/accounts`, {
        data: { username, authenticationType: 'PASSWORD', password: 'Password1!', enabled: true },
      });
      expect(res.status(), `username="${username}"`).toBe(400);
      expect((await res.json()).username, `username="${username}"`).toBeTruthy();
    }

    const accounts = await (await ctx.get(`/portal/api/services/${serviceId}/accounts`)).json();
    for (const username of badUsernames) {
      expect(accounts.some((a: any) => a.username === username)).toBe(false);
    }
  });

  test('duplicate sftp usernames are rejected with a clear message', async () => {
    const username = `e2edup${Date.now()}`;
    const first = await ctx.post(`/portal/api/services/${serviceId}/accounts`, {
      data: { username, authenticationType: 'PASSWORD', password: 'Password1!', enabled: true },
    });
    expect([200, 201]).toContain(first.status());

    // Same username again — and case-insensitively
    for (const dup of [username, username.toUpperCase()]) {
      const res = await ctx.post(`/portal/api/services/${serviceId}/accounts`, {
        data: { username: dup, authenticationType: 'PASSWORD', password: 'Password1!', enabled: true },
      });
      expect(res.status(), `username="${dup}"`).toBeGreaterThanOrEqual(400);
      expect((await res.text()).toLowerCase()).toContain('taken');
    }
  });

  // ── Account profile fields ──

  test('profile update rejects an invalid phone number', async () => {
    const before = await (await ctx.get('/portal/api/account')).json();

    const res = await ctx.put('/portal/api/account', { data: { phone: 'not-a-phone!!' } });
    expect(res.status()).toBe(400);
    expect((await res.json()).phone).toContain('Invalid phone');

    const after = await (await ctx.get('/portal/api/account')).json();
    expect(after.phone).toBe(before.phone); // unchanged
  });

  test('profile update rejects an invalid postcode', async () => {
    const before = await (await ctx.get('/portal/api/account')).json();

    const res = await ctx.put('/portal/api/account', { data: { postcode: '@@' } });
    expect(res.status()).toBe(400);
    expect((await res.json()).postcode).toContain('Invalid postcode');

    const after = await (await ctx.get('/portal/api/account')).json();
    expect(after.postcode).toBe(before.postcode);
  });

  test('profile update accepts valid phone and postcode', async () => {
    const res = await ctx.put('/portal/api/account', {
      data: { phone: '+61 400 123 456', postcode: '6007' },
    });
    expect(res.status()).toBe(200);

    const after = await (await ctx.get('/portal/api/account')).json();
    expect(after.phone).toBe('+61 400 123 456');
    expect(after.postcode).toBe('6007');
  });
});
