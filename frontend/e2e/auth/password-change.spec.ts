import { test, expect } from '../fixtures/auth.fixture';

test.describe('パスワード変更', () => {
  test('SC-010: 初回ログイン時のパスワード変更必須', async ({ page }) => {
    await page.goto('/login');
    await page.locator('[data-testid="user-code"] input').fill('new_user');
    await page.locator('[data-testid="password"] input').fill('Initial@1234');
    await page.locator('[data-testid="login-submit"]').click();
    await expect(page).toHaveURL(/\/change-password/);
    await page.locator('[data-testid="current-password"] input').fill('Initial@1234');
    await page.locator('[data-testid="new-password"] input').fill('NewSecure@1234');
    await page.locator('[data-testid="confirm-password"] input').fill('NewSecure@1234');
    await page.locator('[data-testid="change-password-submit"]').click();
    await expect(page).not.toHaveURL(/\/change-password/);
  });

  test('SC-011: パスワード変更後、新パスワードでログイン', async ({ page, loginAs }) => {
    await loginAs('pw_change_user', 'Current@1234');
    await page.goto('/change-password');
    await page.locator('[data-testid="current-password"] input').fill('Current@1234');
    await page.locator('[data-testid="new-password"] input').fill('Changed@1234');
    await page.locator('[data-testid="confirm-password"] input').fill('Changed@1234');
    await page.locator('[data-testid="change-password-submit"]').click();
    await expect(page).not.toHaveURL(/\/change-password/);
  });

  test('SC-012: 現在のパスワード誤りでエラー', async ({ page, loginAs }) => {
    await loginAs('pw_change_user', 'Current@1234');
    await page.goto('/change-password');
    await page.locator('[data-testid="current-password"] input').fill('WrongCurrent@1234');
    await page.locator('[data-testid="new-password"] input').fill('NewPass@1234');
    await page.locator('[data-testid="confirm-password"] input').fill('NewPass@1234');
    await page.locator('[data-testid="change-password-submit"]').click();
    await expect(page.locator('[data-testid="error-banner"]')).toBeVisible();
  });
});
