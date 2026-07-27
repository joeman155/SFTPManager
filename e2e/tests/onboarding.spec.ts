import { test, expect, type APIRequestContext } from '@playwright/test';
import { mutatingSpec, signupNewUser } from './helpers';

/**
 * Onboarding journeys — each scenario uses its own fresh user because
 * onboarding is a one-shot process per account.
 *
 * WRITES TO THE DATABASE — localhost only (helpers.mutatingSpec).
 *
 * The trial-abuse guard (signup.trial-ip-limit) can hide trial plans and
 * refuse onboarding after several runs from one IP; scenarios detect that
 * and skip rather than fail.
 */
test.describe('Onboarding', () => {
  mutatingSpec();

  /** Returns days between today and an ISO date string. */
  function daysFromNow(iso: string): number {
    return Math.round((new Date(iso).getTime() - Date.now()) / 86_400_000);
  }

  async function getPlans(ctx: APIRequestContext) {
    const res = await ctx.get('/portal/api/onboarding');
    expect(res.status()).toBe(200);
    return res.json();
  }

  test('trial plan onboarding starts the trial clock', async ({ playwright }) => {
    const user = await signupNewUser(playwright, 'onb-trial');
    try {
      const data = await getPlans(user.ctx);
      const trial = data.plans.find((p: any) => p.trialDays && p.trialDays > 0);
      test.skip(!trial, 'no trial plan offered (trial-abuse guard active or none configured)');

      const res = await user.ctx.post('/portal/api/onboarding', {
        data: { planId: trial.id, phone: '+61 400 000 001' },
      });
      expect(res.status()).toBe(200);
      expect((await res.json()).success).toBe(true);

      // Trial expiry visible on /me, matching the plan's trialDays
      const me = await (await user.ctx.get('/portal/api/me')).json();
      expect(me.trialExpires).toBeTruthy();
      expect(daysFromNow(me.trialExpires)).toBeGreaterThanOrEqual(trial.trialDays - 1);
      expect(daysFromNow(me.trialExpires)).toBeLessThanOrEqual(trial.trialDays + 1);

      // Onboarding is one-shot: the data endpoint now reports done
      expect((await getPlans(user.ctx)).onboarded).toBe(true);
    } finally {
      await user.ctx.dispose();
    }
  });

  test('paid plan without a card grants a 7-day grace period', async ({ playwright }) => {
    const user = await signupNewUser(playwright, 'onb-grace');
    try {
      const data = await getPlans(user.ctx);
      const paid = data.plans
        .filter((p: any) => (!p.trialDays || p.trialDays <= 0) && (p.monthlyPriceCents ?? 0) > 0)
        .sort((a: any, b: any) => a.monthlyPriceCents - b.monthlyPriceCents)[0];
      test.skip(!paid, 'no paid plan configured');

      const res = await user.ctx.post('/portal/api/onboarding', { data: { planId: paid.id } });
      if (res.status() === 400) {
        test.skip(true, 'trial-abuse guard refused the grace period for this IP');
      }
      expect(res.status()).toBe(200);

      const me = await (await user.ctx.get('/portal/api/me')).json();
      expect(daysFromNow(me.trialExpires)).toBeGreaterThanOrEqual(6);
      expect(daysFromNow(me.trialExpires)).toBeLessThanOrEqual(8);
    } finally {
      await user.ctx.dispose();
    }
  });

  test('paid plan with a saved card charges the first month immediately', async ({ playwright }) => {
    const user = await signupNewUser(playwright, 'onb-paid');
    try {
      // Only possible in mock billing mode (no Stripe keys) — the mock-save
      // endpoint simulates the browser-side card tokenization.
      const config = await (await user.ctx.get('/portal/api/billing/config')).json();
      test.skip(config.mode !== 'mock', `billing mode is ${config.mode}, not mock`);

      const saved = await user.ctx.post('/portal/api/billing/mock-save', {
        data: { cardNumber: '4242 4242 4242 4242', expiry: '12/30', cvc: '123', slot: 'PRIMARY' },
      });
      expect(saved.status()).toBe(200);

      const data = await getPlans(user.ctx);
      const paid = data.plans
        .filter((p: any) => (!p.trialDays || p.trialDays <= 0) && (p.monthlyPriceCents ?? 0) > 0)
        .sort((a: any, b: any) => a.monthlyPriceCents - b.monthlyPriceCents)[0];
      test.skip(!paid, 'no paid plan configured');

      const res = await user.ctx.post('/portal/api/onboarding', { data: { planId: paid.id } });
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.success).toBe(true);
      expect(body.paymentWarning).toBeUndefined(); // 4242 card must charge cleanly

      // Paid up: no trial, paid one month ahead
      const plans = await (await user.ctx.get('/portal/api/plans')).json();
      expect(plans.paidUp).toBe(true);
      expect(daysFromNow(plans.paidToDate)).toBeGreaterThanOrEqual(27);

      const me = await (await user.ctx.get('/portal/api/me')).json();
      expect(me.trialExpires).toBe('');
    } finally {
      await user.ctx.dispose();
    }
  });

  test('declined card at onboarding falls back to a payment warning and grace', async ({ playwright }) => {
    const user = await signupNewUser(playwright, 'onb-decline');
    try {
      const config = await (await user.ctx.get('/portal/api/billing/config')).json();
      test.skip(config.mode !== 'mock', `billing mode is ${config.mode}, not mock`);

      // 4000...0002 saves fine but every charge is declined (mirrors Stripe)
      const saved = await user.ctx.post('/portal/api/billing/mock-save', {
        data: { cardNumber: '4000 0000 0000 0002', expiry: '12/30', cvc: '123', slot: 'PRIMARY' },
      });
      expect(saved.status()).toBe(200);

      const data = await getPlans(user.ctx);
      const paid = data.plans
        .filter((p: any) => (!p.trialDays || p.trialDays <= 0) && (p.monthlyPriceCents ?? 0) > 0)[0];
      test.skip(!paid, 'no paid plan configured');

      const res = await user.ctx.post('/portal/api/onboarding', { data: { planId: paid.id } });
      if (res.status() === 400) {
        test.skip(true, 'trial-abuse guard refused the grace period for this IP');
      }
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.paymentWarning).toContain('could not be charged');

      // 7-day grace so the user can fix their card
      const me = await (await user.ctx.get('/portal/api/me')).json();
      expect(daysFromNow(me.trialExpires)).toBeGreaterThanOrEqual(6);
      expect(daysFromNow(me.trialExpires)).toBeLessThanOrEqual(8);
    } finally {
      await user.ctx.dispose();
    }
  });

  test('invalid mock cards are rejected at save time', async ({ playwright }) => {
    const user = await signupNewUser(playwright, 'onb-badcard');
    try {
      const config = await (await user.ctx.get('/portal/api/billing/config')).json();
      test.skip(config.mode !== 'mock', `billing mode is ${config.mode}, not mock`);

      const cases = [
        { cardNumber: '4242 4242 4242 4241', expiry: '12/30', cvc: '123', reason: 'Invalid card number' }, // Luhn fail
        { cardNumber: '4242 4242 4242 4242', expiry: '01/20', cvc: '123', reason: 'expired' },
        { cardNumber: '4242 4242 4242 4242', expiry: '12/30', cvc: '000', reason: 'security code' },
        { cardNumber: '4000 0000 0000 0101', expiry: '12/30', cvc: '123', reason: 'security code' },
      ];
      for (const c of cases) {
        const res = await user.ctx.post('/portal/api/billing/mock-save', {
          data: { ...c, slot: 'PRIMARY' },
        });
        expect(res.status(), `${c.cardNumber} / ${c.expiry} / ${c.cvc}`).toBe(400);
        expect((await res.json()).error.toLowerCase()).toContain(c.reason.toLowerCase());
      }

      // Nothing stuck to the account
      const cards = await (await user.ctx.get('/portal/api/billing/cards')).json();
      expect(cards.primary).toBeNull();
    } finally {
      await user.ctx.dispose();
    }
  });
});
