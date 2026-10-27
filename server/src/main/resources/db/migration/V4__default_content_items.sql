INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'BANNER', '首页横幅', '', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'BANNER');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'ABOUT', '关于我们', '', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'ABOUT');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'RULES', '积分规则', '<p>请在此编辑积分使用规则。</p>', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'RULES');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'SERVICE', '鄞领π服务点', '', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'SERVICE');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'PICKUP', '领取说明', '', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'PICKUP');
