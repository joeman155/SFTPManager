import { defineConfig, devices } from '@playwright/test';

/**
 * E2E tests for SFTP Manager.
 *
 * The Spring Boot app must already be running (default http://localhost:8080)
 * with its PostgreSQL database available. Override the target with:
 *   E2E_BASE_URL=https://staging.example.net npx playwright test
 *
 * NOTE: the signup spec creates throwaway users (e2e+<timestamp>@example.com)
 * in whatever database the app is pointed at — run against dev/test, not prod.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Uncomment to also test Firefox/WebKit:
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    // { name: 'webkit',  use: { ...devices['Desktop Safari'] } },
  ],
});
