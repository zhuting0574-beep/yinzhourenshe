ALTER TABLE users ADD COLUMN gender VARCHAR(20) NULL;
ALTER TABLE activity_participations ADD COLUMN registration_phone VARCHAR(30) NULL;
ALTER TABLE orders ADD COLUMN reopened_at DATETIME NULL;
CREATE INDEX idx_users_created_phone ON users(created_at, phone);
CREATE INDEX idx_activity_participation_activity_registered ON activity_participations(activity_id, registered_at);
CREATE INDEX idx_orders_product_name ON orders(product_name);
