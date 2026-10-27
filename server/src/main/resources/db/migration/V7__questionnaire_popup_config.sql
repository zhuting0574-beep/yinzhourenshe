ALTER TABLE questionnaires
  ADD COLUMN popup_image_url VARCHAR(500) NULL AFTER description,
  ADD COLUMN popup_title VARCHAR(200) NULL AFTER popup_image_url,
  ADD COLUMN header_title VARCHAR(200) NULL AFTER popup_title;
