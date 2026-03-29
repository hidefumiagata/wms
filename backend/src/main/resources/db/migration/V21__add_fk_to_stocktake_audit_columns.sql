-- V20で追加した created_by / updated_by にFK制約を追加（V20マージ時に漏れた修正）
ALTER TABLE stocktake_headers ADD CONSTRAINT fk_stocktake_headers_created_by FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE stocktake_headers ADD CONSTRAINT fk_stocktake_headers_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

ALTER TABLE stocktake_lines ADD CONSTRAINT fk_stocktake_lines_created_by FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE stocktake_lines ADD CONSTRAINT fk_stocktake_lines_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
