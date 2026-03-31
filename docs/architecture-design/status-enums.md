# ステータス・Enum 定義一覧（SSOT）

> **本書はシステム全体で使用するすべての Enum・区分値定義の SSOT（Single Source of Truth）である。**
> Enum の追加・変更は必ず本書を先に更新し、バックエンド・フロントエンドの実装はこの定義に従うこと。
>
> JPA マッピングパターン・セレクトボックスヘルパー等の **実装パターン** については
> [08-common-infrastructure.md セクション 6](./08-common-infrastructure.md#6-共通コード管理設計) を参照。

---

## Enum 一覧（サマリー）

| Enum 名 | 説明 | 値の数 | パッケージ |
|---------|------|--------|----------|
| `StorageCondition` | 保管条件 | 3 | `com.wms.shared.enums` |
| `PartnerType` | 取引先種別 | 3 | `com.wms.shared.enums` |
| `UserRole` | ユーザーロール | 4 | `com.wms.shared.enums` |
| `UnitType` | 荷姿 | 3 | `com.wms.shared.enums` |
| `AreaType` | エリア種別 | 4 | `com.wms.shared.enums` |
| `InboundStatus` | 入荷伝票ステータス | 6 | `com.wms.shared.enums` |
| `OutboundStatus` | 出荷伝票ステータス | 7 | `com.wms.shared.enums` |
| `MovementType` | 在庫変動種別 | 10 | `com.wms.shared.enums` |
| `StocktakeStatus` | 棚卸ステータス | 2 | `com.wms.shared.enums` |
| `UnpackInstructionStatus` | ばらし指示ステータス | 2 | `com.wms.shared.enums` |

---

## 1. バックエンド — Java Enum 定義

> DBには文字列値（例: `"AMBIENT"`）で保存する（`@Enumerated(EnumType.STRING)`）。
> JPA マッピングパターンは [08-common-infrastructure.md セクション 6.3](./08-common-infrastructure.md#63-バックエンド--jpa-enum-マッピング) を参照。

### StorageCondition（保管条件）

```java
package com.wms.shared.enums;

/**
 * 保管条件。
 * DBには文字列値（"AMBIENT" 等）で保存する。
 */
public enum StorageCondition {
    AMBIENT("常温"),
    REFRIGERATED("冷蔵"),
    FROZEN("冷凍");

    private final String displayName;

    StorageCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### PartnerType（取引先種別）

```java
package com.wms.shared.enums;

/** 取引先種別 */
public enum PartnerType {
    SUPPLIER("仕入先"),
    CUSTOMER("出荷先"),
    BOTH("両方");

    private final String displayName;

    PartnerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### UserRole（ユーザーロール）

```java
package com.wms.shared.enums;

/** ユーザーロール */
public enum UserRole {
    SYSTEM_ADMIN("システム管理者"),
    WAREHOUSE_MANAGER("倉庫管理者"),
    WAREHOUSE_STAFF("倉庫スタッフ"),
    VIEWER("閲覧者");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### UnitType（荷姿）

```java
package com.wms.shared.enums;

/** 荷姿 */
public enum UnitType {
    CASE("ケース"),
    BALL("ボール"),
    PIECE("バラ");

    private final String displayName;

    UnitType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### AreaType（エリア種別）

```java
package com.wms.shared.enums;

/** エリア種別 */
public enum AreaType {
    STOCK("在庫エリア"),
    INBOUND("入荷エリア"),
    OUTBOUND("出荷エリア"),
    RETURN("返品エリア");

    private final String displayName;

    AreaType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### InboundStatus（入荷伝票ステータス）

```java
package com.wms.shared.enums;

/** 入荷伝票ステータス */
public enum InboundStatus {
    PLANNED("入荷予定"),
    CONFIRMED("入荷確認済"),
    INSPECTING("検品中"),
    PARTIAL_STORED("一部入庫"),
    STORED("入庫完了"),
    CANCELLED("キャンセル");

    private final String displayName;

    InboundStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### OutboundStatus（出荷伝票ステータス）

```java
package com.wms.shared.enums;

/** 出荷伝票ステータス */
public enum OutboundStatus {
    ORDERED("受注"),
    PARTIAL_ALLOCATED("一部引当"),
    ALLOCATED("引当完了（ピッキング指示済み）"),
    PICKING_COMPLETED("ピッキング完了"),
    INSPECTING("出荷検品中"),
    SHIPPED("出荷完了"),
    CANCELLED("キャンセル");

    private final String displayName;

    OutboundStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### MovementType（在庫変動種別）

```java
package com.wms.shared.enums;

/** 在庫変動種別 */
public enum MovementType {
    INBOUND("入庫"),
    OUTBOUND("出庫"),
    MOVE_OUT("移動元出庫"),
    MOVE_IN("移動先入庫"),
    BREAKDOWN_OUT("ばらし元出庫"),
    BREAKDOWN_IN("ばらし先入庫"),
    CORRECTION("在庫訂正"),
    STOCKTAKE_ADJUSTMENT("棚卸差異調整"),
    INBOUND_CANCEL("入荷キャンセル戻し"),
    RETURN_OUT("返品出庫");

    private final String displayName;

    MovementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### StocktakeStatus（棚卸ステータス）

```java
package com.wms.shared.enums;

/** 棚卸ステータス */
public enum StocktakeStatus {
    STARTED("棚卸中"),
    CONFIRMED("確定済");

    private final String displayName;

    StocktakeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

### UnpackInstructionStatus（ばらし指示ステータス）

```java
package com.wms.shared.enums;

/** ばらし指示ステータス */
public enum UnpackInstructionStatus {
    INSTRUCTED("指示済"),
    COMPLETED("完了");

    private final String displayName;

    UnpackInstructionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

---

## 2. バックエンド — JPA Enum マッピングパターン

```java
// Entity 内での Enum マッピング
@Enumerated(EnumType.STRING)
@Column(name = "storage_condition", nullable = false)
private StorageCondition storageCondition;
```

---

## 3. フロントエンド — TypeScript 定数定義

> `as const` パターンを使用し、型と値オブジェクトを同名で export する。
> セレクトボックス変換ヘルパーは [セクション 4](#4-フロントエンド--enum-をセレクトボックスに変換するヘルパー) を参照。

```typescript
// src/constants/enums.ts

export const StorageCondition = {
  AMBIENT: 'AMBIENT',
  REFRIGERATED: 'REFRIGERATED',
  FROZEN: 'FROZEN',
} as const

export type StorageCondition =
  typeof StorageCondition[keyof typeof StorageCondition]

export const StorageConditionLabel: Record<StorageCondition, string> = {
  AMBIENT: '常温',
  REFRIGERATED: '冷蔵',
  FROZEN: '冷凍',
}

export const PartnerType = {
  SUPPLIER: 'SUPPLIER',
  CUSTOMER: 'CUSTOMER',
  BOTH: 'BOTH',
} as const

export type PartnerType = typeof PartnerType[keyof typeof PartnerType]

export const PartnerTypeLabel: Record<PartnerType, string> = {
  SUPPLIER: '仕入先',
  CUSTOMER: '出荷先',
  BOTH: '両方',
}

export const UserRole = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  WAREHOUSE_MANAGER: 'WAREHOUSE_MANAGER',
  WAREHOUSE_STAFF: 'WAREHOUSE_STAFF',
  VIEWER: 'VIEWER',
} as const

export type UserRole = typeof UserRole[keyof typeof UserRole]

export const UserRoleLabel: Record<UserRole, string> = {
  SYSTEM_ADMIN: 'システム管理者',
  WAREHOUSE_MANAGER: '倉庫管理者',
  WAREHOUSE_STAFF: '倉庫スタッフ',
  VIEWER: '閲覧者',
}

export const UnitType = {
  CASE: 'CASE',
  BALL: 'BALL',
  PIECE: 'PIECE',
} as const

export type UnitType = typeof UnitType[keyof typeof UnitType]

export const UnitTypeLabel: Record<UnitType, string> = {
  CASE: 'ケース',
  BALL: 'ボール',
  PIECE: 'バラ',
}

export const AreaType = {
  STOCK: 'STOCK',
  INBOUND: 'INBOUND',
  OUTBOUND: 'OUTBOUND',
  RETURN: 'RETURN',
} as const

export type AreaType = typeof AreaType[keyof typeof AreaType]

export const AreaTypeLabel: Record<AreaType, string> = {
  STOCK: '在庫エリア',
  INBOUND: '入荷エリア',
  OUTBOUND: '出荷エリア',
  RETURN: '返品エリア',
}

export const InboundStatus = {
  PLANNED: 'PLANNED',
  CONFIRMED: 'CONFIRMED',
  INSPECTING: 'INSPECTING',
  PARTIAL_STORED: 'PARTIAL_STORED',
  STORED: 'STORED',
  CANCELLED: 'CANCELLED',
} as const

export type InboundStatus =
  typeof InboundStatus[keyof typeof InboundStatus]

export const InboundStatusLabel: Record<InboundStatus, string> = {
  PLANNED: '入荷予定',
  CONFIRMED: '入荷確認済',
  INSPECTING: '検品中',
  PARTIAL_STORED: '一部入庫',
  STORED: '入庫完了',
  CANCELLED: 'キャンセル',
}

export const OutboundStatus = {
  ORDERED: 'ORDERED',
  PARTIAL_ALLOCATED: 'PARTIAL_ALLOCATED',
  ALLOCATED: 'ALLOCATED',
  PICKING_COMPLETED: 'PICKING_COMPLETED',
  INSPECTING: 'INSPECTING',
  SHIPPED: 'SHIPPED',
  CANCELLED: 'CANCELLED',
} as const

export type OutboundStatus =
  typeof OutboundStatus[keyof typeof OutboundStatus]

export const OutboundStatusLabel: Record<OutboundStatus, string> = {
  ORDERED: '受注',
  PARTIAL_ALLOCATED: '一部引当',
  ALLOCATED: '引当完了（ピッキング指示済み）',
  PICKING_COMPLETED: 'ピッキング完了',
  INSPECTING: '出荷検品中',
  SHIPPED: '出荷完了',
  CANCELLED: 'キャンセル',
}

export const MovementType = {
  INBOUND: 'INBOUND',
  OUTBOUND: 'OUTBOUND',
  MOVE_OUT: 'MOVE_OUT',
  MOVE_IN: 'MOVE_IN',
  BREAKDOWN_OUT: 'BREAKDOWN_OUT',
  BREAKDOWN_IN: 'BREAKDOWN_IN',
  CORRECTION: 'CORRECTION',
  STOCKTAKE_ADJUSTMENT: 'STOCKTAKE_ADJUSTMENT',
  INBOUND_CANCEL: 'INBOUND_CANCEL',
  RETURN_OUT: 'RETURN_OUT',
} as const

export type MovementType =
  typeof MovementType[keyof typeof MovementType]

export const MovementTypeLabel: Record<MovementType, string> = {
  INBOUND: '入庫',
  OUTBOUND: '出庫',
  MOVE_OUT: '移動元出庫',
  MOVE_IN: '移動先入庫',
  BREAKDOWN_OUT: 'ばらし元出庫',
  BREAKDOWN_IN: 'ばらし先入庫',
  CORRECTION: '在庫訂正',
  STOCKTAKE_ADJUSTMENT: '棚卸差異調整',
  INBOUND_CANCEL: '入荷キャンセル戻し',
  RETURN_OUT: '返品出庫',
}

export const StocktakeStatus = {
  STARTED: 'STARTED',
  CONFIRMED: 'CONFIRMED',
} as const

export type StocktakeStatus =
  typeof StocktakeStatus[keyof typeof StocktakeStatus]

export const StocktakeStatusLabel: Record<StocktakeStatus, string> = {
  STARTED: '棚卸中',
  CONFIRMED: '確定済',
}

export const UnpackInstructionStatus = {
  INSTRUCTED: 'INSTRUCTED',
  COMPLETED: 'COMPLETED',
} as const

export type UnpackInstructionStatus =
  typeof UnpackInstructionStatus[keyof typeof UnpackInstructionStatus]

export const UnpackInstructionStatusLabel: Record<UnpackInstructionStatus, string> = {
  INSTRUCTED: '指示済',
  COMPLETED: '完了',
}
```

---

## 4. フロントエンド — Enum をセレクトボックスに変換するヘルパー

```typescript
// src/utils/enum-helper.ts

export interface SelectOption {
  value: string
  label: string
}

/**
 * ラベルマップを Element Plus の el-select 用オプション配列に変換する。
 */
export function toSelectOptions(
  labelMap: Record<string, string>,
): SelectOption[] {
  return Object.entries(labelMap).map(([value, label]) => ({
    value,
    label,
  }))
}

// 使用例:
// const storageOptions = toSelectOptions(StorageConditionLabel)
// → [{ value: 'AMBIENT', label: '常温' }, ...]
```

---

## 5. Enum 追加・変更手順

1. **本書を先に更新する**（SSOT のため）
2. バックエンド: `com.wms.shared.enums` パッケージの Java ファイルを更新する
3. フロントエンド: `src/constants/enums.ts` を更新する
4. OpenAPI (`wms-api.yaml`) で当該 Enum を使用している箇所も合わせて更新する
5. 関連する画面設計書・API 設計書のステータス遷移表を確認し、必要に応じて更新する
