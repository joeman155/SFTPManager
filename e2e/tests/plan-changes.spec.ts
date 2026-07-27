import { test, expect, type APIRequestContext } from '@playwright/test';
import { mutatingSpec, signupNewUser, type TestUser } from './helpers';

/**
 * Plan-change rules for a PAID-UP customer — requires mock billing mode so
 * the setup can save the 4242 test card and genuinely pay the first month.
 *
 * WRITES TO THE DATABASE — localhost only (helpers.mutatingSpec).
 */
test.describe.serial('Plan changes for a paid-up user', () => {
  mutatingSpec();

  let user: TestUser;
  let ctx: APIRequestContext;
  let cheapPlan: any;
  let expensivePlan: any;
  let trialPlanId: number | null = null;

  test.beforeAll(async ({ playwright }) => {
    user = await signupNewUser(playwright, 'plans');
    ctx = user.ctx;

    // Mock mode required (4242 card); otherwise every test here skips
    const config = await (await ctx.get('/portal/api/billing/config')).json();
    if (config.mode !== 'mock') {
      test.skip(true, `billing mode is ${config.mode}, not mock`);
    }

    // Two differently-priced paid plans are needed for upgrade/downgrade
    const onb = await (await ctx.get('/portal/api/onboarding')).json();
    trialPlanId = onb.plans.find((p: any) => p.trialDays > 0)?.id ?? null;
    const paid = onb.plans
      .filter((p: any) => (!p.trialDays || p.trialDays <= 0) && (p.monthlyPriceCents ?? 0) > 0)
      .sort((a: any, b: any) => a.monthlyPriceCents - b.monthlyPriceCents);
    if (paid.length < 2) {
      test.skip(true, 'need at least two paid plans to test upgrades/downgrades');
    }
    cheapPlan = paid[0];
    expensivePlan = paid[paid.length - 1];

    // Save a good card and onboard onto the cheap plan → first month paid
    const saved = await ctx.post('/portal/api/billing/mock-save', {
      data: { cardNumber: '4242 4242 4242 4242', expiry: '12/30', cvc: '123', slot: 'PRIMARY' },
    });
    if (saved.status() !== 200) throw new Error(`card save failed: ${await saved.text()}`);
    const onboarded = await ctx.post('/portal/api/onboarding', { data: { planId: cheapPlan.id } });
    if (onboarded.status() !== 200) throw new Error(`onboarding failed: ${await onboarded.text()}`);
  });

  test.afterAll(async () => {
    // Retire the throwaway user
    await ctx?.post('/portal/api/account/close').catch(() => {});
    await ctx?.dispose();
  });

  test('setup left the user paid-up on the cheap plan', async () => {
    const plans = await (await ctx.get('/portal/api/plans')).json();

    expect(plans.currentPlanId).toBe(cheapPlan.id);
    expect(plans.paidUp).toBe(true);
  });

  test('plan change requires a planId', async () => {
    const res = await ctx.post('/portal/api/account/plan', { data: {} });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('planId');
  });

  test('nonexistent plan is rejected', async () => {
    const res = await ctx.post('/portal/api/account/plan', { data: { planId: 999999 } });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('Invalid plan');
  });

  test('switching to a trial plan is rejected', async () => {
    test.skip(trialPlanId === null, 'no trial plan configured');
    const res = await ctx.post('/portal/api/account/plan', { data: { planId: trialPlanId } });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('Invalid plan');
  });

  test('reselecting the current plan is rejected', async () => {
    const res = await ctx.post('/portal/api/account/plan', { data: { planId: cheapPlan.id } });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toContain('already on');
  });

  test('upgrade charges the prorated difference and moves the plan', async () => {
    const res = await ctx.post('/portal/api/account/plan', { data: { planId: expensivePlan.id } });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.plan).toBe(expensivePlan.plan);
    expect(body.charged).toBe(true);
    // Prorated: full cycle just paid on the cheap plan is credited, so the
    // amount due is at most the price difference (and never the full price)
    expect(body.amountChargedCents).toBeLessThanOrEqual(
      expensivePlan.monthlyPriceCents - cheapPlan.monthlyPriceCents,
    );

    const plans = await (await ctx.get('/portal/api/plans')).json();
    expect(plans.currentPlanId).toBe(expensivePlan.id);
    expect(plans.paidUp).toBe(true);
  });

  test('mid-cycle downgrade is refused and routed to support', async () => {
    const res = await ctx.post('/portal/api/account/plan', { data: { planId: cheapPlan.id } });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.downgradeNeedsSupport).toBe(true);

    // Plan unchanged
    const plans = await (await ctx.get('/portal/api/plans')).json();
    expect(plans.currentPlanId).toBe(expensivePlan.id);
  });

  test('a downgrade request goes to support instead', async () => {
    const res = await ctx.post('/portal/api/account/plan-request', {
      data: { planId: cheapPlan.id, message: 'E2E: please downgrade me' },
    });

    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);
  });
});
