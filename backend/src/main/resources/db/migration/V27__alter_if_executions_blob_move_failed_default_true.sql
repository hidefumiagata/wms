-- V27: if_executions.blob_move_failed のデフォルトを悲観値 TRUE に変更
--
-- 背景（Issue #374 / PR #372 レビュー m-4）:
--   これまで blob_move_failed は tx1（DB登録トランザクション）内で false を
--   明示設定し、tx1 コミット後に Blob 移動を行い、失敗時のみ catch ブロックで
--   true に UPDATE する楽観方式だった。
--   しかし catch ブロックの UPDATE は readOnly=true 配下の暗黙自動コミットに
--   依存しており、tx1 と catch の間（アプリ再起動・プロセスkill等）で
--   レコードが失われる障害パターンが残っていた。
--
-- 本マイグレーションの効果:
--   カラムのデフォルトを TRUE に変更することで、アプリ層で明示的に true を
--   設定しなくても DB レベルで悲観デフォルトが担保される（二重のセーフティ）。
--   Blob 移動が成功した場合のみ InterfaceService#moveBlobSafely が独立
--   トランザクションで false に更新する。失敗/中断時は true のまま残り、
--   リカバリバッチ（BAT-IF-RECONCILE, Issue #440）の対象となる。

-- 既存行（V27 適用前に INSERT されたレコード）は過去の成功履歴として
-- blob_move_failed=false のまま維持する。本マイグレーションは新規 INSERT の
-- デフォルト値のみ変更し、既存データには影響を与えない。
-- 詳細は Issue #374 / アーキテクチャ設計書 09-interface-architecture.md セクション 7.4 参照。
ALTER TABLE if_executions
    ALTER COLUMN blob_move_failed SET DEFAULT TRUE;

COMMENT ON COLUMN if_executions.blob_move_failed IS
    'Blob移動失敗フラグ。tx1コミット時に悲観デフォルトtrueで初期化し、Blob移動成功後にfalseへ更新。リカバリバッチ（BAT-IF-RECONCILE）の対象選択に使用。';
