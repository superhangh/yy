-- 派单模块 菜单与权限（幂等：先清理 id 段 90000-90099）
DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 90000 AND 90099;
DELETE FROM `system_menu` WHERE `id` BETWEEN 90000 AND 90099;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(90000, '派单管理', '', 1, 60, 0, '/dispatch', 'ep:guide', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(90001, '商家管理', '', 2, 1, 90000, 'merchant', 'ep:shop', 'dispatch/merchant/index', 'DispatchMerchant', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(90002, '商家查询', 'dispatch:merchant:query', 3, 1, 90001, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(90003, '商家审核', 'dispatch:merchant:audit', 3, 2, 90001, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(90004, '派单订单', '', 2, 2, 90000, 'order', 'ep:list', 'dispatch/order/index', 'DispatchOrder', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(90005, '订单查询', 'dispatch:order:query', 3, 1, 90004, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(90006, '订单取消', 'dispatch:order:cancel', 3, 2, 90004, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 授权超级管理员角色（role_id = 1）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 90000, '1', NOW(), '1', NOW(), b'0', 1),
(1, 90001, '1', NOW(), '1', NOW(), b'0', 1),
(1, 90002, '1', NOW(), '1', NOW(), b'0', 1),
(1, 90003, '1', NOW(), '1', NOW(), b'0', 1),
(1, 90004, '1', NOW(), '1', NOW(), b'0', 1),
(1, 90005, '1', NOW(), '1', NOW(), b'0', 1),
(1, 90006, '1', NOW(), '1', NOW(), b'0', 1);
