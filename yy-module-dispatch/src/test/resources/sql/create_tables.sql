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

CREATE TABLE IF NOT EXISTS "dispatch_merchant_wallet"
(
    "id"             bigint  NOT NULL AUTO_INCREMENT,
    "merchant_id"    bigint  NOT NULL,
    "balance"        int     NOT NULL DEFAULT 0,
    "total_recharge" int    NOT NULL DEFAULT 0,
    "total_consume"  int     NOT NULL DEFAULT 0,
    "creator"        varchar(64) NULL DEFAULT '',
    "create_time"    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"        varchar(64) NULL DEFAULT '',
    "update_time"    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        bit(1)  NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    UNIQUE ("merchant_id")
);

CREATE TABLE IF NOT EXISTS "dispatch_settlement"
(
    "id"           bigint       NOT NULL AUTO_INCREMENT,
    "order_id"     bigint       NOT NULL,
    "merchant_id"  bigint       NOT NULL,
    "user_id"      bigint       NOT NULL,
    "amount"       int          NOT NULL,
    "status"       tinyint      NOT NULL DEFAULT 0,
    "settle_time"  datetime     NULL,
    "refund_time"  datetime     NULL,
    "remark"       varchar(255) NOT NULL DEFAULT '',
    "creator"      varchar(64)  NULL DEFAULT '',
    "create_time"  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"      varchar(64)  NULL DEFAULT '',
    "update_time"  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"      bit(1)       NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    UNIQUE ("order_id")
);

CREATE TABLE IF NOT EXISTS "dispatch_order"
(
    "id"             bigint       NOT NULL AUTO_INCREMENT,
    "no"             varchar(32)  NOT NULL,
    "merchant_id"    bigint       NOT NULL,
    "user_id"        bigint       NULL,
    "status"         tinyint      NOT NULL DEFAULT 0,
    "pay_status"     tinyint      NOT NULL DEFAULT 0,
    "title"          varchar(128) NOT NULL,
    "description"    varchar(512) NOT NULL DEFAULT '',
    "images"         varchar(1024) NOT NULL DEFAULT '',
    "address"        varchar(255) NOT NULL DEFAULT '',
    "contact_name"   varchar(32)  NOT NULL DEFAULT '',
    "contact_mobile" varchar(20)  NOT NULL DEFAULT '',
    "amount"         int          NOT NULL,
    "deadline"       datetime     NULL,
    "accept_time"    datetime     NULL,
    "start_time"     datetime     NULL,
    "finish_time"    datetime     NULL,
    "cancel_reason"  varchar(255) NOT NULL DEFAULT '',
    "creator"        varchar(64)  NULL DEFAULT '',
    "create_time"    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"        varchar(64)  NULL DEFAULT '',
    "update_time"    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        bit(1)       NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    UNIQUE ("no")
);

CREATE TABLE IF NOT EXISTS "dispatch_order_log"
(
    "id"           bigint       NOT NULL AUTO_INCREMENT,
    "order_id"     bigint       NOT NULL,
    "operate_type" tinyint      NOT NULL,
    "content"      varchar(255) NOT NULL DEFAULT '',
    "creator"      varchar(64)  NULL DEFAULT '',
    "create_time"  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"      varchar(64)  NULL DEFAULT '',
    "update_time"  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"      bit(1)       NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
);
