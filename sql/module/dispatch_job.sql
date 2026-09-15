DELETE FROM `infra_job` WHERE `handler_name` = 'dispatchOrderTimeoutJob';
INSERT INTO `infra_job` (`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('派单订单超时取消 Job', 1, 'dispatchOrderTimeoutJob', NULL, '0 0/1 * * * ?', 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
