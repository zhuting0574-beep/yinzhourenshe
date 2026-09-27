ALTER TABLE admins ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'ADMIN';

INSERT INTO admins (username, password_hash, role, status)
VALUES
  ('operator1', '$2y$10$.Iti7ZDiT/LyOFKdVz4D7.5fTK6Bs8Jd7BsaUz7g3jALjwnG9Bv/q', 'ORDER_OPERATOR', 'ACTIVE'),
  ('operator2', '$2y$10$.Iti7ZDiT/LyOFKdVz4D7.5fTK6Bs8Jd7BsaUz7g3jALjwnG9Bv/q', 'ORDER_OPERATOR', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  role = VALUES(role),
  status = VALUES(status);
