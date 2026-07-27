import { test, expect } from '@playwright/test';

/**
 * The admin UI lives at "/" (index.html) and everything under /api/** and
 * /admin/** requires a Google-authenticated admin. Unauthenticated visitors
 * must be bounced to the admin login page — these tests prove no admin
 * surface leaks to anonymous users.
 */
test.describe('Admin security', () => {
  test('anonymous visit to / is redirected to the admin login page', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/admin-login\.html/);
  });

  test('admin login page is publicly reachable', async ({ page }) => {
    const res = await page.goto('/admin-login.html');

    expect(res?.status()).toBe(200);
  });

  test('admin-denied page is publicly reachable', async ({ page }) => {
    const res = await page.goto('/admin-denied.html');

    expect(res?.status()).toBe(200);
  });

  test('anonymous /api request does not return data', async ({ request }) => {
    // Spring redirects browserish requests to the login page rather than
    // serving data; either a redirect (3xx) or an auth error (401/403) is
    // acceptable — a 200 with JSON would be a security hole.
    const res = await request.get('/api/users', { maxRedirects: 0 });

    expect([301, 302, 303, 401, 403]).toContain(res.status());
  });

  test('anonymous /admin request does not return data', async ({ request }) => {
    const res = await request.get('/admin/whoami', { maxRedirects: 0 });

    expect([301, 302, 303, 401, 403, 404]).toContain(res.status());
  });
});
