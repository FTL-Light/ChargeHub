-- ============================================================
-- 第1周技术栈升级 - 增量迁移脚本
-- 适用：已按 charging_station.sql 初始化过的存量库
-- 执行：mysql -uroot -p charging_station < sql/migration_week1.sql
-- 说明：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，这里用
--       information_schema 预检 + 动态 SQL 实现幂等，可重复执行。
-- ============================================================

-- 1) 订单表增加乐观锁版本号（@Version 生效前提：列存在且非 NULL）
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_order'
      AND COLUMN_NAME = 'version'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_order` ADD COLUMN `version` int DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `status`',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 存量数据兜底：历史行可能为 NULL，MP 在 version 为 NULL 时不拼接乐观锁条件
UPDATE `t_order` SET `version` = 0 WHERE `version` IS NULL;

-- 3) 校验
-- SELECT COLUMN_NAME, COLUMN_DEFAULT, COLUMN_COMMENT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = 'charging_station' AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'version';
