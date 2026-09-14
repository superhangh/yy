CREATE TABLE IF NOT EXISTS "dispatch_merchant"
(
    "id"             bigint       NOT NULL AUTO_INCREMENT,
    "member_user_id" bigint       NOT NULL,
    "name"           varchar(64)  NOT NULL,
    "logo"           varchar(255) NOT NULL DEFAULT '',
    "contact_name"   varchar(32)  NOT NULL,
    "contact_mobile" varchar(20)  NOT NULL,
    "status"         tinyint      NOT NULL DEFAULT 0,
    "audit_time"     datetime     NULL,
    "audit_remark"   varchar(255) NOT NULL DEFAULT '',
    "creator"        varchar(64)  NULL DEFAULT '',
    "create_time"    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"        varchar(64)  NULL DEFAULT '',
    "update_time"    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        bit(1)       NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    UNIQUE ("member_user_id")
);
