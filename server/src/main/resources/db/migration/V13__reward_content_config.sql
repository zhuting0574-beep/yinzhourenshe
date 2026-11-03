INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'REWARD_PROFILE', '完善信息奖励积分', '100', 90, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'REWARD_PROFILE');

INSERT INTO content_items(content_type, title, content, sort_order, enabled)
SELECT 'REWARD_DAILY_CHECKIN', '每日签到奖励积分', '10', 91, TRUE
WHERE NOT EXISTS (SELECT 1 FROM content_items WHERE content_type = 'REWARD_DAILY_CHECKIN');
