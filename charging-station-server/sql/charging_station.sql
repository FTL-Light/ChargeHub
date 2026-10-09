-- ============================================
-- 新能源充电站管理平台 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS charging_station DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE charging_station;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `password` varchar(100) DEFAULT NULL COMMENT '密码(BCrypt加密)',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `openid` varchar(64) DEFAULT NULL COMMENT '微信openid',
  `unionid` varchar(64) DEFAULT NULL COMMENT '微信unionid',
  `balance` decimal(10,2) DEFAULT '0.00' COMMENT '余额',
  `status` int DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 2. 管理员表
-- ----------------------------
DROP TABLE IF EXISTS `t_admin`;
CREATE TABLE `t_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码(BCrypt加密)',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `role` int DEFAULT '2' COMMENT '角色: 1-超级管理员, 2-普通管理员',
  `status` int DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 默认管理员账号由应用启动时 DataInitializer 自动创建: admin / admin123
-- (密码使用 BCrypt 加密，确保与登录验证一致)

-- ----------------------------
-- 3. 充电站表
-- ----------------------------
DROP TABLE IF EXISTS `t_station`;
CREATE TABLE `t_station` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '站点名称',
  `address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `image` varchar(255) DEFAULT NULL COMMENT '站点图片',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `business_hours` varchar(100) DEFAULT NULL COMMENT '营业时间',
  `charger_count` int DEFAULT '0' COMMENT '充电桩数量',
  `status` int DEFAULT '1' COMMENT '状态: 0-下线, 1-正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电站表';

-- ----------------------------
-- 4. 充电桩表
-- ----------------------------
DROP TABLE IF EXISTS `t_charger`;
CREATE TABLE `t_charger` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `station_id` bigint NOT NULL COMMENT '站点ID',
  `code` varchar(50) DEFAULT NULL COMMENT '编码',
  `name` varchar(100) DEFAULT NULL COMMENT '名称',
  `type` int DEFAULT NULL COMMENT '类型: 1-慢充, 2-快充',
  `power` decimal(10,2) DEFAULT NULL COMMENT '功率(kW)',
  `gun_count` int DEFAULT '1' COMMENT '枪数量',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `status` int DEFAULT '1' COMMENT '状态: 0-故障, 1-空闲, 2-使用中',
  `price` decimal(10,2) DEFAULT NULL COMMENT '单价(元/度)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_station_id` (`station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电桩表';

-- ----------------------------
-- 5. 充电枪表
-- ----------------------------
DROP TABLE IF EXISTS `t_gun`;
CREATE TABLE `t_gun` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `charger_id` bigint NOT NULL COMMENT '充电桩ID',
  `code` varchar(50) DEFAULT NULL COMMENT '编码',
  `status` int DEFAULT '1' COMMENT '状态: 0-故障, 1-空闲, 2-使用中',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_charger_id` (`charger_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电枪表';

-- ----------------------------
-- 6. 订单表
-- ----------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `station_id` bigint DEFAULT NULL COMMENT '站点ID',
  `charger_id` bigint DEFAULT NULL COMMENT '充电桩ID',
  `gun_id` bigint DEFAULT NULL COMMENT '充电枪ID',
  `start_time` datetime DEFAULT NULL COMMENT '开始充电时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束充电时间',
  `duration` int DEFAULT NULL COMMENT '充电时长(分钟)',
  `electricity` decimal(10,2) DEFAULT NULL COMMENT '充电量(度)',
  `price` decimal(10,2) DEFAULT NULL COMMENT '单价(元/度)',
  `total_amount` decimal(10,2) DEFAULT NULL COMMENT '总金额',
  `pay_amount` decimal(10,2) DEFAULT NULL COMMENT '实付金额',
  `pay_type` int DEFAULT NULL COMMENT '支付方式: 1-余额, 2-微信',
  `status` int DEFAULT '1' COMMENT '状态: 0-已取消, 1-充电中, 2-待支付, 3-已完成',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_station_id` (`station_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------
-- 7. 充值记录表
-- ----------------------------
DROP TABLE IF EXISTS `t_recharge_record`;
CREATE TABLE `t_recharge_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `amount` decimal(10,2) NOT NULL COMMENT '充值金额',
  `pay_type` int DEFAULT '1' COMMENT '支付方式: 1-微信',
  `status` int DEFAULT '1' COMMENT '状态: 0-失败, 1-成功',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值记录表';

-- ----------------------------
-- 8. 反馈表
-- ----------------------------
DROP TABLE IF EXISTS `t_feedback`;
CREATE TABLE `t_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `station_id` bigint DEFAULT NULL COMMENT '站点ID',
  `charger_id` bigint DEFAULT NULL COMMENT '充电桩ID',
  `type` int DEFAULT NULL COMMENT '反馈类型: 1-故障, 2-投诉, 3-建议, 4-其他',
  `title` varchar(100) DEFAULT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `images` varchar(1000) DEFAULT NULL COMMENT '图片(逗号分隔)',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` int DEFAULT '0' COMMENT '状态: 0-待处理, 1-已处理',
  `reply` text COMMENT '回复内容',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈表';

-- ----------------------------
-- 9. 公告表
-- ----------------------------
DROP TABLE IF EXISTS `t_notice`;
CREATE TABLE `t_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(100) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `type` int DEFAULT '1' COMMENT '类型: 1-系统公告, 2-活动公告',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `top` int DEFAULT '0' COMMENT '是否置顶: 0-否, 1-是',
  `status` int DEFAULT '1' COMMENT '状态: 0-下线, 1-上线',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- ----------------------------
-- 10. 登录日志表 (无 deleted 字段)
-- ----------------------------
DROP TABLE IF EXISTS `t_login_log`;
CREATE TABLE `t_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_type` int DEFAULT NULL COMMENT '用户类型: 1-管理员, 2-小程序用户',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `login_ip` varchar(50) DEFAULT NULL COMMENT '登录IP',
  `login_location` varchar(100) DEFAULT NULL COMMENT '登录地点',
  `browser` varchar(50) DEFAULT NULL COMMENT '浏览器',
  `os` varchar(50) DEFAULT NULL COMMENT '操作系统',
  `status` int DEFAULT NULL COMMENT '状态: 0-失败, 1-成功',
  `msg` varchar(255) DEFAULT NULL COMMENT '提示信息',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- ----------------------------
-- 11. AI对话日志表 (无 deleted 字段)
-- ----------------------------
DROP TABLE IF EXISTS `t_ai_chat_log`;
CREATE TABLE `t_ai_chat_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `role` varchar(20) DEFAULT NULL COMMENT '角色: user/assistant',
  `content` text COMMENT '内容',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话日志表';

-- ----------------------------
-- 初始化测试数据
-- ----------------------------
INSERT INTO `t_station` (`name`, `address`, `province`, `city`, `district`, `longitude`, `latitude`, `contact_phone`, `business_hours`, `charger_count`, `status`) VALUES
('国网充电站(朝阳店)', '北京市朝阳区建国路88号', '北京市', '北京市', '朝阳区', 116.397428, 39.909230, '010-12345678', '00:00-24:00', 8, 1),
('特来电充电站(海淀店)', '北京市海淀区中关村大街1号', '北京市', '北京市', '海淀区', 116.316840, 39.981940, '010-87654321', '06:00-23:00', 6, 1),
('星星充电站(丰台店)', '北京市丰台区南三环西路16号', '北京市', '北京市', '丰台区', 116.287150, 39.858560, '010-11112222', '00:00-24:00', 4, 1);

INSERT INTO `t_charger` (`station_id`, `code`, `name`, `type`, `power`, `gun_count`, `status`, `price`) VALUES
(1, 'CG001', '1号快充桩', 2, 60.00, 2, 1, 1.50),
(1, 'CG002', '2号慢充桩', 1, 7.00, 1, 1, 0.80),
(2, 'CG003', '1号快充桩', 2, 120.00, 2, 1, 1.80),
(3, 'CG004', '1号快充桩', 2, 60.00, 1, 1, 1.60);

INSERT INTO `t_gun` (`charger_id`, `code`, `status`) VALUES
(1, 'G001', 1),
(1, 'G002', 1),
(2, 'G003', 1),
(3, 'G004', 1),
(3, 'G005', 1),
(4, 'G006', 1);

INSERT INTO `t_notice` (`title`, `content`, `type`, `top`, `status`, `publish_time`) VALUES
('平台上线公告', '欢迎使用新能源充电站管理平台，我们为您提供便捷的充电服务。', 1, 1, 1, NOW()),
('春节充电优惠活动', '春节期间充值满100送20，欢迎大家参与。', 2, 0, 1, NOW());
