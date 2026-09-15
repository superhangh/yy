# yy 结算 Implementation Plan（Plan 4）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现派单结算：商家预充值钱包 + 订单完成后自动结算（商家扣款、记结算单、订单 pay_status→已结算）+ 管理端退款。

**Architecture:** 商家钱包自含（`dispatch_merchant_wallet`，不耦合 pay 模块 `pay_wallet`）。扣款用条件更新（`WHERE balance >= amount`，原子防超扣）。`finishOrder` 提交后发 `DispatchOrderCompletedMessage` 事件，消费者调 `settleOrder`（独立事务，失败仅 log 不影响已完成订单）。退款反转钱包。

**Tech Stack:** JDK 17 · Spring Boot 3.5.15 · MyBatis Plus · MySQL 8（库 `yy`）· 已完成 Plan 2b/3 的 `yy-module-dispatch`

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`（§5.4 结算）

## Global Constraints

- 包名 `com.yy`；模块 `yy-module-dispatch`，Java 根包 `com.yy.module.dispatch`。
- 数据库 MySQL 8，库 `yy`，root/123456；**无多租户**，DO 继承 `BaseDO`。
- 错误码段：结算用 **`1-099-003-xxx`**（商家=001，订单=002，结算=003）。
- 表名 `dispatch_` 前缀：`dispatch_merchant_wallet`、`dispatch_settlement`。
- 金额 `Integer`（单位：分）。
- 钱包扣款**必须**用条件更新（`WHERE merchant_id=? AND balance >= amount`），不得「先查后改」。
- `finishOrder` 已实现（Plan 2b）：`SERVING → COMPLETED`。Plan 4 在完成后追加结算。
- 事件用 Spring Event（`@TransactionalEventListener(AFTER_COMMIT)`），与 Plan 3 同模式。
- 参考样板：`DispatchOrderMapper.updateCancel`（条件更新）、`DispatchOrderWebSocketConsumer`（事件消费者）。

---

### Task 1: 钱包 + 结算表 + Service + 单测

**Files:**
- Create: `D:\IDEA\yy\sql\module\dispatch_settlement.sql`
- Create: `...\enums\settlement\DispatchSettlementStatusEnum.java`
- Create: `...\dal\dataobject\wallet\DispatchMerchantWalletDO.java`
- Create: `...\dal\dataobject\settlement\DispatchSettlementDO.java`
- Create: `...\dal\mysql\wallet\DispatchMerchantWalletMapper.java`
- Create: `...\dal\mysql\settlement\DispatchSettlementMapper.java`
- Create: `...\service\wallet\DispatchMerchantWalletService.java` + `Impl`
- Create: `...\service\settlement\DispatchSettlementService.java` + `Impl`
- Modify: `...\enums\ErrorCodeConstants.java`（追加结算错误码）
- Test: `...\src\test\java\com\yy\module\dispatch\service\wallet\DispatchMerchantWalletServiceTest.java`
- Test: `...\src\test\java\com\yy\module\dispatch\service\settlement\DispatchSettlementServiceTest.java`

**Interfaces:**
- Produces:
  - `DispatchMerchantWalletService.getOrCreateWallet(Long merchantId)→DO`
  - `.recharge(Long merchantId, Integer amount)` — 充值
  - `.getBalance(Long merchantId)→Integer`
  - `.deduct(Long merchantId, Integer amount)→boolean` — 原子扣款，false=余额不足
  - `.credit(Long merchantId, Integer amount)` — 退款入账
  - `DispatchSettlementService.settleOrder(Long orderId)` — 结算
  - `.refundOrder(Long orderId)` — 退款
  - `.getSettlement(Long orderId)→DO`
  - `.getSettlementPage(DispatchSettlementPageReqVO)→PageResult<DO>`

- [ ] **Step 1: 建表 SQL**

创建 `D:\IDEA\yy\sql\module\dispatch_settlement.sql`：
```sql
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
```

导入：
```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 yy -e "source D:/IDEA/yy/sql/module/dispatch_settlement.sql"
mysql -uroot -p123456 yy -e "SHOW TABLES LIKE 'dispatch_merchant_wallet'; SHOW TABLES LIKE 'dispatch_settlement';"
```
Expected: 各返回 1 行。

- [ ] **Step 2: 错误码（追加）**

在 `enums/ErrorCodeConstants.java` 的 `ORDER_NOT_USER_OWNER` 之后追加：
```java

    // ========== 钱包 1-099-003-000 ==========
    ErrorCode WALLET_NOT_EXISTS = new ErrorCode(1_099_003_000, "商家钱包不存在");
    ErrorCode WALLET_INSUFFICIENT_BALANCE = new ErrorCode(1_099_003_001, "商家钱包余额不足");
    ErrorCode SETTLEMENT_NOT_EXISTS = new ErrorCode(1_099_003_002, "结算记录不存在");
    ErrorCode SETTLEMENT_STATUS_ERROR = new ErrorCode(1_099_003_003, "结算状态不允许该操作");
```

- [ ] **Step 3: 结算状态枚举**

`...\enums\settlement\DispatchSettlementStatusEnum.java`：
```java
package com.yy.module.dispatch.enums.settlement;

import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum DispatchSettlementStatusEnum implements ArrayValuable<Integer> {

    UNPAID(0, "待结算"),
    PAID(1, "已结算"),
    REFUNDED(2, "已退款");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchSettlementStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
```

- [ ] **Step 4: DO**

`...\dal\dataobject\wallet\DispatchMerchantWalletDO.java`：
```java
package com.yy.module.dispatch.dal.dataobject.wallet;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("dispatch_merchant_wallet")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchMerchantWalletDO extends BaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    /** 余额（分） */
    private Integer balance;
    /** 累计充值（分） */
    private Integer totalRecharge;
    /** 累计消费（分） */
    private Integer totalConsume;
}
```

`...\dal\dataobject\settlement\DispatchSettlementDO.java`：
```java
package com.yy.module.dispatch.dal.dataobject.settlement;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("dispatch_settlement")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchSettlementDO extends BaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    /** 结算金额（分） */
    private Integer amount;
    /** 状态，见 DispatchSettlementStatusEnum */
    private Integer status;
    private LocalDateTime settleTime;
    private LocalDateTime refundTime;
    private String remark;
}
```

- [ ] **Step 5: Mapper（钱包含原子扣款）**

`...\dal\mysql\wallet\DispatchMerchantWalletMapper.java`：
```java
package com.yy.module.dispatch.dal.mysql.wallet;

import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispatchMerchantWalletMapper extends BaseMapperX<DispatchMerchantWalletDO> {

    default DispatchMerchantWalletDO selectByMerchantId(Long merchantId) {
        return selectOne(DispatchMerchantWalletDO::getMerchantId, merchantId);
    }

    /** 原子扣款：balance >= amount 才扣，返回影响行数 */
    default int updateDeduct(Long merchantId, Integer amount) {
        return update(null, new LambdaUpdateWrapper<DispatchMerchantWalletDO>()
                .setSql(" balance = balance - " + amount)
                .setSql(" total_consume = total_consume + " + amount)
                .eq(DispatchMerchantWalletDO::getMerchantId, merchantId)
                .ge(DispatchMerchantWalletDO::getBalance, amount));
    }

    /** 入账（充值/退款） */
    default int updateAdd(Long merchantId, Integer amount, boolean isRecharge) {
        LambdaUpdateWrapper<DispatchMerchantWalletDO> wrapper = new LambdaUpdateWrapper<DispatchMerchantWalletDO>()
                .setSql(" balance = balance + " + amount)
                .eq(DispatchMerchantWalletDO::getMerchantId, merchantId);
        if (isRecharge) {
            wrapper.setSql(" total_recharge = total_recharge + " + amount);
        }
        return update(null, wrapper);
    }
}
```

`...\dal\mysql\settlement\DispatchSettlementMapper.java`：
```java
package com.yy.module.dispatch.dal.mysql.settlement;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispatchSettlementMapper extends BaseMapperX<DispatchSettlementDO> {

    default DispatchSettlementDO selectByOrderId(Long orderId) {
        return selectOne(DispatchSettlementDO::getOrderId, orderId);
    }

    default PageResult<DispatchSettlementDO> selectPage(DispatchSettlementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DispatchSettlementDO>()
                .eqIfPresent(DispatchSettlementDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(DispatchSettlementDO::getUserId, reqVO.getUserId())
                .eqIfPresent(DispatchSettlementDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DispatchSettlementDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DispatchSettlementDO::getId));
    }
}
```

- [ ] **Step 6: 钱包 Service**

`...\service\wallet\DispatchMerchantWalletService.java`：
```java
package com.yy.module.dispatch.service.wallet;

import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;

public interface DispatchMerchantWalletService {

    DispatchMerchantWalletDO getOrCreateWallet(Long merchantId);

    void recharge(Long merchantId, Integer amount);

    Integer getBalance(Long merchantId);

    /** 原子扣款，返回 false=余额不足 */
    boolean deduct(Long merchantId, Integer amount);

    /** 退款入账 */
    void credit(Long merchantId, Integer amount);
}
```

`...\service\wallet\DispatchMerchantWalletServiceImpl.java`：
```java
package com.yy.module.dispatch.service.wallet;

import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;
import com.yy.module.dispatch.dal.mysql.wallet.DispatchMerchantWalletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

@Service
@Validated
public class DispatchMerchantWalletServiceImpl implements DispatchMerchantWalletService {

    @Resource
    private DispatchMerchantWalletMapper walletMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchMerchantWalletDO getOrCreateWallet(Long merchantId) {
        DispatchMerchantWalletDO wallet = walletMapper.selectByMerchantId(merchantId);
        if (wallet != null) {
            return wallet;
        }
        wallet = DispatchMerchantWalletDO.builder()
                .merchantId(merchantId).balance(0).totalRecharge(0).totalConsume(0)
                .build();
        try {
            walletMapper.insert(wallet);
        } catch (Exception e) {
            // 并发创建：唯一索引兜底，重新查
            wallet = walletMapper.selectByMerchantId(merchantId);
        }
        return wallet;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(Long merchantId, Integer amount) {
        getOrCreateWallet(merchantId);
        walletMapper.updateAdd(merchantId, amount, true);
    }

    @Override
    public Integer getBalance(Long merchantId) {
        DispatchMerchantWalletDO wallet = walletMapper.selectByMerchantId(merchantId);
        return wallet != null ? wallet.getBalance() : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(Long merchantId, Integer amount) {
        getOrCreateWallet(merchantId);
        return walletMapper.updateDeduct(merchantId, amount) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(Long merchantId, Integer amount) {
        getOrCreateWallet(merchantId);
        walletMapper.updateAdd(merchantId, amount, false);
    }
}
```

- [ ] **Step 7: 结算 Service**

`...\service\settlement\DispatchSettlementService.java`：
```java
package com.yy.module.dispatch.service.settlement;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;

public interface DispatchSettlementService {

    void settleOrder(Long orderId);

    void refundOrder(Long orderId);

    DispatchSettlementDO getSettlement(Long orderId);

    PageResult<DispatchSettlementDO> getSettlementPage(DispatchSettlementPageReqVO reqVO);
}
```

`...\service\settlement\DispatchSettlementServiceImpl.java`：
```java
package com.yy.module.dispatch.service.settlement;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.dal.mysql.settlement.DispatchSettlementMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.enums.settlement.DispatchSettlementStatusEnum;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DispatchSettlementServiceImpl implements DispatchSettlementService {

    @Resource
    private DispatchSettlementMapper settlementMapper;
    @Resource
    private DispatchOrderMapper orderMapper;
    @Resource
    private DispatchMerchantWalletService walletService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOrder(Long orderId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        if (!DispatchOrderPayStatusEnum.UNPAID.getStatus().equals(order.getPayStatus())) {
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
        // 原子扣款：余额不足直接失败
        boolean ok = walletService.deduct(order.getMerchantId(), order.getAmount());
        if (!ok) {
            throw exception(WALLET_INSUFFICIENT_BALANCE);
        }
        // 写结算记录
        DispatchSettlementDO settlement = DispatchSettlementDO.builder()
                .orderId(orderId).merchantId(order.getMerchantId()).userId(order.getUserId())
                .amount(order.getAmount()).status(DispatchSettlementStatusEnum.PAID.getStatus())
                .settleTime(LocalDateTime.now()).build();
        settlementMapper.insert(settlement);
        // 条件更新订单 pay_status（防并发重复结算）
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.PAID.getStatus())
                .eq(DispatchOrderDO::getId, orderId)
                .eq(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.UNPAID.getStatus()));
        if (rows == 0) {
            // 被并发结算了，回滚扣款
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long orderId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        if (!DispatchOrderPayStatusEnum.PAID.getStatus().equals(order.getPayStatus())) {
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
        // 退款入账到商家钱包
        walletService.credit(order.getMerchantId(), order.getAmount());
        // 更新结算记录为已退款
        settlementMapper.update(null, new LambdaUpdateWrapper<DispatchSettlementDO>()
                .set(DispatchSettlementDO::getStatus, DispatchSettlementStatusEnum.REFUNDED.getStatus())
                .set(DispatchSettlementDO::getRefundTime, LocalDateTime.now())
                .eq(DispatchSettlementDO::getOrderId, orderId)
                .eq(DispatchSettlementDO::getStatus, DispatchSettlementStatusEnum.PAID.getStatus()));
        // 条件更新订单 pay_status
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.REFUNDED.getStatus())
                .eq(DispatchOrderDO::getId, orderId)
                .eq(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.PAID.getStatus()));
        if (rows == 0) {
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
    }

    @Override
    public DispatchSettlementDO getSettlement(Long orderId) {
        return settlementMapper.selectByOrderId(orderId);
    }

    @Override
    public PageResult<DispatchSettlementDO> getSettlementPage(DispatchSettlementPageReqVO reqVO) {
        return settlementMapper.selectPage(reqVO);
    }
}
```

- [ ] **Step 8: 分页 VO**

`...\controller\admin\settlement\vo\DispatchSettlementPageReqVO.java`：
```java
package com.yy.module.dispatch.controller.admin.settlement.vo;

import com.yy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 结算分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DispatchSettlementPageReqVO extends PageParam {

    @Schema(description = "商家编号")
    private Long merchantId;
    @Schema(description = "用户编号")
    private Long userId;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;
}
```

- [ ] **Step 9: H2 测试表（追加到 `create_tables.sql`）**

在 `yy-module-dispatch\src\test\resources\sql\create_tables.sql` 末尾追加：
```sql
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
```
在 `clean.sql` 追加：
```
DELETE FROM dispatch_merchant_wallet;
DELETE FROM dispatch_settlement;
```

- [ ] **Step 10: 单测**

`...\src\test\java\com\yy\module\dispatch\service\wallet\DispatchMerchantWalletServiceTest.java`：
```java
package com.yy.module.dispatch.service.wallet;

import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@Import(DispatchMerchantWalletServiceImpl.class)
public class DispatchMerchantWalletServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchMerchantWalletService walletService;

    @Test
    public void testRechargeAndDeduct() {
        walletService.recharge(2001L, 5000);
        assertEquals(5000, walletService.getBalance(2001L));
        assertTrue(walletService.deduct(2001L, 3000));
        assertEquals(2000, walletService.getBalance(2001L));
    }

    @Test
    public void testDeduct_insufficient() {
        walletService.recharge(2001L, 1000);
        assertFalse(walletService.deduct(2001L, 2000));
        assertEquals(1000, walletService.getBalance(2001L));
    }

    @Test
    public void testCredit() {
        walletService.recharge(2001L, 1000);
        walletService.credit(2001L, 500);
        assertEquals(1500, walletService.getBalance(2001L));
    }
}
```

`...\src\test\java\com\yy\module\dispatch\service\settlement\DispatchSettlementServiceTest.java`：
```java
package com.yy.module.dispatch.service.settlement;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.enums.settlement.DispatchSettlementStatusEnum;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.WALLET_INSUFFICIENT_BALANCE;
import static org.junit.jupiter.api.Assertions.*;

@Import({DispatchSettlementServiceImpl.class, DispatchMerchantWalletServiceImpl.class,
        com.yy.module.dispatch.service.order.DispatchOrderServiceImpl.class,
        com.yy.module.dispatch.service.order.DispatchOrderLogServiceImpl.class,
        com.yy.module.dispatch.mq.producer.order.DispatchOrderProducer.class,
        com.yy.module.dispatch.service.merchant.DispatchMerchantServiceImpl.class})
public class DispatchSettlementServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchSettlementService settlementService;
    @Resource
    private DispatchOrderService orderService;
    @Resource
    private DispatchMerchantWalletService walletService;
    @Resource
    private DispatchOrderMapper orderMapper;

    private void createAndComplete(Long merchantId, Integer amount) {
        DispatchOrderCreateReqVO vo = new DispatchOrderCreateReqVO();
        vo.setTitle("结算测试"); vo.setAddress("上海"); vo.setAmount(amount);
        Long id = orderService.createOrder(merchantId, vo);
        orderService.acceptOrder(id, 1001L);
        orderService.startOrder(id, 1001L);
        orderService.finishOrder(id, 1001L);
    }

    @Test
    public void testSettleOrder_success() {
        walletService.recharge(2001L, 5000);
        createAndComplete(2001L, 3000);
        Long orderId = orderMapper.selectList().get(0).getId();
        settlementService.settleOrder(orderId);
        assertEquals(DispatchOrderPayStatusEnum.PAID.getStatus(), orderMapper.selectById(orderId).getPayStatus());
        assertEquals(2000, walletService.getBalance(2001L));
    }

    @Test
    public void testSettleOrder_insufficientBalance() {
        walletService.recharge(2001L, 1000);
        createAndComplete(2001L, 3000);
        Long orderId = orderMapper.selectList().get(0).getId();
        ServiceException ex = assertThrows(ServiceException.class, () -> settlementService.settleOrder(orderId));
        assertEquals(WALLET_INSUFFICIENT_BALANCE.getCode(), ex.getCode());
        // 余额不变
        assertEquals(1000, walletService.getBalance(2001L));
    }

    @Test
    public void testRefundOrder() {
        walletService.recharge(2001L, 5000);
        createAndComplete(2001L, 3000);
        Long orderId = orderMapper.selectList().get(0).getId();
        settlementService.settleOrder(orderId);
        settlementService.refundOrder(orderId);
        assertEquals(DispatchOrderPayStatusEnum.REFUNDED.getStatus(), orderMapper.selectById(orderId).getPayStatus());
        assertEquals(5000, walletService.getBalance(2001L)); // 退款后余额恢复
    }
}
```

- [ ] **Step 11: 运行测试**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am "-Dtest=DispatchMerchantWalletServiceTest,DispatchSettlementServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
Expected: 钱包 3/3 + 结算 3/3 = 6 个测试通过。

- [ ] **Step 12: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 商家钱包+结算记录+结算/退款 Service 与单测"
```

---

### Task 2: 结算流程接入（完成→自动结算 + 退款）+ 单测

**Files:**
- Create: `...\mq\message\order\DispatchOrderCompletedMessage.java`
- Modify: `...\mq\producer\order\DispatchOrderProducer.java`（追加 sendOrderCompleted）
- Create: `...\mq\consumer\settlement\DispatchSettlementConsumer.java`
- Modify: `...\service\order\DispatchOrderServiceImpl.java`（finishOrder 发事件）
- Test: `...\src\test\java\com\yy\module\dispatch\mq\consumer\settlement\DispatchSettlementConsumerTest.java`

- [ ] **Step 1: 完成事件消息**

`...\mq\message\order\DispatchOrderCompletedMessage.java`：
```java
package com.yy.module.dispatch.mq.message.order;

import lombok.Data;

@Data
public class DispatchOrderCompletedMessage {
    private Long orderId;
}
```

- [ ] **Step 2: Producer 追加**

在 `DispatchOrderProducer` 中追加：
```java
    public void sendOrderCompleted(Long orderId) {
        DispatchOrderCompletedMessage message = new DispatchOrderCompletedMessage();
        message.setOrderId(orderId);
        applicationContext.publishEvent(message);
    }
```
> 需补 import `com.yy.module.dispatch.mq.message.order.DispatchOrderCompletedMessage`。

- [ ] **Step 3: Service finishOrder 发事件**

在 `DispatchOrderServiceImpl.finishOrder` 的 `orderLogService.createLog(...)` 之后追加：
```java
        orderProducer.sendOrderCompleted(orderId);
```

- [ ] **Step 4: 结算消费者**

`...\mq\consumer\settlement\DispatchSettlementConsumer.java`：
```java
package com.yy.module.dispatch.mq.consumer.settlement;

import com.yy.module.dispatch.mq.message.order.DispatchOrderCompletedMessage;
import com.yy.module.dispatch.service.settlement.DispatchSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.Resource;

/**
 * 订单完成 → 自动结算（事务提交后执行；失败仅 log，不影响已完成订单）
 */
@Slf4j
@Component
public class DispatchSettlementConsumer {

    @Resource
    private DispatchSettlementService settlementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCompleted(DispatchOrderCompletedMessage message) {
        try {
            settlementService.settleOrder(message.getOrderId());
            log.info("[onOrderCompleted][订单({})结算成功]", message.getOrderId());
        } catch (Exception ex) {
            // 结算失败（如余额不足）不影响已完成订单，记录日志即可
            log.warn("[onOrderCompleted][订单({})结算失败：{}]", message.getOrderId(), ex.getMessage());
        }
    }
}
```

- [ ] **Step 5: 单测（结算消费者）**

`...\src\test\java\com\yy\module\dispatch\mq\consumer\settlement\DispatchSettlementConsumerTest.java`：
```java
package com.yy.module.dispatch.mq.consumer.settlement;

import com.yy.module.dispatch.mq.message.order.DispatchOrderCompletedMessage;
import com.yy.module.dispatch.service.settlement.DispatchSettlementService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class DispatchSettlementConsumerTest {

    @Test
    public void testOnOrderCompleted_success() {
        DispatchSettlementService service = mock(DispatchSettlementService.class);
        DispatchSettlementConsumer consumer = new DispatchSettlementConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "settlementService", service);

        DispatchOrderCompletedMessage message = new DispatchOrderCompletedMessage();
        message.setOrderId(1L);
        consumer.onOrderCompleted(message);

        verify(service).settleOrder(1L);
    }

    @Test
    public void testOnOrderCompleted_failureSwallowed() {
        DispatchSettlementService service = mock(DispatchSettlementService.class);
        doThrow(new RuntimeException("余额不足")).when(service).settleOrder(anyLong());
        DispatchSettlementConsumer consumer = new DispatchSettlementConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "settlementService", service);

        DispatchOrderCompletedMessage message = new DispatchOrderCompletedMessage();
        message.setOrderId(1L);
        // 异常被吞掉，不向上抛
        consumer.onOrderCompleted(message);

        verify(service).settleOrder(1L);
    }
}
```

- [ ] **Step 6: 运行测试**

```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am "-Dtest=DispatchSettlementConsumerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
Expected: 2 个通过。

- [ ] **Step 7: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 订单完成自动结算（事件+消费者）"
```

---

### Task 3: Controller + 集成验证

**Files:**
- Create: `...\controller\admin\settlement\DispatchSettlementController.java`
- Create: `...\controller\admin\settlement\vo\DispatchSettlementRespVO.java`
- Create: `...\controller\admin\wallet\DispatchWalletController.java`
- Create: `...\controller\app\merchant\wallet\AppDispatchWalletController.java`

- [ ] **Step 1: 结算 RespVO + Controller（管理端）**

`...\controller\admin\settlement\vo\DispatchSettlementRespVO.java`：
```java
package com.yy.module.dispatch.controller.admin.settlement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 结算记录 Response VO")
@Data
public class DispatchSettlementRespVO {
    private Long id;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    private Integer amount;
    private Integer status;
    private LocalDateTime settleTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
}
```

`...\controller\admin\settlement\DispatchSettlementController.java`：
```java
package com.yy.module.dispatch.controller.admin.settlement;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.common.util.object.BeanUtils;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementRespVO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;
import com.yy.module.dispatch.service.settlement.DispatchSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 派单结算")
@RestController
@RequestMapping("/dispatch/settlement")
@Validated
public class DispatchSettlementController {

    @Resource
    private DispatchSettlementService settlementService;

    @GetMapping("/page")
    @Operation(summary = "结算分页")
    @PreAuthorize("@ss.hasPermission('dispatch:settlement:query')")
    public CommonResult<PageResult<DispatchSettlementRespVO>> getSettlementPage(@Valid DispatchSettlementPageReqVO reqVO) {
        PageResult<DispatchSettlementDO> page = settlementService.getSettlementPage(reqVO);
        return success(BeanUtils.toBean(page, DispatchSettlementRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "结算详情")
    @Parameter(name = "orderId", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:settlement:query')")
    public CommonResult<DispatchSettlementRespVO> getSettlement(@RequestParam("orderId") Long orderId) {
        DispatchSettlementDO settlement = settlementService.getSettlement(orderId);
        return success(BeanUtils.toBean(settlement, DispatchSettlementRespVO.class));
    }

    @PutMapping("/refund")
    @Operation(summary = "退款")
    @Parameter(name = "orderId", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:settlement:refund')")
    public CommonResult<Boolean> refundOrder(@RequestParam("orderId") Long orderId) {
        settlementService.refundOrder(orderId);
        return success(true);
    }
}
```

- [ ] **Step 2: 管理端钱包 Controller**

`...\controller\admin\wallet\DispatchWalletController.java`：
```java
package com.yy.module.dispatch.controller.admin.wallet;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家钱包")
@RestController
@RequestMapping("/dispatch/wallet")
@Validated
public class DispatchWalletController {

    @Resource
    private DispatchMerchantWalletService walletService;

    @PostMapping("/recharge")
    @Operation(summary = "商家充值")
    @Parameter(name = "merchantId", description = "商家编号", required = true)
    @Parameter(name = "amount", description = "金额（分）", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:wallet:recharge')")
    public CommonResult<Boolean> recharge(@RequestParam("merchantId") Long merchantId,
                                          @RequestParam("amount") Integer amount) {
        walletService.recharge(merchantId, amount);
        return success(true);
    }

    @GetMapping("/balance")
    @Operation(summary = "查询余额")
    @Parameter(name = "merchantId", description = "商家编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:wallet:query')")
    public CommonResult<Integer> getBalance(@RequestParam("merchantId") Long merchantId) {
        return success(walletService.getBalance(merchantId));
    }
}
```

- [ ] **Step 3: 商家端余额 Controller**

`...\controller\app\merchant\wallet\AppDispatchWalletController.java`：
```java
package com.yy.module.dispatch.controller.app.merchant.wallet;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextHolder;
import com.yy.module.dispatch.framework.merchant.core.MerchantIdentity;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "小程序 - 商家钱包")
@RestController
@RequestMapping("/merchant/dispatch/wallet")
@Validated
@MerchantIdentity
public class AppDispatchWalletController {

    @Resource
    private DispatchMerchantWalletService walletService;

    @GetMapping("/balance")
    @Operation(summary = "查询我的余额")
    public CommonResult<Integer> getBalance() {
        return success(walletService.getBalance(MerchantContextHolder.getMerchantId()));
    }
}
```

- [ ] **Step 4: 编译**

```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 结算/钱包/退款管理端与商家端接口"
```

- [ ] **Step 6: 集成验证（控制器直接执行）**

构建→启动→充值→发单→抢单→完成→自动结算→验证钱包扣款+结算记录→退款→验证余额恢复→关停。

> 用 mock 登录（`Bearer test247` 商家 / `Bearer test248` 用户）。
> 充值用管理端 token：`POST /admin-api/dispatch/wallet/recharge?merchantId=1&amount=100000`。
> 发单→抢单→完成后验证：`dispatch_merchant_wallet.balance` 减少、`dispatch_settlement` 有记录、`dispatch_order.pay_status=10`。
> 退款：`PUT /admin-api/dispatch/settlement/refund?orderId=X` → 验证 `pay_status=20`、余额恢复。

---

## 完成标准（Definition of Done）

- `dispatch_merchant_wallet` / `dispatch_settlement` 建表 + H2 同步
- 钱包单测通过（充值/扣款/余额不足/退款入账）；结算单测通过（结算成功/余额不足/退款）
- `finishOrder` 完成后发事件 → 消费者自动结算（失败不阻断已完成订单）
- 管理端：结算分页/详情/退款、钱包充值/余额；商家端：余额
- 端到端：充值→发单→抢单→完成→自动结算→验证扣款→退款→验证恢复
- 全部改动提交到 `feature/yy-backend-framework`

## 已知风险 / 备注

- 用 dispatch 自有商家钱包而非 pay 模块 `pay_wallet`，避免与 pay 模块耦合；后续可接 pay 模块做真实支付/提现。
- 结算失败（余额不足）不阻断已完成订单；管理员可充值后手动重调 `settleOrder`（或补一个重试接口）。
- 用户端收益暂无独立钱包（用户余额=其结算记录 SUM）；如需提现，后续加 `dispatch_user_wallet` + 提现流程。
