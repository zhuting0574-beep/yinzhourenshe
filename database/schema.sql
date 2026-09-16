-- 只创建数据库。表结构由后端启动时的 Flyway 迁移统一维护，避免手工脚本与应用版本不一致。
CREATE DATABASE IF NOT EXISTS wx_community
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
