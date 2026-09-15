# yy 实时与异步 Implementation Plan（Plan 3）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给派单系统加上实时与异步能力：商家发单/用户抢单时通过 **WebSocket 实时广播**给在线用户，并用 **定时任务自动取消超时未接订单**（spec §5.5 / §6）。

**Architecture:** 领域事件用 **Spring Event**（`ApplicationContext.publishEvent`，spec §6.3 明确允许 MVP 用本地事件；member 模块同款模式），消费者调用框架的 `WebSocketMessageSender` 广播（sender-type=local，单实例；集群切 redis 即可）。超时取消用框架的 **Quartz JobHandler**（`yy-spring-boot-starter-job`）+ `infra_job` 注册，每分钟扫描。

**Tech Stack:** JDK 17（跑在 JDK 21）· Spring Boot 3.5.15 · `yy-spring-boot-starter-websocket` · `yy-spring-boot-starter-mq` · `yy-spring-boot-starter-job`（Quartz）· MySQL 8（库 `yy`）

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`（第 5.5、6 节）

## Global Constraints

- 包名 `com.yy`；模块 `yy-module-dispatch`，Java 根包 `com.yy.module.dispatch`。
- 三个 starter 已在 `yy-server` classpath（无需改 pom）：`yy-spring-boot-starter-websocket`、`-mq`、`-job`。
- WebSocket 已启用：`yy.websocket.enable=true`，`path=/infra/ws`，`sender-type=local`。`WebSocketMessageSender` bean 可直接注入。
- 用户类型用 `com.yy.framework.common.enums.UserTypeEnum.MEMBER`（会员即用户端/商家端同一登录主体）。
- 错误码段 `1-099-002-xxx`（已有 `ORDER_STATUS_ERROR` 等，Task 复用，不新增）。
- 超时取消复用已有 `DispatchOrderOperateTypeEnum.SYSTEM_CANCEL(41)` 与 `DispatchOrderMapper.updateCancel(...)`（条件更新，Plan 2b 已实现）。
- 命令在 Windows PowerShell 5.1；MySQL 导入加 `--default-character-set=utf8mb4`；mock 登录 `Authorization: Bearer test<userId>`（`yy.security.mock-enable=true`）。
- 参考样板：`yy-module-member\...\mq\producer\user\MemberUserProducer.java`（`ApplicationContext.publishEvent` 模式）。

---

### Task 1: 订单事件 + WebSocket 广播

**Files:**
- Create: `...\mq\message\order\DispatchOrderCreatedMessage.java`
- Create: `...\mq\message\order\DispatchOrderAcceptedMessage.java`
- Create: `...\mq\producer\order\DispatchOrderProducer.java`
- Create: `...\mq\consumer\order\DispatchOrderWebSocketConsumer.java`
- Modify: `...\service\order\DispatchOrderServiceImpl.java`（createOrder / acceptOrder 发事件）
- Test: `...\src\test\java\com\yy\module\dispatch\mq\consumer\order\DispatchOrderWebSocketConsumerTest.java`

**Interfaces:**
- Consumes: Plan 2b 的 `DispatchOrderDO`、`DispatchOrderOperateTypeEnum`、`WebSocketMessageSender`、`UserTypeEnum`
- Produces:
  - `DispatchOrderProducer.sendOrderCreated(DispatchOrderDO)`、`sendOrderAccepted(Long orderId, Long userId, Long merchantMemberUserId)`
  - 消息类型常量：`"dispatch-order-created"`、`"dispatch-order-accepted"`

- [ ] **Step 1: 事件消息类**

`...\mq\message\order\DispatchOrderCreatedMessage.java`：
```java
package com.yy.module.dispatch.mq.message.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单订单创建消息：用于 WebSocket 广播给在线用户抢单
 */
@Data
public class DispatchOrderCreatedMessage {

    private Long orderId;
    private String title;
    private Integer amount;
    private String address;
    private LocalDateTime deadline;

    public static DispatchOrderCreatedMessage of(DispatchOrderDO order) {
        DispatchOrderCreatedMessage message = new DispatchOrderCreatedMessage();
        message.setOrderId(order.getId());
        message.setTitle(order.getTitle());
        message.setAmount(order.getAmount());
        message.setAddress(order.getAddress());
        message.setDeadline(order.getDeadline());
        return message;
    }
}
```

`...\mq\message\order\DispatchOrderAcceptedMessage.java`：
```java
package com.yy.module.dispatch.mq.message.order;

import lombok.Data;

/**
 * 派单订单被接单消息：广播给在线用户（从大厅移除）+ 定向通知商家
 */
@Data
public class DispatchOrderAcceptedMessage {

    private Long orderId;
    private Long userId;
    /** 发单商家的会员编号，用于定向通知 */
    private Long merchantMemberUserId;

}
```

- [ ] **Step 2: Producer**

`...\mq\producer\order\DispatchOrderProducer.java`：
```java
package com.yy.module.dispatch.mq.producer.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 派单订单事件 Producer
 */
@Slf4j
@Component
public class DispatchOrderProducer {

    @Resource
    private ApplicationContext applicationContext;

    public void sendOrderCreated(DispatchOrderDO order) {
        applicationContext.publishEvent(DispatchOrderCreatedMessage.of(order));
    }

    public void sendOrderAccepted(Long orderId, Long userId, Long merchantMemberUserId) {
        DispatchOrderAcceptedMessage message = new DispatchOrderAcceptedMessage();
        message.setOrderId(orderId);
        message.setUserId(userId);
        message.setMerchantMemberUserId(merchantMemberUserId);
        applicationContext.publishEvent(message);
    }

}
```

- [ ] **Step 3: WebSocket 消费者**

`...\mq\consumer\order\DispatchOrderWebSocketConsumer.java`：
```java
package com.yy.module.dispatch.mq.consumer.order;

import com.yy.framework.common.enums.UserTypeEnum;
import com.yy.framework.websocket.core.sender.WebSocketMessageSender;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 派单订单事件消费者：WebSocket 实时推送
 */
@Slf4j
@Component
public class DispatchOrderWebSocketConsumer {

    public static final String MESSAGE_TYPE_CREATED = "dispatch-order-created";
    public static final String MESSAGE_TYPE_ACCEPTED = "dispatch-order-accepted";

    @Resource
    private WebSocketMessageSender webSocketMessageSender;

    @EventListener
    public void onOrderCreated(DispatchOrderCreatedMessage message) {
        // 广播给所有在线会员：抢单大厅实时刷新
        webSocketMessageSender.sendObject(UserTypeEnum.MEMBER.getValue(), MESSAGE_TYPE_CREATED, message);
        log.info("[onOrderCreated][广播新单({})]", message.getOrderId());
    }

    @EventListener
    public void onOrderAccepted(DispatchOrderAcceptedMessage message) {
        // 广播给所有在线会员：其他用户端从大厅移除该单
        webSocketMessageSender.sendObject(UserTypeEnum.MEMBER.getValue(), MESSAGE_TYPE_ACCEPTED, message);
        // 定向通知发单商家
        if (message.getMerchantMemberUserId() != null) {
            webSocketMessageSender.sendObject(UserTypeEnum.MEMBER.getValue(), message.getMerchantMemberUserId(),
                    MESSAGE_TYPE_ACCEPTED, message);
        }
        log.info("[onOrderAccepted][订单({})被用户({})接单]", message.getOrderId(), message.getUserId());
    }

}
```

- [ ] **Step 4: 在 Service 发事件**

修改 `...\service\order\DispatchOrderServiceImpl.java`：
1. 注入 producer 与 merchantService：
```java
    @Resource
    private DispatchOrderProducer orderProducer;
    @Resource
    private com.yy.module.dispatch.service.merchant.DispatchMerchantService merchantService;
```
2. `createOrder` 在 `orderLogService.createLog(...)` 之后、`return order.getId();` 之前加：
```java
        orderProducer.sendOrderCreated(order);
```
3. `acceptOrder` 把 `validateOrderExists(orderId);` 改为持有 order，并在写日志后发事件：
```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        int rows = orderMapper.updateAccept(orderId, userId);
        if (rows == 0) {
            throw exception(ORDER_ALREADY_ACCEPTED);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_ACCEPT,
                DispatchOrderOperateTypeEnum.USER_ACCEPT.getContent());
        // 通知：广播给在线用户 + 定向通知商家
        Long merchantMemberUserId = null;
        var merchant = merchantService.getMerchant(order.getMerchantId());
        if (merchant != null) {
            merchantMemberUserId = merchant.getMemberUserId();
        }
        orderProducer.sendOrderAccepted(orderId, userId, merchantMemberUserId);
    }
```

- [ ] **Step 4b: 修正既有单测的装配**

`DispatchOrderServiceImpl` 新增了 `DispatchOrderProducer`、`DispatchMerchantService` 两个依赖，会让 Plan 2b 的 `DispatchOrderServiceTest` 因缺少 Bean 而装配失败。修改 `...\src\test\java\com\yy\module\dispatch\service\order\DispatchOrderServiceTest.java` 的 `@Import`：
```java
@Import({DispatchOrderServiceImpl.class, DispatchOrderLogServiceImpl.class,
        com.yy.module.dispatch.mq.producer.order.DispatchOrderProducer.class,
        com.yy.module.dispatch.service.merchant.DispatchMerchantServiceImpl.class})
```
（保持原测试用例不变；运行 Step 6 时一并跑 `DispatchOrderServiceTest` 确认仍通过。）

- [ ] **Step 5: 单测（mock sender 断言广播）**

`...\src\test\java\com\yy\module\dispatch\mq\consumer\order\DispatchOrderWebSocketConsumerTest.java`：
```java
package com.yy.module.dispatch.mq.consumer.order;

import com.yy.framework.common.enums.UserTypeEnum;
import com.yy.framework.websocket.core.sender.WebSocketMessageSender;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DispatchOrderWebSocketConsumerTest {

    @Test
    public void testOnOrderCreated_broadcastToMembers() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderWebSocketConsumer consumer = new DispatchOrderWebSocketConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "webSocketMessageSender", sender);

        DispatchOrderCreatedMessage message = new DispatchOrderCreatedMessage();
        message.setOrderId(1L);
        consumer.onOrderCreated(message);

        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_CREATED), same(message));
    }

    @Test
    public void testOnOrderAccepted_broadcastAndNotifyMerchant() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderWebSocketConsumer consumer = new DispatchOrderWebSocketConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "webSocketMessageSender", sender);

        DispatchOrderAcceptedMessage message = new DispatchOrderAcceptedMessage();
        message.setOrderId(1L);
        message.setUserId(1001L);
        message.setMerchantMemberUserId(2001L);
        consumer.onOrderAccepted(message);

        // 广播给所有会员
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_ACCEPTED), same(message));
        // 定向通知商家
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()), eq(2001L),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_ACCEPTED), same(message));
    }
}
```
> 说明：`WebSocketMessageSender` 由框架自动装配（sender-type=local）。单测用 mock + `ReflectionTestUtils` 注入，验证广播与定向通知的参数。

- [ ] **Step 6: 运行测试**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am "-Dtest=DispatchOrderWebSocketConsumerTest,DispatchOrderServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
Expected: `DispatchOrderWebSocketConsumerTest` 2 个通过；`DispatchOrderServiceTest`（11 个）仍全部通过（证明 Step 4b 的装配修正生效）。

- [ ] **Step 7: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 发单/抢单 WebSocket 实时广播（Spring Event）"
```

---

### Task 2: 超时未接自动取消（Service + JobHandler + infra_job）

**Files:**
- Modify: `...\dal\mysql\order\DispatchOrderMapper.java`（新增按状态+deadline 查询）
- Modify: `...\service\order\DispatchOrderService.java`（新增 `cancelTimeoutOrders()`）
- Modify: `...\service\order\DispatchOrderServiceImpl.java`（实现）
- Create: `...\job\DispatchOrderTimeoutJob.java`
- Create: `D:\IDEA\yy\sql\module\dispatch_job.sql`
- Test: `...\src\test\java\com\yy\module\dispatch\service\order\DispatchOrderTimeoutTest.java`

**Interfaces:**
- Consumes: Plan 2b 的 `DispatchOrderMapper.updateCancel`、`DispatchOrderOperateTypeEnum.SYSTEM_CANCEL`
- Produces: `DispatchOrderService.cancelTimeoutOrders()→int`；JobHandler bean `dispatchOrderTimeoutJob`；`infra_job` 行

- [ ] **Step 1: Mapper 查询**

在 `DispatchOrderMapper` 中新增（`updateAccept` 之后）：
```java
    /**
     * 查询指定状态且 deadline 早于给定时间的订单（用于超时未接扫描）
     */
    default java.util.List<DispatchOrderDO> selectListByStatusAndDeadlineLt(Integer status, LocalDateTime deadline) {
        return selectList(new LambdaQueryWrapperX<DispatchOrderDO>()
                .eq(DispatchOrderDO::getStatus, status)
                .isNotNull(DispatchOrderDO::getDeadline)
                .lt(DispatchOrderDO::getDeadline, deadline));
    }
```

- [ ] **Step 2: Service 方法**

在 `DispatchOrderService` 接口新增：
```java
    /**
     * 取消超时未接的订单（status=待接单 且 deadline < now）
     *
     * @return 取消数量
     */
    int cancelTimeoutOrders();
```

在 `DispatchOrderServiceImpl` 实现（放在 `cancelOrderByAdmin` 之后）：
```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders() {
        List<DispatchOrderDO> orders = orderMapper.selectListByStatusAndDeadlineLt(
                DispatchOrderStatusEnum.PENDING.getStatus(), LocalDateTime.now());
        int count = 0;
        for (DispatchOrderDO order : orders) {
            // 条件更新：仅当仍是待接单才取消，避免与并发抢单互相覆盖
            if (orderMapper.updateCancel(order.getId(), "超时未接单，系统自动取消",
                    DispatchOrderStatusEnum.PENDING.getStatus()) > 0) {
                orderLogService.createLog(order.getId(), DispatchOrderOperateTypeEnum.SYSTEM_CANCEL,
                        DispatchOrderOperateTypeEnum.SYSTEM_CANCEL.getContent());
                count++;
            }
        }
        return count;
    }
```
> 若 `List` 未导入，补 `import java.util.List;`。

- [ ] **Step 3: JobHandler**

`...\job\DispatchOrderTimeoutJob.java`：
```java
package com.yy.module.dispatch.job;

import com.yy.framework.quartz.core.handler.JobHandler;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 派单订单超时取消 Job：扫描并取消超时未接的订单
 */
@Slf4j
@Component("dispatchOrderTimeoutJob")
public class DispatchOrderTimeoutJob implements JobHandler {

    @Resource
    private DispatchOrderService orderService;

    @Override
    public String execute(String param) {
        int count = orderService.cancelTimeoutOrders();
        log.info("[execute][超时取消派单订单 {} 个]", count);
        return "超时取消派单订单 " + count + " 个";
    }

}
```

- [ ] **Step 4: 注册 infra_job**

创建 `D:\IDEA\yy\sql\module\dispatch_job.sql`：
```sql
DELETE FROM `infra_job` WHERE `handler_name` = 'dispatchOrderTimeoutJob';
INSERT INTO `infra_job` (`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('派单订单超时取消 Job', 1, 'dispatchOrderTimeoutJob', NULL, '0 0/1 * * * ?', 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
```
导入：
```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 yy -e "source D:/IDEA/yy/sql/module/dispatch_job.sql"
mysql -uroot -p123456 yy -e "SELECT id,name,status,handler_name,cron_expression FROM infra_job WHERE handler_name='dispatchOrderTimeoutJob';"
```
Expected: 1 行，status=1，cron=`0 0/1 * * * ?`。

- [ ] **Step 5: 单测（超时取消）**

`...\src\test\java\com\yy\module\dispatch\service\order\DispatchOrderTimeoutTest.java`：
```java
package com.yy.module.dispatch.service.order;

import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Import({DispatchOrderServiceImpl.class, DispatchOrderLogServiceImpl.class,
        com.yy.module.dispatch.mq.producer.order.DispatchOrderProducer.class,
        com.yy.module.dispatch.service.merchant.DispatchMerchantServiceImpl.class})
public class DispatchOrderTimeoutTest extends BaseDbUnitTest {

    @Resource
    private DispatchOrderService orderService;
    @Resource
    private DispatchOrderMapper orderMapper;

    private DispatchOrderCreateReqVO buildReqVO(LocalDateTime deadline) {
        DispatchOrderCreateReqVO vo = new DispatchOrderCreateReqVO();
        vo.setTitle("超时测试");
        vo.setAddress("上海");
        vo.setAmount(1000);
        vo.setDeadline(deadline);
        return vo;
    }

    @Test
    public void testCancelTimeoutOrders() {
        // 已过期的待接单订单
        Long expiredId = orderService.createOrder(2001L, buildReqVO(LocalDateTime.now().minusMinutes(5)));
        // 未过期的待接单订单
        Long futureId = orderService.createOrder(2001L, buildReqVO(LocalDateTime.now().plusMinutes(30)));
        // 已接单（即使过期也不该被系统取消）
        Long acceptedId = orderService.createOrder(2001L, buildReqVO(LocalDateTime.now().minusMinutes(5)));
        orderService.acceptOrder(acceptedId, 1001L);

        int count = orderService.cancelTimeoutOrders();
        assertEquals(1, count);
        assertEquals(DispatchOrderStatusEnum.CANCELED.getStatus(), orderMapper.selectById(expiredId).getStatus());
        assertEquals(DispatchOrderStatusEnum.PENDING.getStatus(), orderMapper.selectById(futureId).getStatus());
        assertEquals(DispatchOrderStatusEnum.ACCEPTED.getStatus(), orderMapper.selectById(acceptedId).getStatus());
    }
}
```
> 备注：`createOrder` 现在会调用 `orderProducer.sendOrderCreated(...)`（Spring Event，无监听器时是 no-op）。若单测因缺少 `WebSocketMessageSender` 报错，则**不要**导入 producer，改为在测试里 `@MockBean` 掉 `DispatchOrderProducer`，或把 producer 的事件发布做成可注入的空实现。实现者按实际报错调整，但必须覆盖：过期待接单被取消、未过期不动、已接单不动。

- [ ] **Step 6: 运行测试**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am "-Dtest=DispatchOrderTimeoutTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
Expected: 通过。

- [ ] **Step 7: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 超时未接订单自动取消（JobHandler + infra_job）"
```

---

### Task 3: 集成验证

**Files:**
- Verify: `D:\IDEA\yy` 全工程

**Interfaces:**
- Consumes: Task 1–2
- Produces: 可运行、可验证的实时广播与超时取消

- [ ] **Step 1: 全量构建**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 2: 启动服务器（后台）**

```powershell
$log="$env:TEMP\yy-server.log"; Remove-Item $log -ErrorAction SilentlyContinue
$p = Start-Process -FilePath "java" -ArgumentList "-jar","D:\IDEA\yy\yy-server\target\yy-server.jar","--spring.profiles.active=local" -RedirectStandardOutput $log -RedirectStandardError "$env:TEMP\yy-server.err.log" -PassThru -WindowStyle Hidden
$p.Id | Set-Content "$env:TEMP\yy-server.pid"
$ok=$false; for ($i=0; $i -lt 25; $i++) { Start-Sleep -Seconds 3; if (Select-String -Path $log -SimpleMatch "Started YyServerApplication" -Quiet -ErrorAction SilentlyContinue) { $ok=$true; break } }
"started=$ok"
```
Expected: `started=True`。

- [ ] **Step 3: 造一个「已过期待接单」订单并触发 Job**

先确认商家数据（Plan 2b 造过：`dispatch_merchant` member_user_id=247，status=1）：
```powershell
# 商家发一个 deadline 已过期的单
$merchant = @{ Authorization="Bearer test247" }
$body = '{"title":"超时单","address":"上海","amount":1000,"deadline":"2020-01-01 00:00:00"}'
$orderId = (Invoke-RestMethod -Uri "http://127.0.0.1:48080/app-api/merchant/dispatch/order/create" -Method POST -ContentType "application/json" -Headers $merchant -Body $body).data
"orderId=$orderId"
```
Expected: 返回非空 orderId，且状态为 0（待接单）。

- [ ] **Step 4: 等待 Quartz 每分钟执行（或手动触发）**

等待最多 ~70 秒让 `dispatchOrderTimeoutJob`（cron `0 0/1 * * * ?`）执行一次：
```powershell
Start-Sleep -Seconds 70
mysql -uroot -p123456 yy -e "SELECT id,status,cancel_reason FROM dispatch_order WHERE id=$orderId;"
```
Expected: status=40（已取消），cancel_reason 含「超时未接单」。
> 备选：管理端手动触发 Job（`POST /admin-api/infra/job/trigger?id=<jobId>`），避免等待。

- [ ] **Step 5: 校验操作日志含「系统自动取消」**

```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 yy -e "SELECT operate_type,content FROM dispatch_order_log WHERE order_id=$orderId ORDER BY id;"
```
Expected: 含 `operate_type=41`（系统自动取消）。

- [ ] **Step 6: 校验 WebSocket 广播已发出**

查启动日志是否出现广播日志（发单时）：
```powershell
Select-String -Path "$env:TEMP\yy-server.log" -SimpleMatch "广播新单" | Select-Object -Last 3
```
Expected: 至少 1 条 `[onOrderCreated][广播新单(<orderId>)]`。
> 说明：WebSocket 客户端的真实收发已由 Task 1 单测覆盖；此处验证事件→消费者→sender 的运行时路径被触发。

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

- 发单/抢单触发 Spring Event，消费者经 `WebSocketMessageSender` 广播（`dispatch-order-created` / `dispatch-order-accepted`），单测验证参数
- 超时未接订单被 `dispatchOrderTimeoutJob`（cron 每分钟）自动取消为 status=40，并写 `SYSTEM_CANCEL` 日志
- `infra_job` 已注册该 Job（status=1）
- 全量构建 BUILD SUCCESS，运行时验证通过
- 全部改动提交到 `feature/yy-backend-framework`

## 已知风险 / 备注

- 用 Spring Event（本地）而非 Redis Stream：spec §6.3 允许 MVP 用本地事件；WebSocket sender-type=local（单实例）。集群部署时把 `yy.websocket.sender-type` 改 `redis`、事件换 Redis Stream 即可。
- 微信订阅消息（spec §6.2）未开通，本计划不实现。
- WebSocket 客户端真实连接未做自动化验证（无 WS 客户端工具），由单测覆盖发送参数 + 运行时日志验证路径。
- `cancelTimeoutOrders` 依赖 `deadline` 非空；无 deadline 的订单不会被超时取消（业务上视为长期有效）。
