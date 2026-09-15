-- 派单订单超时取消 Job 注册
--
-- 重要：仅插入 infra_job 行不会自动被 Quartz 调度（yudao 在「通过管理端创建/更新 Job」时才注册，
-- 且 local profile 默认排除了 QuartzAutoConfiguration）。导入本 SQL 后，必须再调用一次：
--   POST /admin-api/infra/job/sync        （把 infra_job 同步进 Quartz，一次性即可，之后持久化在 QRTZ_* 表）
-- 并且 local 环境需启用 Quartz（见 application-local.yaml，已移除 QuartzAutoConfiguration 的 exclude）。

DELETE FROM `infra_job` WHERE `handler_name` = 'dispatchOrderTimeoutJob';
INSERT INTO `infra_job` (`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('派单订单超时取消 Job', 1, 'dispatchOrderTimeoutJob', NULL, '0 0/1 * * * ?', 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
