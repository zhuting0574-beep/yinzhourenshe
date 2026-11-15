ALTER TABLE activities
  ADD COLUMN share_env_version VARCHAR(16) NULL AFTER share_url_created_at;

ALTER TABLE questionnaires
  ADD COLUMN share_env_version VARCHAR(16) NULL AFTER share_url_created_at;
