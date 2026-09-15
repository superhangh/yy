DROP TABLE IF EXISTS `dispatch_order`;
CREATE TABLE `dispatch_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(32) NOT NULL COMMENT '订单号',
  `merchant_id` bigint NOT NULL COMMENT '发单商家编号',
  `user_id` bigint DEFAULT NULL COMMENT '接单用户编号（抢单前为 NULL）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态：0待接单 10已接单 20服务中 30已完成 40已取消',
  `pay_status` tinyint NOT NULL DEFAULT 0 COMMENT '结算状态：0待结算 10已结算 20已退款',
  `title` varchar(128) NOT NULL COMMENT '服务标题',
  `description` varchar(512) NOT NULL DEFAULT '' COMMENT '服务描述',
  `images` varchar(1024) NOT NULL DEFAULT '' COMMENT '服务图片（逗号分隔）',
  `address` varchar(255) NOT NULL DEFAULT '' COMMENT '服务地址',
  `contact_name` varchar(32) NOT NULL DEFAULT '' COMMENT '联系人',
  `contact_mobile` varchar(20) NOT NULL DEFAULT '' COMMENT '联系电话',
  `amount` int NOT NULL COMMENT '酬劳金额（分）',
  `deadline` datetime DEFAULT NULL COMMENT '接单截止时间',
  `accept_time` datetime DEFAULT NULL COMMENT '接单时间',
  `start_time` datetime DEFAULT NULL COMMENT '开始服务时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `cancel_reason` varchar(255) NOT NULL DEFAULT '' COMMENT '取消原因',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`no`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单订单';

DROP TABLE IF EXISTS `dispatch_order_log`;
CREATE TABLE `dispatch_order_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '订单编号',
  `operate_type` tinyint NOT NULL COMMENT '操作类型',
  `content` varchar(255) NOT NULL DEFAULT '' COMMENT '操作内容',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单订单操作日志';
