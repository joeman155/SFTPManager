import { test, expect } from '@playwright/test';

/**
 * API-level checks of the portal auth endpoints (all permitAll, so they are
 * reachable without a session). These don't create any data.
 */
test.describe('Portal auth API', () => {
  test('auth config exposes the support email', async ({ request }) => {
    const res = await request.get('/portal/api/auth/config');

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.supportEmail).toContain('@');
  });

  test('check-email reports an unknown address as a new user', async ({ request }) => {
    const res = await request.post('/portal/api/auth/check-email', {
      data: { email: `nobody-${Date.now()}@example.com` },
    });

    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual({ newUser: true });
  });

  test('check-email without an email is a 400', async ({ request }) => {
    const res = await request.post('/portal/api/auth/check-email', { data: {} });

    expect(res.status()).toBe(400);
  });

  test('email-signin requires email and password', async ({ request }) => {
    const res = await request.post('/portal/api/auth/email-signin', {
      data: { email: '', password: '' },
    });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('required');
  });

  test('new signup rejects passwords under 8 characters', async ({ request }) => {
    const res = await request.post('/portal/api/auth/email-signin', {
      data: { email: `nobody-${Date.now()}@example.com`, password: 'short' },
    });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('at least 8 characters');
  });

  test('new signup rejects missing first name and surname', async ({ request }) => {
    const res = await request.post('/portal/api/auth/email-signin', {
      data: { email: `nobody-${Date.now()}@example.com`, password: 'longenough1' },
    });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('First name and surname');
  });

  test('forgot-password never reveals whether an email exists', async ({ request }) => {
    const res = await request.post('/portal/api/auth/forgot-password', {
      data: { email: `nobody-${Date.now()}@example.com` },
    });

    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);
  });

  test('email-status is unauthenticated for a fresh context', async ({ request }) => {
    const res = await request.get('/portal/api/auth/email-status');

    expect(res.status()).toBe(200);
    expect((await res.json()).authenticated).toBe(false);
  });
});
