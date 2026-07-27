import { test, expect } from '@playwright/test';

test.describe('Portal login page', () => {
  test('unauthenticated /portal is redirected to the login page', async ({ page }) => {
    await page.goto('/portal');

    await expect(page).toHaveURL(/\/portal\/login/);
  });

  test('login page renders the sign-in form', async ({ page }) => {
    await page.goto('/portal/login');

    await expect(page).toHaveTitle(/Sign In/i);
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.locator('#signin-btn')).toBeVisible();
  });

  test('name fields are hidden until the email is identified as new', async ({ page }) => {
    await page.goto('/portal/login');

    // firstName/surname only appear for brand-new signups
    await expect(page.locator('#name-fields')).toBeHidden();
  });

  test('logout endpoint lands back on the login page', async ({ page }) => {
    await page.goto('/portal/logout');

    await expect(page).toHaveURL(/\/portal\/login/);
  });
});
