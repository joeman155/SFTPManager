import { test, type APIRequestContext, type PlaywrightWorkerArgs } from '@playwright/test';

type Playwright = PlaywrightWorkerArgs['playwright'];

export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8080';

/**
 * Guard for specs that WRITE to the database (signup, services, onboarding).
 * They only run against localhost unless explicitly overridden with
 * E2E_ALLOW_REMOTE_WRITES=1 — so a casual run against production can't
 * create junk users or services.
 */
export function mutatingSpec() {
  const isLocal = /localhost|127\.0\.0\.1/i.test(BASE_URL);
  test.skip(
    !isLocal && process.env.E2E_ALLOW_REMOTE_WRITES !== '1',
    `mutating spec skipped against ${BASE_URL} — set E2E_ALLOW_REMOTE_WRITES=1 to force`,
  );
}

export interface TestUser {
  ctx: APIRequestContext;
  email: string;
  password: string;
}

/**
 * Creates a brand-new email/password user via the auth API and returns a
 * request context whose cookie jar carries the authenticated session.
 * Caller should ctx.dispose() when done.
 */
export async function signupNewUser(playwright: Playwright, label: string): Promise<TestUser> {
  const email = `e2e+${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const password = 'CorrectHorse1!';

  const ctx = await playwright.request.newContext({ baseURL: BASE_URL });
  const res = await ctx.post('/portal/api/auth/email-signin', {
    data: { email, password, firstName: 'E2E', surname: 'Test' },
  });
  if (res.status() !== 200) {
    throw new Error(`signup failed (${res.status()}): ${await res.text()}`);
  }
  return { ctx, email, password };
}
