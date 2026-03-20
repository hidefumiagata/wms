# テスト仕様書テンプレート

## 概要

本テンプレートは結合テスト・E2Eテストのシナリオテスト仕様書に使用する。
単体テストはコードベースで管理するためテスト仕様書を作成しない。

---

## テスト仕様書の構成

### ヘッダー情報

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-{モジュール}-{連番}（例: TST-INB-001） |
| テスト対象機能 | 機能名（例: 入荷予定登録） |
| 対象設計書 | 参照する機能設計書のID（例: SCR-07 INB-002, API-INB-002） |
| 作成者 | |
| 作成日 | |
| レビュー者 | |
| レビュー日 | |

### テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-001 | 正常系: 新規登録 | 高 | ログイン済み、WAREHOUSE_MANAGER | ○ | ○ |
| SC-002 | 異常系: 必須未入力 | 高 | 同上 | ○ | ○ |

### テストシナリオ詳細

#### SC-001: 正常系: 新規登録

| 項目 | 内容 |
|------|------|
| シナリオID | SC-001 |
| シナリオ名 | 正常系: 入荷予定の新規登録が成功する |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み。商品マスタ・取引先マスタにテストデータ登録済み |
| テストデータ | R__001_master_data.sql の商品コード PRD-001、取引先コード SUP-0001 |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | INB-001（入荷予定一覧）画面を開く | 一覧画面が表示される | URL `/inbound/slips` に遷移 |
| 2 | [新規登録] ボタンをクリック | INB-002（入荷予定登録）画面に遷移 | URL `/inbound/slips/new` |
| 3 | 仕入先コード「SUP-0001」を入力 | 仕入先名が自動表示される | 仕入先名フィールドに値が表示 |
| 4 | 入荷予定日を入力 | 日付ピッカーで選択可能 | — |
| 5 | 明細行に商品コード「PRD-001」、荷姿「ケース」、数量「100」を入力 | 商品名が自動表示される | — |
| 6 | [登録] ボタンをクリック | 確認ダイアログが表示される | MSG-W-INB002-001 |
| 7 | 確認ダイアログで [OK] をクリック | 成功メッセージが表示され、INB-001に遷移 | MSG-S-INB002-001 |
| 8 | INB-001で登録した伝票が一覧に表示される | 伝票番号がINB-YYYYMMDD-0001形式 | ステータス「入荷予定」 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | inbound_slips | レコードが1件追加されていること。status='PLANNED' |
| 2 | inbound_slip_lines | 明細が1件追加されていること。planned_qty=100, unit_type='CASE' |

---

### Playwrightコード例

```typescript
test('SC-001: 入荷予定の新規登録が成功する', async ({ page }) => {
  // 前提: ログイン済み
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Step 1: 入荷予定一覧を開く
  await page.goto('/inbound/slips');
  await expect(page.locator('h1')).toContainText('入荷予定一覧');

  // Step 2: 新規登録ボタンクリック
  await page.click('button:has-text("新規登録")');
  await expect(page).toHaveURL(/\/inbound\/slips\/new/);

  // Step 3-5: フォーム入力
  await page.fill('[data-testid="partner-code"]', 'SUP-0001');
  await page.fill('[data-testid="planned-date"]', '2026-04-01');
  await page.fill('[data-testid="product-code-0"]', 'PRD-001');
  await page.selectOption('[data-testid="unit-type-0"]', 'CASE');
  await page.fill('[data-testid="quantity-0"]', '100');

  // Step 6-7: 登録
  await page.click('button:has-text("登録")');
  await page.click('button:has-text("OK")'); // 確認ダイアログ
  await expect(page.locator('.el-message--success')).toBeVisible();

  // Step 8: 一覧で確認
  await expect(page).toHaveURL(/\/inbound\/slips/);
  await expect(page.locator('table')).toContainText('INB-');
});
```

---

## 付録: テストIDの採番体系

| プレフィックス | 対象 |
|-------------|------|
| TST-AUTH | 認証テスト |
| TST-MST | マスタ管理テスト |
| TST-INB | 入荷管理テスト |
| TST-INV | 在庫管理テスト |
| TST-OUT | 出荷管理テスト |
| TST-ALL | 引当管理テスト |
| TST-RTN | 返品管理テスト |
| TST-BAT | バッチテスト |
| TST-SYS | システムパラメータテスト |
| TST-IF | I/Fテスト |
| TST-RPT | レポートテスト |
