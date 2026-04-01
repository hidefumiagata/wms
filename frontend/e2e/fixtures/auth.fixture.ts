import { test as base, expect } from '@playwright/test';

export const test = base.extend<{
  loginAs: (userCode: string, password: string) => Promise<void>;
}>({
  loginAs: async ({ page }, use) => {
    const login = async (userCode: string, password: string) => {
      await page.goto('/login');
      await page.locator('[data-testid="user-code"] input').fill(userCode);
      await page.locator('[data-testid="password"] input').fill(password);
      await page.locator('[data-testid="login-submit"]').click();
      await page.waitForURL(/^(?!.*\/login)/);
    };
    await use(login);
  },
});

export { expect };
