---
name: dev
description: バックエンド開発タスクを一気通貫で実行する。開発準備→TDD開発→テストカバレッジ100%→レビュー→指摘対応→PR作成まで。
argument-hint: <開発内容の説明 または Issue番号>
---

# 開発タスクスキル

開発対象: $ARGUMENTS

---

## Phase 1: 開発準備

**目的**: 最新コードベースの取得、設計書の理解、タスク計画、Issue・ブランチの作成

### Step 1.1: mainブランチの最新化

```
git checkout main
git pull origin main
```

### Step 1.2: プロジェクトルール読み込み

以下のファイルを**必ず**読み込んでコンテキストに入れる:
1. `CLAUDE.md` — プロジェクト全体のルール
2. `CLAUDE-LESSONS-LEARNED.md` — 過去の学び
3. `docs/ARCHITECTURE-RULES.md` — アーキテクチャルール集

### Step 1.3: 対象設計書の特定と読み込み

`docs/document-map.yaml` で対象モジュールの関連ドキュメントを確認し、読み込む:
- `docs/functional-design/API-*.md` — API設計書（バックエンドの場合）
- `docs/functional-design/SCR-*.md` — 画面設計書（フロントエンドの場合）
- `docs/functional-requirements/` — 機能要件定義書
- `docs/data-model/` — データモデル定義
- `openapi/wms-api.yaml` — OpenAPI定義

既存の類似実装も探して読む（既存パターンの踏襲のため）。

### Step 1.4: タスク計画

設計書を元に、作業タスクを細分化してチェックリスト形式のタスク一覧を作成する。
タスク粒度の目安:
- 1タスク = 1つのクラスまたは1つの機能単位
- テストは本体実装とセットで1タスク

### Step 1.5: GitHub Issue 作成

CLAUDE.md の Issue ルールに従い Issue を作成する。
既にIssueが存在する場合（Issue番号指定時）は、タスク一覧を更新する。

### Step 1.6: ブランチ作成

```
git checkout -b feature/{Issue番号}_{短い説明}
```

---

## Phase 2: バックエンド開発

**目的**: テスト駆動で機能を実装する

### Step 2.1: OpenAPI コード生成

OpenAPI定義を変更した場合、バックエンドのコード生成を実行する:
```
cd backend && ./gradlew openApiGenerate
```

生成先:
- インターフェース: `com.wms.generated.api.{Domain}Api`
- DTOモデル: `com.wms.generated.model.*`

設定は `backend/build.gradle` の `openApiGenerate` タスクで定義済み（`interfaceOnly: true`, `useTags: true`）。

### Step 2.2: テストコード先行

**テスト駆動開発を厳守する。**

#### Serviceテスト（`@ExtendWith(MockitoExtension.class)`）
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("XxxService")
class XxxServiceTest {
    @Mock private XxxRepository xxxRepository;
    @InjectMocks private XxxService xxxService;

    // テスト群を @Nested でメソッド単位にグループ化
    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("正常系の説明")
        void create_validInput_returnsCreated() { ... }
    }
}
```

**テスト必須パターン:**
- 正常系テスト
- 異常系テスト（バリデーションエラー、Not Found、ステータス不正等）
- 境界値テスト
- テストメソッド名: `{method}_{condition}_{expectedResult}` の3部構成
- 全テストに `@DisplayName` を付与
- Entity の id 設定には `setField` リフレクションユーティリティを使用:
```java
private static void setField(Object obj, String fieldName, Object value) {
    Class<?> clazz = obj.getClass();
    while (clazz != null) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
            return;
        } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    throw new RuntimeException("Field not found: " + fieldName);
}
```

#### Controllerテスト（`@WebMvcTest`）
```java
@WebMvcTest(XxxController.class)
@AutoConfigureMockMvc(addFilters = false)
class XxxControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private XxxService xxxService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
}
```

**テスト対象**: 200/201正常系、400バリデーション、404 Not Found、409 Conflict、422業務ルール違反

### Step 2.3: 実装

テストを通すための実装を行う:
1. **Entity** — JPA エンティティ（Flyway migration も作成: `V{次の番号}__{説明}.sql`）
2. **Repository** — Spring Data JPA リポジトリ
3. **Service** — ビジネスロジック（`@Transactional` 付与）
4. **Controller** — `{Domain}Api` インターフェースを `implements`

**実装ルール**（`docs/ARCHITECTURE-RULES.md` の RULE-* 参照）:
- 手書きDTOは禁止、OpenAPI生成モデルのみ使用
- Entity → DTO 変換は Controller 内の `private` メソッド（`toDetail()`, `toListItem()` 等）
- 関連Entity情報の合成はバッチフェッチ + Map合成で N+1 を回避
- 例外は `shared.exception` パッケージのカスタム例外を使用
- エラーコードは `{RESOURCE}_{ERROR_TYPE}` 形式のリテラル

**DB migration（Flyway）**:
- テーブル追加・変更時は `backend/src/main/resources/db/migration/V{n}__{description}.sql` を作成
- 既存の最大バージョン番号を確認してインクリメント
- 命名: スネークケース（例: `V20__add_column_to_xxx.sql`）

### Step 2.4: タスク進捗更新

1タスク完了ごとに Issue のチェックリストにチェックを付ける。

---

## Phase 2F: フロントエンド開発（該当する場合）

**目的**: Vue 3 + Element Plus で画面を実装する

### Step 2F.1: OpenAPI TypeScript 生成

OpenAPI定義を変更した場合:
```
cd frontend && npm run generate:api
```

生成先: `frontend/src/api/generated/`（models/ + api/）

### Step 2F.2: Composable 作成

画面ロジックは1画面1Composableに閉じ込める（RULE-FE-003）:
- 一覧画面: `src/composables/{module}/use{Resource}List.ts`
- 登録・編集画面: `src/composables/{module}/use{Resource}Form.ts`

**実装パターン**:
```typescript
export function use{Resource}List() {
  const items = ref<XxxItem[]>([])
  const loading = ref(false)
  const searchForm = reactive({ ... })
  let abortController: AbortController | null = null

  onUnmounted(() => { abortController?.abort() })

  async function fetchList() {
    abortController?.abort()
    abortController = new AbortController()
    loading.value = true
    try {
      const res = await apiClient.get<PageResponse<XxxItem>>('/path', {
        params: { ... }, signal: abortController.signal
      })
      items.value = res.data.content
    } catch (err) {
      if (axios.isCancel(err)) return
      ElMessage.error(t('xxx.fetchError'))
    } finally {
      loading.value = false
    }
  }
  return { items, loading, searchForm, fetchList, ... }
}
```

**フォームバリデーション**: vee-validate + Zod
```typescript
const validationSchema = computed(() =>
  toTypedSchema(z.object({
    code: z.string().min(1).max(20).regex(/^[A-Za-z0-9-]+$/),
    name: z.string().min(1).max(200),
  }))
)
const { errors, handleSubmit, setFieldError, defineField } = useForm({ validationSchema })
```

### Step 2F.3: Vue コンポーネント

`.vue` ファイルは表示とイベントバインディングのみ。ロジックはComposableに委譲（RULE-FE-005）。

---

## Phase 3: テストカバレッジ

**目的**: カバレッジ目標を達成する

### カバレッジ目標

| 対象 | C0（Stmts/LINE） | C1（Branch） | 計測コマンド |
|------|-----------------|-------------|-------------|
| バックエンド | 100% | 100% | `cd backend && ./gradlew test jacocoTestReport` |
| フロントエンド | 100% | 95% | `cd frontend && npm run test:coverage` |

### Step 3.1: バックエンド カバレッジ

JaCoCo XML レポートを Python で解析する:
```bash
python3 << 'PYEOF'
import xml.etree.ElementTree as ET
tree = ET.parse('backend/build/reports/jacoco/test/jacocoTestReport.xml')
root = tree.getroot()
for pkg in root.findall('.//package'):
    for cls in pkg.findall('class'):
        name = cls.get('name').split('/')[-1]
        if name in ['対象クラス名']:
            for method in cls.findall('method'):
                for c in method.findall('counter'):
                    if c.get('type') == 'BRANCH' and int(c.get('missed')) > 0:
                        print(f"  {method.get('name')}: BRANCH miss={c.get('missed')}")
PYEOF
```

**BE許容される100%未達理由**:
- `&&` 演算子の短絡評価によるJaCoCo制約
- 到達不可能な防御コード（SHA-256 NoSuchAlgorithmException 等）
- フレームワーク起因（Spring Boot main()、@PreAuthorize到達不可分岐等）

### Step 3.2: フロントエンド カバレッジ

`npm run test:coverage` のテキスト出力で Stmts / Branch を確認する。
対象: `src/composables/`, `src/stores/`, `src/utils/`（vitest.config.ts で設定済み）

**FE典型的な未カバーパターンと対処法**:
- エラーハンドリング分岐（`!error.response`, `status === 409` 等）→ apiClientモックでステータスコードを変える
- `axios.isCancel(err)` → `vi.mocked(axios.isCancel).mockReturnValue(true)`
- `ElMessageBox.confirm` のキャンセル → `vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')`

**FE許容される95%未達理由**:
- optional chaining (`?.`) がv8 coverageでブランチカウントされる
- AbortController キャンセルタイミング依存の分岐
- DOM操作依存（Blob URL生成等）

### Step 3.3: テスト追加・達成確認

未カバー箇所にテストを追加し、目標達成まで繰り返す。

---

## Phase 4: PR 作成

**目的**: mainブランチへのPRを作成する

### Step 4.1: コミット・プッシュ

コミットメッセージ形式:
```
feat: 日本語の変更概要 / English summary

詳細説明（日本語）

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

プレフィックス: `feat:` / `fix:` / `test:` / `docs:` / `chore:`

**注意**: pre-commit hookでCheckstyleが、pre-push hookでSpotBugsが自動実行される。

### Step 4.2: PR 作成

PR 本文に `Closes #{Issue番号}` を含めてIssueと紐付ける:

```markdown
Closes #{Issue番号}

## Summary
- 変更内容の箇条書き

## Test coverage

### Backend (JaCoCo)
| 指標 | 値 |
|------|-----|
| C0（LINE） | XX% |
| C1（BRANCH） | XX% |

### Frontend (v8) ※FE変更がある場合
| 指標 | 値 |
|------|-----|
| Stmts | XX% |
| Branch | XX% |

## Test plan
- [x] テスト内容の箇条書き

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

100% でない場合はその理由も記載する。

---

## Phase 5: レビュー

**目的**: 3つの観点で並列レビューし、結果をPRコメントに投稿する

3つのサブエージェントを**並列で**（バックグラウンドで）起動する:

### Agent 1: 専門家レビュー
対象領域の技術的品質をレビューする:
- コード品質・可読性
- パフォーマンス
- 設計パターンの適切さ
- エラーハンドリング

### Agent 2: セキュリティレビュー
セキュリティ専門家の観点でSAST的な静的解析:
- OWASP Top 10
- インジェクション（SQL、コマンド、LDAP等）
- 認証・認可の不備
- 機密情報の漏洩

### Agent 3: 設計準拠レビュー
以下を読み込み、実装が設計に準拠しているかチェック:
1. `docs/ARCHITECTURE-RULES.md` — RULE-* への準拠
2. `docs/document-map.yaml` で対象モジュールの関連ドキュメントを特定
3. 該当の機能設計書（`docs/functional-design/API-*.md`, `SCR-*.md` 等）— API仕様・画面仕様との整合
4. 該当の要件定義書（`docs/functional-requirements/*.md`）— 業務ルール・業務フローとの整合

### レビュー結果の投稿

3つのレビュー結果を統合し、PRコメントとして投稿する:
```markdown
## レビュー結果

### 1. セキュリティレビュー — PASS/FAIL
...

### 2. 専門家レビュー — PASS
**Critical**: なし / **Major**: なし
| # | 重要度 | 指摘内容 |
|---|--------|---------|
| m-1 | Minor | ... |
| S-1 | Suggestion | ... |

### 3. 設計準拠レビュー
| # | チェック項目 | 結果 | 備考 |
|---|------------|------|------|
| 1 | RULE-API-001 | ✅ | ... |
```

**指摘番号の採番規則**: `C-{n}` (Critical), `M-{n}` (Major), `m-{n}` (Minor), `S-{n}` (Suggestion), `F#{n}` (設計準拠)

---

## Phase 6: レビュー指摘対応

**目的**: レビュー指摘事項をすべて対応する

### 対応ルール

1. **コード修正・テスト追加・リファクタリング** → そのPR内で即対応
   - 1件ごとに: 修正 → テスト → コミット → プッシュ
2. **設計書（docs/配下）の修正が必要** → 新しいIssueを作成
3. **設計判断・方針決定が必要** → 新しいIssueを作成

### 対応後の確認

指摘対応後に再度テストを実行して全テスト通過を確認する:
```
cd backend && ./gradlew test
```

### 最終確認: 全指摘事項の棚卸し

全レビュー指摘の対応状況を一覧表にまとめ、PRコメントに投稿する:

```markdown
## レビュー指摘 対応結果

| # | 重要度 | 指摘内容 | 対応 | コミット |
|---|--------|---------|------|---------|
| M-1 | Major | 〇〇の修正 | ✅ 対応済み | abc1234 |
| m-2 | Minor | △△の改善 | ✅ 対応済み | def5678 |
| S-1 | Suggestion | □□の提案 | 対応見送り（理由: ...） | — |
| F#1 | Medium | ××の不整合 | 📋 Issue #999 で追跡 | — |
```

### 未対応指摘のIssue化

以下に該当する指摘は **GitHub Issue を作成して追跡**する:
- 設計書（docs/配下）の修正が必要な指摘
- 設計判断・方針決定が必要な指摘
- スコープ外だが将来対応すべき指摘

Issue作成時のルール:
- タイトルに元のレビュー指摘番号を含める（例: `[M-3] 〇〇の対応`）
- 本文に指摘内容・背景・対応方針案を記載する

**対応見送りの理由は必ず明記する。**

---

## 完了条件

以下がすべて満たされていることを確認:
- [ ] 全テストがグリーン
- [ ] BE: C0/C1 カバレッジが100%（または理由付きで例外）
- [ ] FE（変更がある場合）: C0 100% / C1 95%（または理由付きで例外）
- [ ] PR本文にカバレッジ記載
- [ ] 3種レビュー完了・結果をPRコメント
- [ ] **全指摘事項の対応状況を一覧表でPRコメントに投稿済み**
- [ ] **未対応指摘のうちIssue化が必要なものはすべてIssue作成済み**
- [ ] Issue のタスク一覧すべてチェック済み

**注意: PRのマージはユーザーが行う。Claudeは絶対にマージしない。**
