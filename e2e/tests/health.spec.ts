import { test, expect } from '@playwright/test';

test.describe('Health endpoint', () => {
  test('GET /healthz returns ok without authentication', async ({ request }) => {
    const res = await request.get('/healthz');

    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual({ status: 'ok' });
  });
});
