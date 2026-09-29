DELETE FROM content_items WHERE content_type = 'PICKUP';

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'PRODUCT_NOTICE', '商品详情温馨提示', '<p>点击兑换后请在周一至周五 9:00-17:00</p><p>来“鄞领π”鄞州区大学生就业创业综合服务中心领取</p><p>暂不支持快递运输，敬请谅解！</p>', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'PRODUCT_NOTICE');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'ORDER_SUCCESS', '兑换成功提示', '<p>请在7个自然日内到服务点领取</p>', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'ORDER_SUCCESS');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'ORDER_RECORD', '兑换记录提示', '<p>核销时请出示订单编号或绑定手机号</p>', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'ORDER_RECORD');
