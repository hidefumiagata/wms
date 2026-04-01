import { test, expect } from '../fixtures/auth.fixture';

test.describe('パスワードリセット', () => {
  test('SC-015: パスワードリセットリクエスト送信', async ({ page }) => {
    await page.goto('/auth/reset-request');
    await page.locator('[data-testid="reset-identifier"] input').fill('reset_user');
    await page.locator('[data-testid="reset-submit"]').click();
    await expect(page.locator('[data-testid="success-message"]')).toBeVisible();
  });

  test('SC-016: 無効なトークンでパスワードリセット確認', async ({ page }) => {
    await page.goto('/auth/reset-password?token=expired-invalid-token');
    await page.locator('[data-testid="reset-new-password"] input').fill('NewPass@1234');
    await page.locator('[data-testid="reset-confirm-password"] input').fill('NewPass@1234');
    await page.locator('[data-testid="reset-confirm-submit"]').click();
    await expect(page.locator('[data-testid="error-banner"]')).toBeVisible();
  });
});
