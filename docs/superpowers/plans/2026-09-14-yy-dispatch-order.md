# yy 派单订单 + 抢单 Implementation Plan（Plan 2b）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `yy-module-dispatch` 中实现派单订单领域：订单/日志表、状态机（待接单→已接单→服务中→已完成/已取消）、**广播抢单（数据库条件更新保证原子）**、以及用户端/商家端/管理端三组 Controller。

**Architecture:** 沿用 yudao 分层（Controller / Service / Mapper / DO / Convert / VO / Enums / ErrorCode）。抢单用 `UPDATE ... WHERE status=待接单 AND user_id IS NULL` 的条件更新作为唯一原子防线（影响行数=1 即抢到）。所有身份来自登录态/`MerchantContextHolder`，不信任前端传参。订单每次状态流转写一条 `dispatch_order_log`。

**Tech Stack:** JDK 17 · Spring Boot 3.5.15 · MyBatis Plus（`BaseMapperX`/`LambdaUpdateWrapper`）· MapStruct · MySQL 8（库 `yy`）· 已完成 Plan 2a 的 `yy-module-dispatch`

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`（第 5 节 派单核心模型）

## Global Constraints

- 包名 `com.yy`；模块 `yy-module-dispatch`（Plan 2a 已建，`D:\IDEA\yy\yy-module-dispatch\`），Java 根包 `com.yy.module.dispatch`。
- 数据库 MySQL 8，库 `yy`，root/123456；**无多租户**，DO 继承 `com.yy.framework.mybatis.core.dataobject.BaseDO`。
- 错误码段：订单用 **`1-099-002-xxx`**（商家已用 `1-099-001-xxx`，见 `enums/ErrorCodeConstants.java`）。
- **表名沿用 Plan 2a 的 `dispatch_` 前缀**：`dispatch_order`、`dispatch_order_log`（spec 写的 `yy_dispatch_order` 为示意，以本计划为准）。
- 金额字段 `amount` 用 **`Integer`（单位：分）**，沿用 yudao 惯例。
- 订单状态（`DispatchOrderStatusEnum`）：`待接单(0) 已接单(10) 服务中(20) 已完成(30) 已取消(40)`。
- 结算状态（`DispatchOrderPayStatusEnum`）：`待结算(0) 已结算(10) 已退款(20)`。
- 抢单**必须**用条件更新（`dispatch_order` 的 `status=0 AND user_id IS NULL`），不得用「先查后改」。
- 身份来源：用户端取 `SecurityFrameworkUtils.getLoginUserId()`；商家端取 `MerchantContextHolder.getMerchantId()`（由 Plan 2a 的拦截器写入）。
- 命令在 Windows PowerShell 5.1；MySQL 导入加 `--default-character-set=utf8mb4`；JSON 请求用 `Invoke-RestMethod`。
- 参考样板（Plan 2a 同模块内）：`service/merchant/DispatchMerchantService(Impl).java`、`dal/mysql/merchant/DispatchMerchantMapper.java`、`controller/admin/merchant/DispatchMerchantController.java`、`framework/merchant/core/MerchantContextHolder.java`。

---

### Task 1: 订单领域（枚举 / 错误码 / DO / Mapper / Service / 日志）+ 单测

**Files:**
- Modify: `...\enums\ErrorCodeConstants.java`（追加订单错误码）
- Create: `...\enums\order\DispatchOrderStatusEnum.java`
- Create: `...\enums\order\DispatchOrderPayStatusEnum.java`
- Create: `...\enums\order\DispatchOrderOperateTypeEnum.java`
- Create: `...\dal\dataobject\order\DispatchOrderDO.java`
- Create: `...\dal\dataobject\order\DispatchOrderLogDO.java`
- Create: `...\dal\mysql\order\DispatchOrderMapper.java`
- Create: `...\dal\mysql\order\DispatchOrderLogMapper.java`
- Create: `...\service\order\DispatchOrderService.java`
- Create: `...\service\order\DispatchOrderServiceImpl.java`
- Create: `...\service\order\DispatchOrderLogService.java`
- Create: `...\service\order\DispatchOrderLogServiceImpl.java`
- Create: `...\controller\admin\order\vo\DispatchOrderPageReqVO.java`
- Create: `...\controller\admin\order\vo\DispatchOrderCreateReqVO.java`
- Create: `D:\IDEA\yy\sql\module\dispatch_order.sql`
- Test: `...\src\test\java\com\yy\module\dispatch\service\order\DispatchOrderServiceTest.java`
- Modify: `...\src\test\resources\sql\create_tables.sql`（追加两张表）

**Interfaces:**
- Consumes: Plan 2a 的 `DispatchMerchantService`、`MerchantContextHolder`、`ErrorCodeConstants`
- Produces:
  - `DispatchOrderService.createOrder(Long merchantId, DispatchOrderCreateReqVO)→Long`
  - `acceptOrder(Long orderId, Long userId)`（抢单，条件更新）
  - `startOrder(Long orderId, Long userId)`
  - `finishOrder(Long orderId, Long userId)`
  - `cancelOrder(Long orderId, Long merchantId, String reason)`
  - `cancelOrderByAdmin(Long orderId, String reason)`
  - `getOrder(Long orderId)→DO`
  - `getHallPage(DispatchOrderPageReqVO)→PageResult<DO>`（status=待接单）
  - `getUserOrderPage(Long userId, DispatchOrderPageReqVO)→PageResult<DO>`
  - `getMerchantOrderPage(Long merchantId, DispatchOrderPageReqVO)→PageResult<DO>`
  - `getAdminOrderPage(DispatchOrderPageReqVO)→PageResult<DO>`
  - `DispatchOrderLogService.createLog(Long orderId, DispatchOrderOperateTypeEnum, String)`、`getLogList(Long orderId)`

- [ ] **Step 1: 建表 SQL**

创建 `D:\IDEA\yy\sql\module\dispatch_order.sql`：
```sql
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
```

导入：
```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 yy -e "source D:/IDEA/yy/sql/module/dispatch_order.sql"
mysql -uroot -p123456 yy -e "SHOW TABLES LIKE 'dispatch_order'; SHOW TABLES LIKE 'dispatch_order_log';"
```
Expected: 各返回 1 行。

- [ ] **Step 2: 错误码（追加到 `enums/ErrorCodeConstants.java`）**

在 `MERCHANT_STATUS_ERROR` 之后、类结束 `}` 之前追加：
```java

    // ========== 订单 1-099-002-000 ==========
    ErrorCode ORDER_NOT_EXISTS = new ErrorCode(1_099_002_000, "派单订单不存在");
    ErrorCode ORDER_STATUS_ERROR = new ErrorCode(1_099_002_001, "订单当前状态不允许该操作");
    ErrorCode ORDER_ALREADY_ACCEPTED = new ErrorCode(1_099_002_002, "订单已被接单");
    ErrorCode ORDER_NOT_MERCHANT_OWNER = new ErrorCode(1_099_002_003, "该订单不属于当前商家");
    ErrorCode ORDER_NOT_USER_OWNER = new ErrorCode(1_099_002_004, "该订单不属于当前用户");
```

- [ ] **Step 3: 订单状态枚举**

`...\enums\order\DispatchOrderStatusEnum.java`：
```java
package com.yy.module.dispatch.enums.order;

import cn.hutool.core.util.ObjectUtil;
import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 派单订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchOrderStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待接单"),
    ACCEPTED(10, "已接单"),
    SERVING(20, "服务中"),
    COMPLETED(30, "已完成"),
    CANCELED(40, "已取消");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchOrderStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isPending(Integer status) {
        return ObjectUtil.equal(PENDING.getStatus(), status);
    }

    public static boolean isAccepted(Integer status) {
        return ObjectUtil.equal(ACCEPTED.getStatus(), status);
    }

    public static boolean isServing(Integer status) {
        return ObjectUtil.equal(SERVING.getStatus(), status);
    }

    public static boolean isCompleted(Integer status) {
        return ObjectUtil.equal(COMPLETED.getStatus(), status);
    }

    public static boolean isCanceled(Integer status) {
        return ObjectUtil.equal(CANCELED.getStatus(), status);
    }
}
```

- [ ] **Step 4: 结算状态枚举 + 操作类型枚举**

`...\enums\order\DispatchOrderPayStatusEnum.java`：
```java
package com.yy.module.dispatch.enums.order;

import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 派单订单结算状态枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchOrderPayStatusEnum implements ArrayValuable<Integer> {

    UNPAID(0, "待结算"),
    PAID(10, "已结算"),
    REFUNDED(20, "已退款");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchOrderPayStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
```

`...\enums\order\DispatchOrderOperateTypeEnum.java`：
```java
package com.yy.module.dispatch.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 派单订单操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchOrderOperateTypeEnum {

    MERCHANT_CREATE(1, "商家发单"),
    USER_ACCEPT(10, "用户接单"),
    USER_START(20, "用户开始服务"),
    USER_FINISH(30, "用户完成服务"),
    MERCHANT_CANCEL(40, "商家取消订单"),
    SYSTEM_CANCEL(41, "系统自动取消订单"),
    ADMIN_CANCEL(42, "管理员取消订单");

    private final Integer type;
    private final String content;
}
```

- [ ] **Step 5: DO（订单 + 日志）**

`...\dal\dataobject\order\DispatchOrderDO.java`：
```java
package com.yy.module.dispatch.dal.dataobject.order;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 派单订单 DO
 */
@TableName("dispatch_order")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchOrderDO extends BaseDO {

    @TableId
    private Long id;
    /** 订单号 */
    private String no;
    /** 发单商家编号 */
    private Long merchantId;
    /** 接单用户编号（抢单前为 null） */
    private Long userId;
    /** 订单状态，见 DispatchOrderStatusEnum */
    private Integer status;
    /** 结算状态，见 DispatchOrderPayStatusEnum */
    private Integer payStatus;
    /** 服务标题 */
    private String title;
    /** 服务描述 */
    private String description;
    /** 服务图片（逗号分隔） */
    private String images;
    /** 服务地址 */
    private String address;
    /** 联系人 */
    private String contactName;
    /** 联系电话 */
    private String contactMobile;
    /** 酬劳金额（分） */
    private Integer amount;
    /** 接单截止时间 */
    private LocalDateTime deadline;
    /** 接单时间 */
    private LocalDateTime acceptTime;
    /** 开始服务时间 */
    private LocalDateTime startTime;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 取消原因 */
    private String cancelReason;

}
```

`...\dal\dataobject\order\DispatchOrderLogDO.java`：
```java
package com.yy.module.dispatch.dal.dataobject.order;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 派单订单操作日志 DO
 */
@TableName("dispatch_order_log")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchOrderLogDO extends BaseDO {

    @TableId
    private Long id;
    /** 订单编号 */
    private Long orderId;
    /** 操作类型，见 DispatchOrderOperateTypeEnum */
    private Integer operateType;
    /** 操作内容 */
    private String content;

}
```

- [ ] **Step 6: Mapper（订单含抢单条件更新）**

`...\dal\mysql\order\DispatchOrderMapper.java`：
```java
package com.yy.module.dispatch.dal.mysql.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface DispatchOrderMapper extends BaseMapperX<DispatchOrderDO> {

    /**
     * 抢单：条件更新，原子性由 WHERE 保证
     *
     * @return 影响行数；1 表示抢单成功，0 表示已被抢走/状态不对
     */
    default int updateAccept(Long id, Long userId) {
        return update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getUserId, userId)
                .set(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.ACCEPTED.getStatus())
                .set(DispatchOrderDO::getAcceptTime, LocalDateTime.now())
                .eq(DispatchOrderDO::getId, id)
                .eq(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.PENDING.getStatus())
                .isNull(DispatchOrderDO::getUserId));
    }

    default PageResult<DispatchOrderDO> selectPage(DispatchOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DispatchOrderDO>()
                .eqIfPresent(DispatchOrderDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(DispatchOrderDO::getUserId, reqVO.getUserId())
                .eqIfPresent(DispatchOrderDO::getStatus, reqVO.getStatus())
                .likeIfPresent(DispatchOrderDO::getTitle, reqVO.getTitle())
                .betweenIfPresent(DispatchOrderDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DispatchOrderDO::getId));
    }
}
```

`...\dal\mysql\order\DispatchOrderLogMapper.java`：
```java
package com.yy.module.dispatch.dal.mysql.order;

import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DispatchOrderLogMapper extends BaseMapperX<DispatchOrderLogDO> {

    default List<DispatchOrderLogDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<DispatchOrderLogDO>()
                .eq(DispatchOrderLogDO::getOrderId, orderId)
                .orderByAsc(DispatchOrderLogDO::getId));
    }
}
```

- [ ] **Step 7: VO（分页 + 创建）**

`...\controller\admin\order\vo\DispatchOrderPageReqVO.java`：
```java
package com.yy.module.dispatch.controller.admin.order.vo;

import com.yy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 派单订单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DispatchOrderPageReqVO extends PageParam {

    @Schema(description = "商家编号", example = "2001")
    private Long merchantId;

    @Schema(description = "用户编号", example = "1001")
    private Long userId;

    @Schema(description = "订单状态", example = "0")
    private Integer status;

    @Schema(description = "服务标题", example = "跑腿")
    private String title;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;
}
```

`...\controller\admin\order\vo\DispatchOrderCreateReqVO.java`：
```java
package com.yy.module.dispatch.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "商家 - 发单 Request VO")
@Data
public class DispatchOrderCreateReqVO {

    @Schema(description = "服务标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "帮买一杯咖啡")
    @NotEmpty(message = "服务标题不能为空")
    private String title;

    @Schema(description = "服务描述")
    private String description;

    @Schema(description = "服务图片（逗号分隔）")
    private String images;

    @Schema(description = "服务地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "服务地址不能为空")
    private String address;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactMobile;

    @Schema(description = "酬劳金额（分）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "酬劳金额不能为空")
    private Integer amount;

    @Schema(description = "接单截止时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;
}
```

- [ ] **Step 8: Service 接口 + 实现（含抢单）**

`...\service\order\DispatchOrderService.java`：
```java
package com.yy.module.dispatch.service.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;

public interface DispatchOrderService {

    Long createOrder(Long merchantId, DispatchOrderCreateReqVO reqVO);

    void acceptOrder(Long orderId, Long userId);

    void startOrder(Long orderId, Long userId);

    void finishOrder(Long orderId, Long userId);

    void cancelOrder(Long orderId, Long merchantId, String reason);

    void cancelOrderByAdmin(Long orderId, String reason);

    DispatchOrderDO getOrder(Long orderId);

    PageResult<DispatchOrderDO> getHallPage(DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getUserOrderPage(Long userId, DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getMerchantOrderPage(Long merchantId, DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getAdminOrderPage(DispatchOrderPageReqVO reqVO);

}
```

`...\service\order\DispatchOrderServiceImpl.java`：
```java
package com.yy.module.dispatch.service.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DispatchOrderServiceImpl implements DispatchOrderService {

    @Resource
    private DispatchOrderMapper orderMapper;
    @Resource
    private DispatchOrderLogService orderLogService;

    @Override
    public Long createOrder(Long merchantId, DispatchOrderCreateReqVO reqVO) {
        DispatchOrderDO order = DispatchOrderDO.builder()
                .no(generateNo())
                .merchantId(merchantId)
                .status(DispatchOrderStatusEnum.PENDING.getStatus())
                .payStatus(DispatchOrderPayStatusEnum.UNPAID.getStatus())
                .title(reqVO.getTitle())
                .description(reqVO.getDescription())
                .images(reqVO.getImages())
                .address(reqVO.getAddress())
                .contactName(reqVO.getContactName())
                .contactMobile(reqVO.getContactMobile())
                .amount(reqVO.getAmount())
                .deadline(reqVO.getDeadline())
                .build();
        orderMapper.insert(order);
        orderLogService.createLog(order.getId(), DispatchOrderOperateTypeEnum.MERCHANT_CREATE,
                DispatchOrderOperateTypeEnum.MERCHANT_CREATE.getContent());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOrder(Long orderId, Long userId) {
        validateOrderExists(orderId);
        // 抢单：条件更新保证原子
        int rows = orderMapper.updateAccept(orderId, userId);
        if (rows == 0) {
            throw exception(ORDER_ALREADY_ACCEPTED);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_ACCEPT,
                DispatchOrderOperateTypeEnum.USER_ACCEPT.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderOwnerByUser(orderId, userId);
        if (!DispatchOrderStatusEnum.isAccepted(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.SERVING.getStatus())
                .startTime(LocalDateTime.now()).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_START,
                DispatchOrderOperateTypeEnum.USER_START.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderOwnerByUser(orderId, userId);
        if (!DispatchOrderStatusEnum.isServing(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.COMPLETED.getStatus())
                .finishTime(LocalDateTime.now()).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_FINISH,
                DispatchOrderOperateTypeEnum.USER_FINISH.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long merchantId, String reason) {
        DispatchOrderDO order = validateOrderOwnerByMerchant(orderId, merchantId);
        // 仅待接单可取消（已接单后的取消走后续退款流程，暂不允许）
        if (!DispatchOrderStatusEnum.isPending(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.CANCELED.getStatus())
                .cancelReason(reason).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.MERCHANT_CANCEL, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByAdmin(Long orderId, String reason) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (DispatchOrderStatusEnum.isCompleted(order.getStatus()) || DispatchOrderStatusEnum.isCanceled(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.CANCELED.getStatus())
                .cancelReason(reason).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.ADMIN_CANCEL, reason);
    }

    @Override
    public DispatchOrderDO getOrder(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    @Override
    public PageResult<DispatchOrderDO> getHallPage(DispatchOrderPageReqVO reqVO) {
        reqVO.setStatus(DispatchOrderStatusEnum.PENDING.getStatus());
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<DispatchOrderDO> getUserOrderPage(Long userId, DispatchOrderPageReqVO reqVO) {
        reqVO.setUserId(userId);
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<DispatchOrderDO> getMerchantOrderPage(Long merchantId, DispatchOrderPageReqVO reqVO) {
        reqVO.setMerchantId(merchantId);
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<DispatchOrderDO> getAdminOrderPage(DispatchOrderPageReqVO reqVO) {
        return orderMapper.selectPage(reqVO);
    }

    // ========== 校验 ==========

    private DispatchOrderDO validateOrderExists(Long orderId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        return order;
    }

    private DispatchOrderDO validateOrderOwnerByUser(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (!java.util.Objects.equals(order.getUserId(), userId)) {
            throw exception(ORDER_NOT_USER_OWNER);
        }
        return order;
    }

    private DispatchOrderDO validateOrderOwnerByMerchant(Long orderId, Long merchantId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (!java.util.Objects.equals(order.getMerchantId(), merchantId)) {
            throw exception(ORDER_NOT_MERCHANT_OWNER);
        }
        return order;
    }

    private String generateNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
```

- [ ] **Step 9: 日志 Service**

`...\service\order\DispatchOrderLogService.java`：
```java
package com.yy.module.dispatch.service.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderLogDO;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;

import java.util.List;

public interface DispatchOrderLogService {

    void createLog(Long orderId, DispatchOrderOperateTypeEnum operateType, String content);

    List<DispatchOrderLogDO> getLogList(Long orderId);

}
```

`...\service\order\DispatchOrderLogServiceImpl.java`：
```java
package com.yy.module.dispatch.service.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderLogDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderLogMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

@Service
@Validated
public class DispatchOrderLogServiceImpl implements DispatchOrderLogService {

    @Resource
    private DispatchOrderLogMapper orderLogMapper;

    @Override
    public void createLog(Long orderId, DispatchOrderOperateTypeEnum operateType, String content) {
        DispatchOrderLogDO log = DispatchOrderLogDO.builder()
                .orderId(orderId)
                .operateType(operateType.getType())
                .content(content)
                .build();
        orderLogMapper.insert(log);
    }

    @Override
    public List<DispatchOrderLogDO> getLogList(Long orderId) {
        return orderLogMapper.selectListByOrderId(orderId);
    }
}
```

- [ ] **Step 10: H2 测试表结构（追加到 `create_tables.sql`）**

在 `yy-module-dispatch\src\test\resources\sql\create_tables.sql` 末尾追加：
```sql
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
```
并在 `src\test\resources\sql\clean.sql` 追加两行（若该文件按行写表名）：
```
DELETE FROM dispatch_order;
DELETE FROM dispatch_order_log;
```

- [ ] **Step 11: 单元测试（含抢单原子性）**

`...\src\test\java\com\yy\module\dispatch\service\order\DispatchOrderServiceTest.java`：
```java
package com.yy.module.dispatch.service.order;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.ORDER_ALREADY_ACCEPTED;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.ORDER_STATUS_ERROR;
import static org.junit.jupiter.api.Assertions.*;

@Import({DispatchOrderServiceImpl.class, DispatchOrderLogServiceImpl.class})
public class DispatchOrderServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchOrderService orderService;
    @Resource
    private DispatchOrderMapper orderMapper;

    private DispatchOrderCreateReqVO buildReqVO() {
        DispatchOrderCreateReqVO vo = new DispatchOrderCreateReqVO();
        vo.setTitle("帮买咖啡");
        vo.setAddress("上海市浦东新区");
        vo.setAmount(1000);
        return vo;
    }

    @Test
    public void testCreateOrder() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        DispatchOrderDO order = orderMapper.selectById(id);
        assertEquals(DispatchOrderStatusEnum.PENDING.getStatus(), order.getStatus());
        assertEquals(2001L, order.getMerchantId());
        assertNotNull(order.getNo());
    }

    @Test
    public void testAcceptOrder_success() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        DispatchOrderDO order = orderMapper.selectById(id);
        assertEquals(DispatchOrderStatusEnum.ACCEPTED.getStatus(), order.getStatus());
        assertEquals(1001L, order.getUserId());
    }

    @Test
    public void testAcceptOrder_alreadyAccepted() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        // 第二个人抢同一单 -> 条件更新影响行数为 0
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.acceptOrder(id, 1002L));
        assertEquals(ORDER_ALREADY_ACCEPTED.getCode(), ex.getCode());
        // 仍然是第一个用户
        assertEquals(1001L, orderMapper.selectById(id).getUserId());
    }

    @Test
    public void testStartAndFinishOrder() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        orderService.startOrder(id, 1001L);
        assertEquals(DispatchOrderStatusEnum.SERVING.getStatus(), orderMapper.selectById(id).getStatus());
        orderService.finishOrder(id, 1001L);
        assertEquals(DispatchOrderStatusEnum.COMPLETED.getStatus(), orderMapper.selectById(id).getStatus());
    }

    @Test
    public void testStartOrder_wrongStatus() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        // 未接单就 start -> 状态错误
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.startOrder(id, 1001L));
        assertEquals(ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }
}
```

- [ ] **Step 12: 运行测试**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am "-Dtest=DispatchOrderServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
Expected: 5 个测试全部通过（重点：`testAcceptOrder_alreadyAccepted` 证明抢单原子性）。

- [ ] **Step 13: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 派单订单领域（状态机/抢单条件更新/日志）与单测"
```

---

### Task 2: 用户端 Controller（大厅 / 抢单 / 我的接单 / 开始 / 完成 / 详情）

**Files:**
- Create: `...\controller\app\order\AppDispatchOrderController.java`
- Create: `...\controller\app\order\vo\AppDispatchOrderRespVO.java`
- Create: `...\convert\order\DispatchOrderConvert.java`

**Interfaces:**
- Consumes: Task 1 的 `DispatchOrderService`、`DispatchOrderLogService`、`DispatchOrderStatusEnum`；`SecurityFrameworkUtils`
- Produces: `/app-api/dispatch/order/{hall-page,accept,my-page,start,finish,get}`

- [ ] **Step 1: Convert + RespVO**

`...\convert\order\DispatchOrderConvert.java`：
```java
package com.yy.module.dispatch.convert.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.app.order.vo.AppDispatchOrderRespVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface DispatchOrderConvert {

    DispatchOrderConvert INSTANCE = Mappers.getMapper(DispatchOrderConvert.class);

    AppDispatchOrderRespVO convert(DispatchOrderDO bean);

    List<AppDispatchOrderRespVO> convertList(List<DispatchOrderDO> list);

    PageResult<AppDispatchOrderRespVO> convertPage(PageResult<DispatchOrderDO> page);
}
```

`...\controller\app\order\vo\AppDispatchOrderRespVO.java`：
```java
package com.yy.module.dispatch.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "小程序 - 派单订单 Response VO")
@Data
public class AppDispatchOrderRespVO {

    @Schema(description = "订单编号")
    private Long id;
    @Schema(description = "订单号")
    private String no;
    @Schema(description = "发单商家编号")
    private Long merchantId;
    @Schema(description = "接单用户编号")
    private Long userId;
    @Schema(description = "订单状态")
    private Integer status;
    @Schema(description = "结算状态")
    private Integer payStatus;
    @Schema(description = "服务标题")
    private String title;
    @Schema(description = "服务描述")
    private String description;
    @Schema(description = "服务图片")
    private String images;
    @Schema(description = "服务地址")
    private String address;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系电话")
    private String contactMobile;
    @Schema(description = "酬劳金额（分）")
    private Integer amount;
    @Schema(description = "接单截止时间")
    private LocalDateTime deadline;
    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;
    @Schema(description = "开始服务时间")
    private LocalDateTime startTime;
    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
    @Schema(description = "取消原因")
    private String cancelReason;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
```

- [ ] **Step 2: 用户端 Controller**

`...\controller\app\order\AppDispatchOrderController.java`：
```java
package com.yy.module.dispatch.controller.app.order;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.controller.app.order.vo.AppDispatchOrderRespVO;
import com.yy.module.dispatch.convert.order.DispatchOrderConvert;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "小程序 - 派单订单（用户端）")
@RestController
@RequestMapping("/dispatch/order")
@Validated
public class AppDispatchOrderController {

    @Resource
    private DispatchOrderService orderService;

    @GetMapping("/hall-page")
    @Operation(summary = "抢单大厅分页（待接单）")
    public CommonResult<PageResult<AppDispatchOrderRespVO>> getHallPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getHallPage(reqVO);
        return success(DispatchOrderConvert.INSTANCE.convertPage(page));
    }

    @PostMapping("/accept")
    @Operation(summary = "抢单")
    @Parameter(name = "id", description = "订单编号", required = true, example = "1")
    public CommonResult<Boolean> acceptOrder(@RequestParam("id") Long id) {
        orderService.acceptOrder(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/my-page")
    @Operation(summary = "我的接单分页")
    public CommonResult<PageResult<AppDispatchOrderRespVO>> getMyPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getUserOrderPage(SecurityFrameworkUtils.getLoginUserId(), reqVO);
        return success(DispatchOrderConvert.INSTANCE.convertPage(page));
    }

    @PutMapping("/start")
    @Operation(summary = "开始服务")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> startOrder(@RequestParam("id") Long id) {
        orderService.startOrder(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成服务")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> finishOrder(@RequestParam("id") Long id) {
        orderService.finishOrder(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得订单详情")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<AppDispatchOrderRespVO> getOrder(@RequestParam("id") Long id) {
        DispatchOrderDO order = orderService.getOrder(id);
        return success(DispatchOrderConvert.INSTANCE.convert(order));
    }
}
```

- [ ] **Step 3: 编译**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 4: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 用户端派单订单接口（大厅/抢单/开始/完成）"
```

---

### Task 3: 商家端 Controller（发单 / 我的发单 / 取消 / 详情）

**Files:**
- Create: `...\controller\app\merchant\order\AppDispatchMerchantOrderController.java`

**Interfaces:**
- Consumes: Task 1 的 Service、`MerchantContextHolder`、`@MerchantIdentity`、`DispatchOrderCreateReqVO`
- Produces: `/app-api/merchant/dispatch/order/{create,my-page,cancel,get}`（类级 `@MerchantIdentity`）

- [ ] **Step 1: 商家端 Controller**

`...\controller\app\merchant\order\AppDispatchMerchantOrderController.java`：
```java
package com.yy.module.dispatch.controller.app.merchant.order;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.controller.app.order.vo.AppDispatchOrderRespVO;
import com.yy.module.dispatch.convert.order.DispatchOrderConvert;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextHolder;
import com.yy.module.dispatch.framework.merchant.core.MerchantIdentity;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "小程序 - 派单订单（商家端）")
@RestController
@RequestMapping("/merchant/dispatch/order")
@Validated
@MerchantIdentity
public class AppDispatchMerchantOrderController {

    @Resource
    private DispatchOrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "商家发单")
    public CommonResult<Long> createOrder(@Valid @RequestBody DispatchOrderCreateReqVO reqVO) {
        return success(orderService.createOrder(MerchantContextHolder.getMerchantId(), reqVO));
    }

    @GetMapping("/my-page")
    @Operation(summary = "我的发单分页")
    public CommonResult<PageResult<AppDispatchOrderRespVO>> getMyPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getMerchantOrderPage(MerchantContextHolder.getMerchantId(), reqVO);
        return success(DispatchOrderConvert.INSTANCE.convertPage(page));
    }

    @PutMapping("/cancel")
    @Operation(summary = "商家取消订单")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id,
                                             @RequestParam(value = "reason", required = false) String reason) {
        orderService.cancelOrder(id, MerchantContextHolder.getMerchantId(), reason);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得订单详情（商家端）")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<AppDispatchOrderRespVO> getOrder(@RequestParam("id") Long id) {
        DispatchOrderDO order = orderService.getOrder(id);
        return success(DispatchOrderConvert.INSTANCE.convert(order));
    }
}
```
> 说明：类级 `@MerchantIdentity` 由 Plan 2a 的拦截器识别（`HandlerMethod` 存的是用户类，CGLIB 代理安全）。

- [ ] **Step 2: 编译**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 商家端派单订单接口（发单/取消/我的发单）"
```

---

### Task 4: 管理端 Controller（分页 / 详情 / 强制取消）

**Files:**
- Create: `...\controller\admin\order\DispatchOrderController.java`
- Create: `...\controller\admin\order\vo\DispatchOrderRespVO.java`

**Interfaces:**
- Consumes: Task 1 的 Service
- Produces: `/admin-api/dispatch/order/{page,get,cancel}`

- [ ] **Step 1: RespVO + Controller**

`...\controller\admin\order\vo\DispatchOrderRespVO.java`：
```java
package com.yy.module.dispatch.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 派单订单 Response VO")
@Data
public class DispatchOrderRespVO {

    @Schema(description = "订单编号")
    private Long id;
    @Schema(description = "订单号")
    private String no;
    @Schema(description = "发单商家编号")
    private Long merchantId;
    @Schema(description = "接单用户编号")
    private Long userId;
    @Schema(description = "订单状态")
    private Integer status;
    @Schema(description = "结算状态")
    private Integer payStatus;
    @Schema(description = "服务标题")
    private String title;
    @Schema(description = "服务地址")
    private String address;
    @Schema(description = "酬劳金额（分）")
    private Integer amount;
    @Schema(description = "接单截止时间")
    private LocalDateTime deadline;
    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;
    @Schema(description = "开始服务时间")
    private LocalDateTime startTime;
    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
    @Schema(description = "取消原因")
    private String cancelReason;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
```

`...\controller\admin\order\DispatchOrderController.java`：
```java
package com.yy.module.dispatch.controller.admin.order;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderRespVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 派单订单")
@RestController
@RequestMapping("/dispatch/order")
@Validated
public class DispatchOrderController {

    @Resource
    private DispatchOrderService orderService;

    @GetMapping("/page")
    @Operation(summary = "获得派单订单分页")
    @PreAuthorize("@ss.hasPermission('dispatch:order:query')")
    public CommonResult<PageResult<DispatchOrderRespVO>> getOrderPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getAdminOrderPage(reqVO);
        PageResult<DispatchOrderRespVO> result = new PageResult<>();
        result.setList(page.getList().stream().map(o -> {
            DispatchOrderRespVO vo = new DispatchOrderRespVO();
            BeanUtils.copyProperties(o, vo);
            return vo;
        }).toList());
        result.setTotal(page.getTotal());
        return success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "获得派单订单详情")
    @Parameter(name = "id", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:order:query')")
    public CommonResult<DispatchOrderRespVO> getOrder(@RequestParam("id") Long id) {
        DispatchOrderDO order = orderService.getOrder(id);
        DispatchOrderRespVO vo = new DispatchOrderRespVO();
        BeanUtils.copyProperties(order, vo);
        return success(vo);
    }

    @PutMapping("/cancel")
    @Operation(summary = "管理员强制取消订单")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id,
                                             @RequestParam(value = "reason", required = false) String reason) {
        orderService.cancelOrderByAdmin(id, reason);
        return success(true);
    }
}
```

- [ ] **Step 2: 编译**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 管理端派单订单接口（分页/详情/强制取消）"
```

---

### Task 5: 集成验证（发单 → 抢单 → 开始 → 完成，端到端）

**Files:**
- Verify: `D:\IDEA\yy` 全工程

**Interfaces:**
- Consumes: Task 1–4 全部
- Produces: 端到端可验证的派单主流程

- [ ] **Step 1: 全量构建**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 2: 启动服务器（后台）**

Run:
```powershell
$log="$env:TEMP\yy-server.log"; Remove-Item $log -ErrorAction SilentlyContinue
$p = Start-Process -FilePath "java" -ArgumentList "-jar","D:\IDEA\yy\yy-server\target\yy-server.jar","--spring.profiles.active=local" -RedirectStandardOutput $log -RedirectStandardError "$env:TEMP\yy-server.err.log" -PassThru -WindowStyle Hidden
$p.Id | Set-Content "$env:TEMP\yy-server.pid"
$ok=$false; for ($i=0; $i -lt 25; $i++) { Start-Sleep -Seconds 3; if (Select-String -Path $log -SimpleMatch "Started YyServerApplication" -Quiet -ErrorAction SilentlyContinue) { $ok=$true; break } }
"started=$ok"
```
Expected: `started=True`。（不要用 `$pid` 变量名。）

- [ ] **Step 3: 准备一个已审核商家 + 一个会员 token**

需要：① 一个 `member_user` 登录拿到会员 token（可用短信 mock 或直接 SQL 造数据）；② 一条 `dispatch_merchant`（status=1，绑定该 member_user_id）。
> 简化：直接用 SQL 插入一条已审核商家，会员 token 通过 member 的 mock 登录接口获取。若 mock 短信不可用，可先用 admin token 验证管理端，商家/用户端用 SQL 造数 + 直连 service 的方式做最小验证（记录实际所用路径）。

- [ ] **Step 4: 商家发单（商家端）**

用商家 token（会员登录后，绑定商家的 member 的 token）：
```powershell
$orderId = (Invoke-RestMethod -Uri "http://127.0.0.1:48080/app-api/merchant/dispatch/order/create" -Method POST -ContentType "application/json" -Headers @{ "Authorization"="Bearer $merchantToken" } -Body '{"title":"帮买咖啡","address":"上海","amount":1000}').data
"orderId=$orderId"
```
Expected: 返回非空 orderId。

- [ ] **Step 5: 用户抢单 + 开始 + 完成**

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:48080/app-api/dispatch/order/accept?id=$orderId" -Method POST -Headers @{ "Authorization"="Bearer $userToken" } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "http://127.0.0.1:48080/app-api/dispatch/order/start?id=$orderId" -Method PUT -Headers @{ "Authorization"="Bearer $userToken" } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "http://127.0.0.1:48080/app-api/dispatch/order/finish?id=$orderId" -Method PUT -Headers @{ "Authorization"="Bearer $userToken" } | ConvertTo-Json -Compress
```
Expected: 三步均 `code=0`。

- [ ] **Step 6: 校验最终状态 + 日志**

```powershell
mysql -uroot -p123456 yy -e "SELECT id,status,user_id FROM dispatch_order WHERE id=$orderId;"
mysql -uroot -p123456 yy -e "SELECT operate_type,content FROM dispatch_order_log WHERE order_id=$orderId ORDER BY id;"
```
Expected: status=30（已完成）；日志含 发单/接单/开始/完成 四条。

- [ ] **Step 7: 关停服务器**

```powershell
$srvPid = Get-Content "$env:TEMP\yy-server.pid" -ErrorAction SilentlyContinue
if ($srvPid) { Stop-Process -Id $srvPid -Force -ErrorAction SilentlyContinue }
Get-NetTCPConnection -LocalPort 48080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
```

- [ ] **Step 8: 提交（如有）

```powershell
git -C "D:\IDEA\yy" status --short
```

---

## 完成标准（Definition of Done）

- `dispatch_order` / `dispatch_order_log` 建表完成，H2 测试表同步
- `DispatchOrderService` 单测通过，**抢单原子性测试**（`testAcceptOrder_alreadyAccepted`）证明条件更新生效
- 三组 Controller 可用：用户端 `/app-api/dispatch/order/**`、商家端 `/app-api/merchant/dispatch/order/**`、管理端 `/admin-api/dispatch/order/**`
- 端到端：发单 → 抢单 → 开始 → 完成，最终 status=30 且有完整操作日志
- 全部改动提交到 `feature/yy-backend-framework`

## 已知风险 / 备注

- 端到端验证需要「已审核商家」的会员 token；若 mock 短信登录不便，允许用 SQL 造数 + 直连 service 做最小验证，但须在报告中说明实际路径。
- 商家端「已接单后取消」暂不允许（返回 `ORDER_STATUS_ERROR`）；退款/取消已接单订单属后续结算计划。
- 订单号 `no` 用「时间戳+随机」生成，`uk_no` 唯一索引兜底；高并发下若碰撞会抛唯一约束异常，可后续换成发号器。
- 抢单并发已用条件更新保证；如需在广播/通知层再加分布式锁，属 Plan 3。
