import { test, expect } from '../fixtures/auth.fixture';

test.describe('ログイン', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('SC-001: SYSTEM_ADMINでログインし、メインメニューに遷移する', async ({ page }) => {
    await page.locator('[data-testid="user-code"] input').fill('admin001');
    await page.locator('[data-testid="password"] input').fill('Admin@1234');
    await page.locator('[data-testid="login-submit"]').click();
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator('[data-testid="header-user-name"]')).toBeVisible();
  });

  test('SC-002: WAREHOUSE_MANAGERでログインする', async ({ page }) => {
    await page.locator('[data-testid="user-code"] input').fill('wh_manager01');
    await page.locator('[data-testid="password"] input').fill('Manager@1234');
    await page.locator('[data-testid="login-submit"]').click();
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator('[data-testid="header-user-name"]')).toBeVisible();
  });

  test('SC-005: パスワード誤りでエラーメッセージ表示', async ({ page }) => {
    await page.locator('[data-testid="user-code"] input').fill('admin001');
    await page.locator('[data-testid="password"] input').fill('WrongPassword');
    await page.locator('[data-testid="login-submit"]').click();
    await expect(page.locator('[data-testid="error-banner"]')).toBeVisible();
  });

  test('SC-006: 存在しないユーザーでエラーメッセージ表示', async ({ page }) => {
    await page.locator('[data-testid="user-code"] input').fill('nonexistent_user');
    await page.locator('[data-testid="password"] input').fill('AnyPassword@1234');
    await page.locator('[data-testid="login-submit"]').click();
    await expect(page.locator('[data-testid="error-banner"]')).toBeVisible();
  });

  test('SC-007: ロック済みアカウントでエラーメッセージ表示', async ({ page }) => {
    await page.locator('[data-testid="user-code"] input').fill('locked_user');
    await page.locator('[data-testid="password"] input').fill('Correct@1234');
    await page.locator('[data-testid="login-submit"]').click();
    await expect(page.locator('[data-testid="error-banner"]')).toBeVisible();
  });

  test('SC-009: 5回連続ログイン失敗でアカウントロック', async ({ page }) => {
    for (let i = 0; i < 5; i++) {
      await page.locator('[data-testid="user-code"] input').clear();
      await page.locator('[data-testid="password"] input').clear();
      await page.locator('[data-testid="user-code"] input').fill('lock_target');
      await page.locator('[data-testid="password"] input').fill('Wrong@1234');
      await page.locator('[data-testid="login-submit"]').click();
      await expect(page.locator('[data-testid="error-banner"]')).toBeVisible();
    }
  });
});
