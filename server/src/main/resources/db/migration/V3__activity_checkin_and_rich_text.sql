ALTER TABLE activities
  ADD COLUMN checkin_mode VARCHAR(20) NOT NULL DEFAULT 'QR' AFTER longitude,
  ADD COLUMN checkin_radius_m INT NOT NULL DEFAULT 200 AFTER checkin_mode,
  MODIFY COLUMN description LONGTEXT;

ALTER TABLE products MODIFY COLUMN description LONGTEXT;

ALTER TABLE activity_participations
  ADD COLUMN checkin_latitude DECIMAL(10,7) NULL AFTER checkin_method,
  ADD COLUMN checkin_longitude DECIMAL(10,7) NULL AFTER checkin_latitude,
  ADD COLUMN checkin_accuracy_m DECIMAL(10,2) NULL AFTER checkin_longitude,
  ADD COLUMN checkin_distance_m DECIMAL(10,2) NULL AFTER checkin_accuracy_m;
