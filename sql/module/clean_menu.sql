-- 清理未迁移模块的菜单，只保留：系统管理(1)/基础设施(2)/支付管理(114)/会员中心(373)/派单管理(90000)
-- 对应 spec §7.1「后台保留系统/基础设施，新增派单管理」

-- 1) 删除不需要的顶级菜单
DELETE FROM `system_menu`
WHERE `parent_id` = 0 AND `id` NOT IN (1, 2, 114, 373, 90000);

-- 2) 级联删除孤儿子菜单（多次执行直到影响 0 行；覆盖较深层级）
DELETE FROM `system_menu` WHERE `parent_id` > 0 AND `parent_id` NOT IN (SELECT `id` FROM (SELECT `id` FROM `system_menu`) AS T);
DELETE FROM `system_menu` WHERE `parent_id` > 0 AND `parent_id` NOT IN (SELECT `id` FROM (SELECT `id` FROM `system_menu`) AS T);
DELETE FROM `system_menu` WHERE `parent_id` > 0 AND `parent_id` NOT IN (SELECT `id` FROM (SELECT `id` FROM `system_menu`) AS T);
DELETE FROM `system_menu` WHERE `parent_id` > 0 AND `parent_id` NOT IN (SELECT `id` FROM (SELECT `id` FROM `system_menu`) AS T);
DELETE FROM `system_menu` WHERE `parent_id` > 0 AND `parent_id` NOT IN (SELECT `id` FROM (SELECT `id` FROM `system_menu`) AS T);
DELETE FROM `system_menu` WHERE `parent_id` > 0 AND `parent_id` NOT IN (SELECT `id` FROM (SELECT `id` FROM `system_menu`) AS T);

-- 3) 清理角色-菜单关联中已不存在的菜单
DELETE FROM `system_role_menu` WHERE `menu_id` NOT IN (SELECT `id` FROM `system_menu`);
