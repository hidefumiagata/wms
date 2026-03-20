# テスト仕様書 — システムパラメータ管理（TST-SYS）

| 項目 | 記載内容 |
|------|---------|
| テスト仕様書ID | TST-SYS |
| テスト対象機能 | システムパラメータ一覧表示・値変更 |
| 対象設計書 | SCR-12（SYS-001）、API-11（API-SYS-002、API-SYS-003） |
| 作成者 | Claude Code |
| 作成日 | 2026-03-20 |
| レビュー者 | |
| レビュー日 | |

---

## テストシナリオ一覧

| シナリオID | シナリオ名 | 優先度 | 前提条件 | 結合 | E2E |
|-----------|----------|:------:|---------|:----:|:---:|
| SC-001 | 正常系: パラメータ一覧表示 | 高 | SYSTEM_ADMINでログイン済み | ○ | ○ |
| SC-002 | 正常系: INTEGER型パラメータ値変更 | 高 | SYSTEM_ADMINでログイン済み | ○ | ○ |
| SC-003 | 正常系: STRING型パラメータ値変更 | 高 | SYSTEM_ADMINでログイン済み | ○ | — |
| SC-004 | 正常系: BOOLEAN型パラメータ値変更 | 中 | SYSTEM_ADMINでログイン済み | ○ | — |
| SC-005 | 異常系: WAREHOUSE_MANAGERでアクセス（403） | 高 | WAREHOUSE_MANAGERでログイン済み | ○ | ○ |
| SC-006 | 異常系: WAREHOUSE_STAFFでアクセス（403） | 中 | WAREHOUSE_STAFFでログイン済み | ○ | — |
| SC-007 | 異常系: VIEWERでアクセス（403） | 中 | VIEWERでログイン済み | ○ | — |
| SC-008 | 異常系: 未認証でアクセス（401） | 高 | 未ログイン | ○ | — |
| SC-009 | 異常系: INTEGER型に不正な値を入力 | 高 | SYSTEM_ADMINでログイン済み | ○ | ○ |
| SC-010 | 異常系: STRING型に空文字を入力 | 中 | SYSTEM_ADMINでログイン済み | ○ | — |
| SC-011 | 異常系: 存在しないparamKeyで更新（404） | 中 | SYSTEM_ADMINでログイン済み | ○ | — |

---

## テストシナリオ詳細

---

### SC-001: 正常系: パラメータ一覧表示

| 項目 | 内容 |
|------|------|
| シナリオID | SC-001 |
| シナリオ名 | 正常系: SYSTEM_ADMINでパラメータ一覧が正しく表示される |
| 前提条件 | SYSTEM_ADMINでログイン済み。system_parametersテーブルに複数カテゴリのパラメータが登録済み |
| テストデータ | system_parametersテーブルの初期データ（INVENTORY, OUTBOUND, SYSTEM等のカテゴリ） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | サイドバーから [システム管理] > [システムパラメータ] をクリック | システムパラメータ設定画面（`/system/parameters`）に遷移する | URL `/system/parameters` |
| 2 | 画面のパンくずリストを確認 | 「ホーム > システム管理 > システムパラメータ設定」が表示される | — |
| 3 | カテゴリ別グルーピングを確認 | パラメータがカテゴリ（INVENTORY, OUTBOUND等）でグルーピングされて表示される | カテゴリ名がセクションヘッダーとして表示 |
| 4 | テーブルの列構成を確認 | パラメータ名、現在値、デフォルト値、説明、最終更新日時、操作（保存ボタン）が表示される | — |
| 5 | INVENTORYカテゴリのパラメータを確認 | `paramValue`、`defaultValue`、`description` が正しく表示される | DB値と一致 |
| 6 | 各行の [保存] ボタンを確認 | 値が変更されていないため、全行の [保存] ボタンが disabled 状態である | ボタンが非活性 |
| 7 | カテゴリヘッダーをクリック | カテゴリセクションが折りたたまれる | — |
| 8 | 再度カテゴリヘッダーをクリック | カテゴリセクションが展開される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | 全レコードの件数と画面表示件数が一致すること |
| 2 | system_parameters | `category` + `display_order` の順でソートされていること（APIレスポンスの順序確認） |

---

### SC-002: 正常系: INTEGER型パラメータ値変更

| 項目 | 内容 |
|------|------|
| シナリオID | SC-002 |
| シナリオ名 | 正常系: INTEGER型パラメータの値を変更し、保存が成功する |
| 前提条件 | SYSTEM_ADMINでログイン済み。INVENTORYカテゴリにINTEGER型パラメータが存在 |
| テストデータ | paramKey: `LOCATION_CAPACITY_CASE`、現在値: `1`、変更後: `10` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | システムパラメータ設定画面（`/system/parameters`）を開く | パラメータ一覧が表示される | — |
| 2 | `LOCATION_CAPACITY_CASE` の現在値フィールドに「10」を入力 | 数値入力フィールドに「10」が反映される。該当行の [保存] ボタンが活性化する | ボタンが active 状態に変化 |
| 3 | [保存] ボタンをクリック | 確認ダイアログ `MSG-W-SYS001-001`「パラメータ「ロケーション収容上限（ケース）」の値を「10」に変更します。よろしいですか？」が表示される | ダイアログ表示 |
| 4 | 確認ダイアログで [OK] をクリック | `MSG-S-SYS001-001`「パラメータを更新しました。」がトースト表示される | 成功メッセージ表示 |
| 5 | 該当行の現在値を確認 | 「10」に更新されていること | — |
| 6 | 該当行の最終更新日時を確認 | 現在日時付近に更新されていること | — |
| 7 | [保存] ボタンを確認 | 値が変更されていないため、再び disabled 状態に戻っていること | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | `param_key = 'LOCATION_CAPACITY_CASE'` のレコードで `param_value = '10'` に更新されていること |
| 2 | system_parameters | `updated_at` が現在日時付近であること |
| 3 | system_parameters | `updated_by` がログイン中のSYSTEM_ADMINのユーザーIDであること |

---

### SC-003: 正常系: STRING型パラメータ値変更

| 項目 | 内容 |
|------|------|
| シナリオID | SC-003 |
| シナリオ名 | 正常系: STRING型パラメータの値を変更し、保存が成功する |
| 前提条件 | SYSTEM_ADMINでログイン済み。STRING型パラメータが存在 |
| テストデータ | STRING型パラメータ、変更後の値: `updated-string-value` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | システムパラメータ設定画面を開く | パラメータ一覧が表示される | — |
| 2 | STRING型パラメータの現在値フィールドに「updated-string-value」を入力 | テキスト入力フィールドに値が反映される。[保存] ボタンが活性化 | — |
| 3 | [保存] ボタンをクリック | 確認ダイアログが表示される | — |
| 4 | [OK] をクリック | 成功メッセージが表示される | `MSG-S-SYS001-001` |
| 5 | 該当行の現在値を確認 | 「updated-string-value」に更新されていること | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | 該当パラメータの `param_value = 'updated-string-value'` に更新されていること |
| 2 | system_parameters | `updated_at` と `updated_by` が更新されていること |

---

### SC-004: 正常系: BOOLEAN型パラメータ値変更

| 項目 | 内容 |
|------|------|
| シナリオID | SC-004 |
| シナリオ名 | 正常系: BOOLEAN型パラメータの値をトグルスイッチで変更し、保存が成功する |
| 前提条件 | SYSTEM_ADMINでログイン済み。BOOLEAN型パラメータが存在（現在値: `true`） |
| テストデータ | BOOLEAN型パラメータ、変更前: `true`、変更後: `false` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | システムパラメータ設定画面を開く | パラメータ一覧が表示される | — |
| 2 | BOOLEAN型パラメータのトグルスイッチをクリック | トグルがOFF状態に変化。[保存] ボタンが活性化 | トグルスイッチのUI状態変化 |
| 3 | [保存] ボタンをクリック | 確認ダイアログが表示される | — |
| 4 | [OK] をクリック | 成功メッセージが表示される | `MSG-S-SYS001-001` |
| 5 | 該当行のトグルスイッチを確認 | OFF状態で表示されていること | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | 該当パラメータの `param_value = 'false'` に更新されていること |
| 2 | system_parameters | `updated_at` と `updated_by` が更新されていること |

---

### SC-005: 異常系: WAREHOUSE_MANAGERでアクセス（403）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-005 |
| シナリオ名 | 異常系: WAREHOUSE_MANAGERでシステムパラメータ画面にアクセスし、403エラーとなる |
| 前提条件 | WAREHOUSE_MANAGERでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | サイドバーを確認 | [システム管理] > [システムパラメータ] のメニューが表示されないこと | メニュー不在 |
| 2 | URL直接入力で `/system/parameters` にアクセス | アクセス拒否画面またはメインメニューへリダイレクトされる | 403 FORBIDDEN |
| 3 | API直接呼び出し: `GET /api/v1/system/parameters` | 403 Forbidden が返される | HTTPレスポンスステータス |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | レコードに一切変更がないこと |

---

### SC-006: 異常系: WAREHOUSE_STAFFでアクセス（403）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-006 |
| シナリオ名 | 異常系: WAREHOUSE_STAFFでシステムパラメータAPIにアクセスし、403エラーとなる |
| 前提条件 | WAREHOUSE_STAFFでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API直接呼び出し: `GET /api/v1/system/parameters` | 403 Forbidden が返される | HTTPレスポンスステータス |
| 2 | API直接呼び出し: `PUT /api/v1/system/parameters/LOCATION_CAPACITY_CASE` (body: `{"paramValue":"99"}`) | 403 Forbidden が返される | HTTPレスポンスステータス |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | レコードに一切変更がないこと |

---

### SC-007: 異常系: VIEWERでアクセス（403）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-007 |
| シナリオ名 | 異常系: VIEWERでシステムパラメータAPIにアクセスし、403エラーとなる |
| 前提条件 | VIEWERでログイン済み |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API直接呼び出し: `GET /api/v1/system/parameters` | 403 Forbidden が返される | HTTPレスポンスステータス |
| 2 | API直接呼び出し: `PUT /api/v1/system/parameters/LOCATION_CAPACITY_CASE` (body: `{"paramValue":"99"}`) | 403 Forbidden が返される | HTTPレスポンスステータス |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | レコードに一切変更がないこと |

---

### SC-008: 異常系: 未認証でアクセス（401）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-008 |
| シナリオ名 | 異常系: 未認証状態でシステムパラメータAPIにアクセスし、401エラーとなる |
| 前提条件 | 未ログイン（access_token Cookieなし） |
| テストデータ | — |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API直接呼び出し（Cookieなし）: `GET /api/v1/system/parameters` | 401 Unauthorized が返される | HTTPレスポンスステータス、エラーコード `UNAUTHORIZED` |
| 2 | API直接呼び出し（Cookieなし）: `PUT /api/v1/system/parameters/LOCATION_CAPACITY_CASE` | 401 Unauthorized が返される | HTTPレスポンスステータス |
| 3 | ブラウザで `/system/parameters` に直接アクセス | ログイン画面（`/login`）にリダイレクトされる | URL `/login` |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | レコードに一切変更がないこと |

---

### SC-009: 異常系: INTEGER型に不正な値を入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-009 |
| シナリオ名 | 異常系: INTEGER型パラメータに不正な値を入力し、バリデーションエラーとなる |
| 前提条件 | SYSTEM_ADMINでログイン済み |
| テストデータ | paramKey: `LOCATION_CAPACITY_CASE`（valueType=INTEGER） |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | システムパラメータ設定画面を開く | — | — |
| 2 | INTEGER型パラメータの現在値に「abc」を入力し [保存] をクリック | フロントエンドバリデーションで `MSG-E-SYS001-001`「値が不正です。INTEGERに応じた値を入力してください。」が表示される | フォームバリデーションエラー |
| 3 | 現在値に「-5」を入力し [保存] をクリック | バリデーションエラーが表示される（負の値は不可） | — |
| 4 | 現在値に「3.14」を入力し [保存] をクリック | バリデーションエラーが表示される（小数は不可） | — |
| 5 | 現在値に空文字を入力し [保存] をクリック | バリデーションエラーが表示される | — |
| 6 | フロントエンドバリデーションをバイパスし、API直接呼び出しで不正値を送信: `PUT /api/v1/system/parameters/LOCATION_CAPACITY_CASE` (body: `{"paramValue":"abc"}`) | 400 Bad Request `VALIDATION_ERROR` が返される | HTTPレスポンス検証 |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | `param_value` が変更されていないこと |

---

### SC-010: 異常系: STRING型に空文字を入力

| 項目 | 内容 |
|------|------|
| シナリオID | SC-010 |
| シナリオ名 | 異常系: STRING型パラメータに空文字を入力し、バリデーションエラーとなる |
| 前提条件 | SYSTEM_ADMINでログイン済み。STRING型パラメータが存在 |
| テストデータ | STRING型パラメータ |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | STRING型パラメータの現在値を空にして [保存] をクリック | フロントエンドバリデーションで「値が不正です。STRINGに応じた値を入力してください。」が表示される | — |
| 2 | 501文字の文字列を入力して [保存] をクリック | バリデーションエラーが表示される（最大500文字超過） | — |
| 3 | API直接呼び出しで空文字を送信 | 400 Bad Request `VALIDATION_ERROR` が返される | — |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | `param_value` が変更されていないこと |

---

### SC-011: 異常系: 存在しないparamKeyで更新（404）

| 項目 | 内容 |
|------|------|
| シナリオID | SC-011 |
| シナリオ名 | 異常系: 存在しないparamKeyでパラメータ更新APIを呼び出し、404エラーとなる |
| 前提条件 | SYSTEM_ADMINでログイン済み |
| テストデータ | paramKey: `NON_EXISTENT_PARAM` |

**テストステップ:**

| # | 操作 | 期待結果 | 確認方法 |
|:-:|------|---------|---------|
| 1 | API直接呼び出し: `PUT /api/v1/system/parameters/NON_EXISTENT_PARAM` (body: `{"paramValue":"100"}`) | 404 Not Found `PARAM_NOT_FOUND` が返される | HTTPレスポンスステータス |

**DB検証（結合テストのみ）:**

| # | 検証対象テーブル | 検証内容 |
|:-:|---------------|---------|
| 1 | system_parameters | レコードに一切変更がないこと |

---

## Playwrightコード例

### SC-002: INTEGER型パラメータ値変更

```typescript
import { test, expect } from '@playwright/test';
import { loginAs } from '../helpers/auth';

test('SC-002: INTEGER型パラメータの値を変更し、保存が成功する', async ({ page }) => {
  // 前提: SYSTEM_ADMINでログイン
  await loginAs(page, 'SYSTEM_ADMIN');

  // Step 1: システムパラメータ画面を開く
  await page.goto('/system/parameters');
  await expect(page.locator('h1')).toContainText('システムパラメータ設定');

  // Step 2: LOCATION_CAPACITY_CASEの値を変更
  const row = page.locator('[data-testid="param-row-LOCATION_CAPACITY_CASE"]');
  const input = row.locator('[data-testid="param-value-input"]');
  await input.clear();
  await input.fill('10');

  // 保存ボタンが活性化していることを確認
  const saveBtn = row.locator('[data-testid="param-save-btn"]');
  await expect(saveBtn).toBeEnabled();

  // Step 3: 保存ボタンをクリック
  await saveBtn.click();

  // Step 3: 確認ダイアログが表示される
  await expect(page.locator('.el-message-box')).toContainText('変更します');

  // Step 4: OKをクリック
  await page.click('.el-message-box__btns .el-button--primary');

  // Step 4: 成功メッセージが表示される
  await expect(page.locator('.el-message--success')).toContainText('パラメータを更新しました');

  // Step 5: 値が更新されていることを確認
  await expect(input).toHaveValue('10');

  // Step 7: 保存ボタンがdisabledに戻っていることを確認
  await expect(saveBtn).toBeDisabled();
});
```

### SC-005: WAREHOUSE_MANAGERでアクセス（403）

```typescript
import { test, expect } from '@playwright/test';
import { loginAs } from '../helpers/auth';

test('SC-005: WAREHOUSE_MANAGERでシステムパラメータ画面にアクセスできない', async ({ page }) => {
  // 前提: WAREHOUSE_MANAGERでログイン
  await loginAs(page, 'WAREHOUSE_MANAGER');

  // Step 1: サイドバーにシステムパラメータメニューがないことを確認
  await expect(page.locator('[data-testid="menu-system-parameters"]')).not.toBeVisible();

  // Step 2: URL直接入力でアクセスを試行
  await page.goto('/system/parameters');

  // アクセス拒否またはリダイレクト
  await expect(page).not.toHaveURL('/system/parameters');
});
```

---
