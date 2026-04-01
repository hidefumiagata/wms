import { test, expect } from '../fixtures/auth.fixture';

test.describe('セッション管理', () => {
  test('SC-020: ページリロード後もセッション維持', async ({ page, loginAs }) => {
    await loginAs('admin001', 'Admin@1234');
    await expect(page.locator('[data-testid="header-user-name"]')).toBeVisible();
    await page.reload();
    await expect(page.locator('[data-testid="header-user-name"]')).toBeVisible();
  });

  test.skip('SC-018: セッションタイムアウト', async () => {
    // Requires backend with very short session timeout + Playwright clock API
  });
});
