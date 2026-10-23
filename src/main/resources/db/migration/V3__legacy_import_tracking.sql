-- Track imported legacy primary keys so retries add only data not imported before.
CREATE TABLE legacy_import_map (
 source_schema VARCHAR(64) NOT NULL,
 entity_name VARCHAR(32) NOT NULL,
 source_key VARCHAR(128) NOT NULL,
 target_id BIGINT NOT NULL,
 action_taken VARCHAR(24) NOT NULL,
 imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(source_schema,entity_name,source_key),
 INDEX idx_legacy_target(entity_name,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

