import { test, expect, type APIRequestContext } from '@playwright/test';
import { mutatingSpec, signupNewUser, type TestUser } from './helpers';

/**
 * Account closing — every door must shut at once.
 * WRITES TO THE DATABASE — localhost only (helpers.mutatingSpec).
 */
test.describe.serial('Account closing', () => {
  mutatingSpec();

  let user: TestUser;
  let ctx: APIRequestContext;
  let serviceId: number;

  test.beforeAll(async ({ playwright }) => {
    user = await signupNewUser(playwright, 'close');
    ctx = user.ctx;
    // Leave something behind so we can prove closed-account data is not served
    const svc = await ctx.post('/portal/api/services', {
      data: { name: 'e2e-close-service', description: 'should become unreachable' },
    });
    serviceId = (await svc.json()).id;
  });

  test.afterAll(async () => {
    await ctx?.dispose();
  });

  test('closing an account without a session is a 401', async ({ playwright }) => {
    const anon = await playwright.request.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    });
    try {
      expect((await anon.post('/portal/api/account/close')).status()).toBe(401);
    } finally {
      await anon.dispose();
    }
  });

  test('the owner can close their account', async () => {
    const res = await ctx.post('/portal/api/account/close');

    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);
  });

  test('the session is dead immediately', async () => {
    expect((await ctx.get('/portal/api/me')).status()).toBe(401);
    expect((await ctx.get('/portal/api/services')).status()).toBe(401);
    expect((await ctx.get(`/portal/api/services/${serviceId}`)).status()).toBe(401);
  });

  test('re-login with the correct password is refused as closed', async () => {
    const res = await ctx.post('/portal/api/auth/email-signin', {
      data: { email: user.email, password: user.password },
    });

    expect(res.status()).toBe(403);
    expect((await res.json()).error).toContain('closed');
  });

  test('the email is still known to the system (data retained, not deleted)', async () => {
    const res = await ctx.post('/portal/api/auth/check-email', { data: { email: user.email } });

    expect(await res.json()).toEqual({ newUser: false });
  });

  test('forgot-password still answers neutrally for a closed account', async () => {
    const res = await ctx.post('/portal/api/auth/forgot-password', { data: { email: user.email } });

    // Privacy: the response must not reveal the account exists or is closed
    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);
  });
});
