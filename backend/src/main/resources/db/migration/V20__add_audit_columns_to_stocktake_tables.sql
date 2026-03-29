-- stocktake_headers に created_by / updated_by 監査カラムを追加（RULE-DB-005 準拠）
ALTER TABLE stocktake_headers ADD COLUMN created_by BIGINT NULL;
ALTER TABLE stocktake_headers ADD COLUMN updated_by BIGINT NULL;

-- stocktake_lines に created_by / updated_by 監査カラムを追加（RULE-DB-005 準拠）
ALTER TABLE stocktake_lines ADD COLUMN created_by BIGINT NULL;
ALTER TABLE stocktake_lines ADD COLUMN updated_by BIGINT NULL;
