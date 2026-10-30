-- ============================================================
-- 第2周延迟队列 - 增量迁移脚本
-- 适用：已执行过 migration_week1.sql 的存量库
-- 执行：mysql -uroot -p charging_station < sql/migration_week2.sql
-- 说明：同样用 information_schema 预检 + 动态 SQL 保证可重复执行
-- ============================================================

-- 1) 订单表增加结算方式：区分用户主动 / 超时自动(MQ) / 超时兜底(定时任务)
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_order'
      AND COLUMN_NAME = 'settle_type'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_order` ADD COLUMN `settle_type` tinyint NOT NULL DEFAULT 0 COMMENT ''结算方式: 0-用户主动, 1-超时自动, 2-兜底任务'' AFTER `version`',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 兜底扫描索引：没有索引时定时任务每次扫描都会全表扫 xx 万行
SET @idx1_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_order'
      AND INDEX_NAME = 'idx_order_status_start'
);

SET @ddl := IF(@idx1_exists = 0,
    'CREATE INDEX `idx_order_status_start` ON `t_order` (`status`, `start_time`)',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 待支付关单扫描：按 status + update_time 过滤
SET @idx2_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_order'
      AND INDEX_NAME = 'idx_order_status_update'
);

SET @ddl := IF(@idx2_exists = 0,
    'CREATE INDEX `idx_order_status_update` ON `t_order` (`status`, `update_time`)',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 存量数据兜底
UPDATE `t_order` SET `settle_type` = 0 WHERE `settle_type` IS NULL;

-- ============================================================
-- 校验语句（手工执行确认）
-- ============================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'settle_type';
--
-- SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
-- FROM information_schema.STATISTICS
-- WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order'
--   AND INDEX_NAME IN ('idx_order_status_start', 'idx_order_status_update')
-- ORDER BY INDEX_NAME, SEQ_IN_INDEX;
--
-- 卡单观测（演示"修复前/后"用）：
-- SELECT COUNT(*) AS 卡单量 FROM t_order
-- WHERE deleted = 0 AND status = 1
--   AND start_time < DATE_SUB(NOW(), INTERVAL 720 MINUTE);
--
-- SELECT settle_type, COUNT(*) FROM t_order WHERE deleted = 0 GROUP BY settle_type;
