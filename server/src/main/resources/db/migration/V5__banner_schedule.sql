ALTER TABLE content_items
    ADD COLUMN start_time DATETIME NULL,
    ADD COLUMN end_time DATETIME NULL,
    ADD INDEX idx_content_banner_active (content_type, enabled, start_time, end_time, sort_order);
