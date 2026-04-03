import { test, expect } from '../fixtures/auth.fixture';

test.describe('ログアウト', () => {
  test('SC-019: ログアウト後、ログイン画面に遷移', async ({ page, loginAs }) => {
    await loginAs('admin001', 'Admin@1234');
    await page.locator('.app-header__user-dropdown').click();
    await page.locator('[data-testid="logout-button"]').click();
    // Element Plus confirm dialog
    await page.locator('.el-message-box__btns .el-button--primary').click();
    await expect(page).toHaveURL(/\/login/);
  });
});
