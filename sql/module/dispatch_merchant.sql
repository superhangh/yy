DROP TABLE IF EXISTS `dispatch_merchant`;
CREATE TABLE `dispatch_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_user_id` bigint NOT NULL COMMENT '会员用户编号',
  `name` varchar(64) NOT NULL COMMENT '商家名称',
  `logo` varchar(255) NOT NULL DEFAULT '' COMMENT '商家 Logo',
  `contact_name` varchar(32) NOT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) NOT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1正常 2禁用',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(255) NOT NULL DEFAULT '' COMMENT '审核备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_member_user_id` (`member_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单商家';
