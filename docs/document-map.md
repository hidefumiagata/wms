# ドキュメント関連マップ (Document Relationship Map)

> **このファイルは `docs/document-map.yaml` から自動生成されています。直接編集しないでください。**
> 
> 再生成: `node docs/scripts/build-docs.js --generate-map`

## モジュール × ドキュメント種別 マトリクス

| モジュール | 要件定義 | API設計 | 画面設計 | 帳票 | バッチ | I/F | データモデル | テスト | アーキテクチャ |
|-----------|---------|---------|---------|------|--------|-----|-------------|--------|--------------|
| **認証・認可** | 00-authentication | API-01-auth | SCR-01-auth | — | — | — | 02-master-tables | TST-AUTH-login-password | 07-auth-architecture, 10-security-architecture |
| **マスタ管理** | 01-master-management | API-02-master-facility, API-03-master-partner, API-04-master-product, API-05-master-user | SCR-02-master-facility, SCR-03-master-partner, SCR-04-master-warehouse, SCR-05-master-product, SCR-06-master-user | — | — | — | 02-master-tables | TST-MST-master-management | — |
| **システムパラメータ** | 01a-system-parameters | API-11-system-parameters | SCR-12-system-parameters | — | — | — | 02-master-tables | TST-SYS-parameters | — |
| **入荷管理** | 02-inbound-management | API-06-inbound | SCR-07-inbound | RPT-01-inbound-inspection, RPT-03-inbound-plan, RPT-04-inbound-result, RPT-05-unreceived-realtime, RPT-06-unreceived-confirmed | — | IF-01-inbound-plan | 03-transaction-tables | TST-INB-inbound | — |
| **在庫管理** | 03-inventory-management | API-07-inventory | SCR-08-inventory-ops, SCR-09-inventory-stocktake | RPT-07-inventory, RPT-08-inventory-transition, RPT-09-inventory-correction, RPT-10-stocktake-list, RPT-11-stocktake-result | — | — | 03-transaction-tables | TST-INV-inventory | — |
| **出荷管理** | 04-outbound-management | API-08-outbound | SCR-10-outbound | RPT-12-picking-instruction, RPT-13-shipping-inspection, RPT-14-delivery-list, RPT-15-unshipped-realtime, RPT-16-unshipped-confirmed | — | IF-02-order | 03-transaction-tables | TST-OUT-outbound | — |
| **在庫引当** | 04a-allocation | API-12-allocation | SCR-13-allocation | — | — | — | 03-transaction-tables | TST-ALL-allocation | — |
| **返品管理** | 08-returns | API-13-returns | SCR-14-returns | RPT-18-returns | — | — | 03-transaction-tables | TST-RTN-returns | — |
| **バッチ処理** | 06-batch-processing | API-09-batch | SCR-11-batch | — | BAT-01-daily-close | — | 04-batch-tables | TST-BAT-batch | — |
| **レポート共通** | 05-reports | API-10-report | — | RPT-17-daily-summary | — | — | — | TST-RPT-reports | — |
| **外部連携I/F** | 07-interface | — | SCR-15-interface | — | — | — | — | TST-IF-interface | 09-interface-architecture |

## モジュール別 詳細

### 認証・認可 (Authentication & Authorization)

**📝 要件定義:**
- [00-authentication](functional-requirements/00-authentication.md)

**🔌 API設計:**
- [API-01-auth](functional-design/API-01-auth.md)

**🖥️ 画面設計:**
- [SCR-01-auth](functional-design/SCR-01-auth.md)

**🗄️ データモデル:**
- [02-master-tables](data-model/02-master-tables.md)

**🧪 テスト仕様:**
- [TST-AUTH-login-password](test-specifications/TST-AUTH-login-password.md)

**🏗️ アーキテクチャ:**
- [07-auth-architecture](architecture-blueprint/07-auth-architecture.md)
- [10-security-architecture](architecture-blueprint/10-security-architecture.md)

### マスタ管理 (Master Management)

**📝 要件定義:**
- [01-master-management](functional-requirements/01-master-management.md)

**🔌 API設計:**
- [API-02-master-facility](functional-design/API-02-master-facility.md)
- [API-03-master-partner](functional-design/API-03-master-partner.md)
- [API-04-master-product](functional-design/API-04-master-product.md)
- [API-05-master-user](functional-design/API-05-master-user.md)

**🖥️ 画面設計:**
- [SCR-02-master-facility](functional-design/SCR-02-master-facility.md)
- [SCR-03-master-partner](functional-design/SCR-03-master-partner.md)
- [SCR-04-master-warehouse](functional-design/SCR-04-master-warehouse.md)
- [SCR-05-master-product](functional-design/SCR-05-master-product.md)
- [SCR-06-master-user](functional-design/SCR-06-master-user.md)

**🗄️ データモデル:**
- [02-master-tables](data-model/02-master-tables.md)

**🧪 テスト仕様:**
- [TST-MST-master-management](test-specifications/TST-MST-master-management.md)

### システムパラメータ (System Parameters)

**📝 要件定義:**
- [01a-system-parameters](functional-requirements/01a-system-parameters.md)

**🔌 API設計:**
- [API-11-system-parameters](functional-design/API-11-system-parameters.md)

**🖥️ 画面設計:**
- [SCR-12-system-parameters](functional-design/SCR-12-system-parameters.md)

**🗄️ データモデル:**
- [02-master-tables](data-model/02-master-tables.md)

**🧪 テスト仕様:**
- [TST-SYS-parameters](test-specifications/TST-SYS-parameters.md)

### 入荷管理 (Inbound Management)

**📝 要件定義:**
- [02-inbound-management](functional-requirements/02-inbound-management.md)

**🔌 API設計:**
- [API-06-inbound](functional-design/API-06-inbound.md)

**🖥️ 画面設計:**
- [SCR-07-inbound](functional-design/SCR-07-inbound.md)

**📊 帳票設計:**
- [RPT-01-inbound-inspection](functional-design/RPT-01-inbound-inspection.md)
- [RPT-03-inbound-plan](functional-design/RPT-03-inbound-plan.md)
- [RPT-04-inbound-result](functional-design/RPT-04-inbound-result.md)
- [RPT-05-unreceived-realtime](functional-design/RPT-05-unreceived-realtime.md)
- [RPT-06-unreceived-confirmed](functional-design/RPT-06-unreceived-confirmed.md)

**🔗 I/F設計:**
- [IF-01-inbound-plan](functional-design/IF-01-inbound-plan.md)

**🗄️ データモデル:**
- [03-transaction-tables](data-model/03-transaction-tables.md)

**🧪 テスト仕様:**
- [TST-INB-inbound](test-specifications/TST-INB-inbound.md)

### 在庫管理 (Inventory Management)

**📝 要件定義:**
- [03-inventory-management](functional-requirements/03-inventory-management.md)

**🔌 API設計:**
- [API-07-inventory](functional-design/API-07-inventory.md)

**🖥️ 画面設計:**
- [SCR-08-inventory-ops](functional-design/SCR-08-inventory-ops.md)
- [SCR-09-inventory-stocktake](functional-design/SCR-09-inventory-stocktake.md)

**📊 帳票設計:**
- [RPT-07-inventory](functional-design/RPT-07-inventory.md)
- [RPT-08-inventory-transition](functional-design/RPT-08-inventory-transition.md)
- [RPT-09-inventory-correction](functional-design/RPT-09-inventory-correction.md)
- [RPT-10-stocktake-list](functional-design/RPT-10-stocktake-list.md)
- [RPT-11-stocktake-result](functional-design/RPT-11-stocktake-result.md)

**🗄️ データモデル:**
- [03-transaction-tables](data-model/03-transaction-tables.md)

**🧪 テスト仕様:**
- [TST-INV-inventory](test-specifications/TST-INV-inventory.md)

### 出荷管理 (Outbound Management)

**📝 要件定義:**
- [04-outbound-management](functional-requirements/04-outbound-management.md)

**🔌 API設計:**
- [API-08-outbound](functional-design/API-08-outbound.md)

**🖥️ 画面設計:**
- [SCR-10-outbound](functional-design/SCR-10-outbound.md)

**📊 帳票設計:**
- [RPT-12-picking-instruction](functional-design/RPT-12-picking-instruction.md)
- [RPT-13-shipping-inspection](functional-design/RPT-13-shipping-inspection.md)
- [RPT-14-delivery-list](functional-design/RPT-14-delivery-list.md)
- [RPT-15-unshipped-realtime](functional-design/RPT-15-unshipped-realtime.md)
- [RPT-16-unshipped-confirmed](functional-design/RPT-16-unshipped-confirmed.md)

**🔗 I/F設計:**
- [IF-02-order](functional-design/IF-02-order.md)

**🗄️ データモデル:**
- [03-transaction-tables](data-model/03-transaction-tables.md)

**🧪 テスト仕様:**
- [TST-OUT-outbound](test-specifications/TST-OUT-outbound.md)

### 在庫引当 (Stock Allocation)

**📝 要件定義:**
- [04a-allocation](functional-requirements/04a-allocation.md)

**🔌 API設計:**
- [API-12-allocation](functional-design/API-12-allocation.md)

**🖥️ 画面設計:**
- [SCR-13-allocation](functional-design/SCR-13-allocation.md)

**🗄️ データモデル:**
- [03-transaction-tables](data-model/03-transaction-tables.md)

**🧪 テスト仕様:**
- [TST-ALL-allocation](test-specifications/TST-ALL-allocation.md)

### 返品管理 (Returns Management)

**📝 要件定義:**
- [08-returns](functional-requirements/08-returns.md)

**🔌 API設計:**
- [API-13-returns](functional-design/API-13-returns.md)

**🖥️ 画面設計:**
- [SCR-14-returns](functional-design/SCR-14-returns.md)

**📊 帳票設計:**
- [RPT-18-returns](functional-design/RPT-18-returns.md)

**🗄️ データモデル:**
- [03-transaction-tables](data-model/03-transaction-tables.md)

**🧪 テスト仕様:**
- [TST-RTN-returns](test-specifications/TST-RTN-returns.md)

### バッチ処理 (Batch Processing)

**📝 要件定義:**
- [06-batch-processing](functional-requirements/06-batch-processing.md)

**🔌 API設計:**
- [API-09-batch](functional-design/API-09-batch.md)

**🖥️ 画面設計:**
- [SCR-11-batch](functional-design/SCR-11-batch.md)

**⚙️ バッチ設計:**
- [BAT-01-daily-close](functional-design/BAT-01-daily-close.md)

**🗄️ データモデル:**
- [04-batch-tables](data-model/04-batch-tables.md)

**🧪 テスト仕様:**
- [TST-BAT-batch](test-specifications/TST-BAT-batch.md)

### レポート共通 (Reporting (Common))

**📝 要件定義:**
- [05-reports](functional-requirements/05-reports.md)

**🔌 API設計:**
- [API-10-report](functional-design/API-10-report.md)

**📊 帳票設計:**
- [RPT-17-daily-summary](functional-design/RPT-17-daily-summary.md)

**🧪 テスト仕様:**
- [TST-RPT-reports](test-specifications/TST-RPT-reports.md)

### 外部連携I/F (External Interface)

**📝 要件定義:**
- [07-interface](functional-requirements/07-interface.md)

**🖥️ 画面設計:**
- [SCR-15-interface](functional-design/SCR-15-interface.md)

**🧪 テスト仕様:**
- [TST-IF-interface](test-specifications/TST-IF-interface.md)

**🏗️ アーキテクチャ:**
- [09-interface-architecture](architecture-blueprint/09-interface-architecture.md)

## 横断的関心事 (Cross-Cutting Concerns)

全モジュールに共通して参照されるドキュメント。

### 全体アーキテクチャ (Overall Architecture)

- [01-overall-architecture](architecture-blueprint/01-overall-architecture.md)
- [02-system-architecture](architecture-design/02-system-architecture.md)

### フロントエンド基盤 (Frontend Architecture)

- [03-frontend-architecture](architecture-blueprint/03-frontend-architecture.md)

### バックエンド基盤 (Backend Architecture)

- [04-backend-architecture](architecture-blueprint/04-backend-architecture.md)

### データベース基盤 (Database Architecture)

- [05-database-architecture](architecture-blueprint/05-database-architecture.md)
- [05-database-architecture](architecture-design/05-database-architecture.md)
- [01-overview](data-model/01-overview.md)

### インフラ基盤 (Infrastructure Architecture)

- [06-infrastructure-architecture](architecture-blueprint/06-infrastructure-architecture.md)
- [06-infrastructure-architecture](architecture-design/06-infrastructure-architecture.md)

### セキュリティ (Security)

- [07-auth-architecture](architecture-blueprint/07-auth-architecture.md)
- [10-security-architecture](architecture-blueprint/10-security-architecture.md)

### アプリケーション共通基盤 (Common Application Infrastructure)

- [08-common-infrastructure](architecture-blueprint/08-common-infrastructure.md)
- [08-common-infrastructure](architecture-design/08-common-infrastructure.md)

### 監視・運用 (Monitoring & Operations)

- [11-monitoring-operations](architecture-blueprint/11-monitoring-operations.md)
- [11-monitoring-operations](architecture-design/11-monitoring-operations.md)

### 開発・デプロイ (Development & Deployment)

- [12-development-deploy](architecture-blueprint/12-development-deploy.md)
- [12-development-deploy](architecture-design/12-development-deploy.md)

### 非機能要件 (Non-Functional Requirements)

- [13-non-functional-requirements](architecture-blueprint/13-non-functional-requirements.md)
- [13-non-functional-requirements](architecture-design/13-non-functional-requirements.md)

### テスト計画 (Test Plan)

- [00-test-plan](test-specifications/00-test-plan.md)

### 設計標準テンプレート (Design Standards & Templates)

- [_standard-api](functional-design/_standard-api.md)
- [_standard-screen](functional-design/_standard-screen.md)
- [_standard-report](functional-design/_standard-report.md)
- [_id-registry](functional-design/_id-registry.md)
- [01-test-template](test-specifications/01-test-template.md)
- [ARCHITECTURE-RULES](ARCHITECTURE-RULES.md)
