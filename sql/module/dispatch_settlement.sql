DROP TABLE IF EXISTS `dispatch_merchant_wallet`;
CREATE TABLE `dispatch_merchant_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `merchant_id` bigint NOT NULL COMMENT '商家编号',
  `balance` int NOT NULL DEFAULT 0 COMMENT '余额（分）',
  `total_recharge` int NOT NULL DEFAULT 0 COMMENT '累计充值（分）',
  `total_consume` int NOT NULL DEFAULT 0 COMMENT '累计消费（分）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单商家钱包';

DROP TABLE IF EXISTS `dispatch_settlement`;
CREATE TABLE `dispatch_settlement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '订单编号',
  `merchant_id` bigint NOT NULL COMMENT '商家编号',
  `user_id` bigint NOT NULL COMMENT '接单用户编号',
  `amount` int NOT NULL COMMENT '结算金额（分）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待结算 1已结算 2已退款',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单结算记录';
