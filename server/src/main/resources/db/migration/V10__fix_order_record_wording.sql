UPDATE content_items
SET content = REPLACE(content, '和绑定手机号', '或绑定手机号')
WHERE content_type = 'ORDER_RECORD'
  AND content LIKE '%和绑定手机号%';
