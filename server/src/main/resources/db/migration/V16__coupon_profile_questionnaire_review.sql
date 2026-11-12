ALTER TABLE products
  ADD COLUMN product_type VARCHAR(30) NOT NULL DEFAULT 'PHYSICAL' AFTER name,
  ADD COLUMN publish_mode VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE' AFTER status,
  ADD COLUMN publish_at DATETIME NULL AFTER publish_mode,
  ADD COLUMN per_user_limit INT NOT NULL DEFAULT 1 AFTER publish_at;

CREATE INDEX idx_products_publish ON products(status, publish_at);

ALTER TABLE orders
  ADD COLUMN product_type VARCHAR(30) NOT NULL DEFAULT 'PHYSICAL' AFTER product_id,
  ADD COLUMN coupon_code_id BIGINT NULL AFTER product_type,
  ADD COLUMN coupon_code_snapshot VARCHAR(500) NULL AFTER coupon_code_id;

CREATE TABLE product_coupon_codes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  code_value VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  order_id BIGINT NULL,
  user_id BIGINT NULL,
  assigned_at DATETIME NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_coupon_code(product_id, code_value),
  UNIQUE KEY uk_coupon_order(order_id),
  INDEX idx_coupon_allocate(product_id, status, id),
  INDEX idx_coupon_user(product_id, user_id)
);

ALTER TABLE users
  ADD COLUMN birth_date DATE NULL AFTER age,
  ADD COLUMN grade_year INT NULL AFTER birth_date;

ALTER TABLE activities
  ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT FALSE AFTER status;

ALTER TABLE activity_participations
  ADD COLUMN reviewed_by BIGINT NULL,
  ADD COLUMN reviewed_at DATETIME NULL;

ALTER TABLE questionnaires
  ADD COLUMN share_url VARCHAR(1000) NULL,
  ADD COLUMN share_url_created_at DATETIME NULL;

INSERT INTO content_items(content_type,title,image_url,content,sort_order,enabled)
SELECT 'DEFAULT_ACTIVITY_IMAGE','活动默认图',NULL,NULL,0,TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type='DEFAULT_ACTIVITY_IMAGE');

INSERT INTO content_items(content_type,title,image_url,content,sort_order,enabled)
SELECT 'DEFAULT_QUESTIONNAIRE_IMAGE','问卷默认图',NULL,NULL,0,TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type='DEFAULT_QUESTIONNAIRE_IMAGE');
