# yy 派单系统 — 项目框架设计文档

- 日期：2026-09-14
- 状态：已与需求方逐段确认
- 参考项目：`ruoyi-vue-pro`（芋道 yudao，`master-jdk17`，JDK 17 + Spring Boot 3.5.15）
- 底座：`yudao-boot-mini`（精简版，`master-jdk17` 分支）

---

## 1. 项目概述

### 1.1 业务定位
一个**派单交易平台**，本质与商城订单相同的交易系统，但角色方向相反：

- **商家**是发单方（派单）
- **用户**是接单方（接单）
- 平台管理端负责审核与监管

### 1.2 匹配机制
**广播抢单**：商家发单后广播给在线用户，用户先到先得。

核心流程：

```
商家发单(待接单) → 广播给在线用户 → 用户抢单(已接单) → 服务中 → 已完成 / 已取消
```

### 1.3 三端形态
| 端 | 载体 | 说明 |
|----|------|------|
| 用户端 | 小程序（微信优先，后续 H5/APP） | 与商家端同一个小程序，通过身份切换 |
| 商家端 | 小程序（同一个） | 同上，登录后切换为商家身份 |
| 后台管理端 | 电脑端 Web | 商家审核、订单监管、结算 |

> 用户端与商家端**不是两个应用**，而是**同一个小程序内切换身份**。

---

## 2. 技术栈与底座

| 项 | 选择 |
|----|------|
| 底座 | `yudao-boot-mini`（`master-jdk17`） |
| JDK | 17 |
| Spring Boot | 3.5.15 |
| Maven 坐标 | groupId `com.yy`，根工程 `yy`，revision `1.0.0-SNAPSHOT` |
| Java 包名 | **全局改名 `cn.iocoder.yudao` → `com.yy`**（脚本化替换 Java 包名、Mapper XML namespace、yaml、SQL 引用） |
| 多租户 | **关闭**（不引入 `biz-tenant`，用 `merchant_id` + 数据权限隔离） |
| 支付 | **保留** `yy-module-pay` |
| 前端 | **包含**（后台 Web + 小程序） |

---

## 3. 工程结构

### 3.1 后端（独立仓库，与参考项目平级：`D:\IDEA\yy`）

```
yy/                                  # 根工程 (pom)
├── yy-dependencies/                 # 依赖 BOM
├── yy-framework/                    # 框架层（从 yudao-framework 裁剪迁移）
│   ├── yy-common/
│   ├── yy-spring-boot-starter-web/
│   ├── yy-spring-boot-starter-security/
│   ├── yy-spring-boot-starter-mybatis/
│   ├── yy-spring-boot-starter-redis/
│   ├── yy-spring-boot-starter-job/
│   ├── yy-spring-boot-starter-mq/
│   ├── yy-spring-boot-starter-websocket/
│   ├── yy-spring-boot-starter-biz-data-permission/
│   ├── yy-spring-boot-starter-excel/
│   ├── yy-spring-boot-starter-monitor/
│   ├── yy-spring-boot-starter-protection/
│   └── yy-spring-boot-starter-test/
│   # 去掉：biz-tenant（关多租户）、biz-ip（不需要）
├── yy-module-system/                # 系统管理（精简版自带）
├── yy-module-infra/                 # 基础设施/代码生成（精简版自带）
├── yy-module-member/                # 会员/用户（迁移）
├── yy-module-pay/                   # 支付（迁移）
├── yy-module-dispatch/              # 派单核心（新建）
└── yy-server/                       # 启动模块
```

> 暂**不拆** `dispatch-api` 子模块（YAGNI）。等出现跨模块调用需求（统计、结算）再拆，方式同 `trade-api`。

### 3.2 前端

```
yy-ui-admin/     # fork yudao-ui-admin-vue3（后台管理端，电脑端 Web）
yy-ui-uniapp/    # fork yudao-mall-uniapp（用户/商家小程序）
```

---

## 4. 身份体系（一个小程序，双身份）

### 4.1 核心思路
用户与商家**共用一套登录**，均属 `UserTypeEnum.MEMBER`。商家不是独立账号，而是**绑定在会员上的一张档案**。身份由前端切换，后端用 **URL 前缀 + 拦截器**识别并校验。

### 4.2 数据模型
```
yy_member_user（member 模块已有）   ← 登录主体（用户/商家共用）
        │ 1:1
        ▼
yy_merchant（新建）
  ├─ id
  ├─ member_user_id   # 绑定会员
  ├─ name / logo / contact
  ├─ status           # 0待审核 1正常 2禁用
  └─ 其他商家资质字段
```

**用户与商家先做 1:1**（一个会员最多绑定一个商家），未来可放开 1:N。

### 4.3 登录
- 统一走 `yy-module-member` 的 `AppAuthController`：短信登录 / 微信小程序登录
- 登录后 `userType = MEMBER`
- 商家**无独立登录入口**；登录后判断该会员是否绑定商家，决定前端是否显示「切换到商家」

### 4.4 身份切换与 API 划分
所有小程序 API 在 `/app-api/**` 下，用路径前缀区分身份：

| 身份 | API 路径 | 功能 |
|------|---------|------|
| 用户 | `/app-api/dispatch/order/**` | 抢单、我的接单、订单详情 |
| 商家 | `/app-api/merchant/dispatch/order/**` | 发单、我的发单、订单详情 |
| 公共 | `/app-api/merchant/profile/**` | 商家入驻、商家信息 |

### 4.5 校验机制
```
@MerchantIdentity 注解            → 加在商家端 Controller 上
MerchantContextInterceptor        → 拦截 /app-api/merchant/**，校验当前会员有「正常」状态的商家档案，
                                    写入 MerchantContextHolder（ThreadLocal）
MerchantContextHolder.getMerchantId() → Service 层取当前商家
```
> 商家端接口**不信任前端传的 merchantId**，一律从登录态推导（防越权）。

### 4.6 商家入驻流程（需要审核）
```
用户在小程序提交入驻 → yy_merchant(status=0 待审核)
                    → 后台管理端审核 → status=1 正常
                    → 用户可切换商家身份
```

---

## 5. 派单核心模型

### 5.1 数据模型（一单一任务）

```
yy_dispatch_order（派单主表）
  ├─ id
  ├─ no                # 订单号
  ├─ merchant_id       # 发单方（yy_merchant.id）
  ├─ user_id           # 接单方（yy_member_user.id，抢单前为 null）
  ├─ status            # 订单状态（见 5.2）
  ├─ pay_status        # 结算状态（见 5.2）
  ├─ title / description / images   # 服务内容
  ├─ address / contact             # 服务地点、联系人
  ├─ amount            # 酬劳金额（商家付给用户）
  ├─ deadline          # 接单截止时间（超时未接→自动取消）
  ├─ accept_time / start_time / finish_time
  └─ create_time / update_time / creator / updater / deleted  # BaseDO

yy_dispatch_order_log（操作日志，借鉴 trade 的 TradeOrderLog）
  ├─ order_id
  ├─ operate_type
  ├─ content
  └─ create_time
```

> **先做「一单一任务」**：一个订单即一项服务，不拆明细。若未来出现「一单含多个服务项」，再按 trade 的 order/order-item 拆分。

### 5.2 状态机

**订单状态**（借鉴 `TradeOrderStatusEnum`）：
```
待接单(0) → 已接单(10) → 服务中(20) → 已完成(30)
                                     ↘ 已取消(40)
```
- 待接单 → 已接单：用户抢单成功
- 已接单 → 服务中：用户点击开始服务
- 服务中 → 已完成：用户完成 / 商家确认
- 待接单 → 已取消：商家主动取消 / 超时未接自动取消

**结算状态**（独立字段）：
```
待结算(0) → 已结算(10) → 已退款(20)
```

**操作日志类型**（借鉴 `TradeOrderOperateTypeEnum`）：
```
商家发单 / 用户抢单 / 开始服务 / 完成服务 / 商家取消 / 系统超时取消 / 结算完成
```

### 5.3 抢单并发控制（关键）
抢单用**数据库条件更新**保证原子性，作为主防线：
```sql
UPDATE yy_dispatch_order
SET user_id = #{userId}, status = 10, accept_time = NOW()
WHERE id = #{orderId} AND status = 0 AND user_id IS NULL AND deleted = 0
-- 影响行数 = 1 → 抢单成功；= 0 → 已被别人抢走
```
- 若抢单涉及多步（校验用户资质 + 扣保证金等），叠加 **Redisson 分布式锁**（`lock:dispatch:order:{id}`）包住整段
- 单表单条件更新已足够原子，Redisson 作为复杂逻辑时的补充

### 5.4 结算
- **订单完成后结算**：商家余额/预充值扣款 → 打给用户
- 复用 `yy-module-pay` 处理资金流转；结算记录独立表（后续设计细化）

### 5.5 超时未接处理
- 用 `yy-spring-boot-starter-job` 定时扫描 `status=待接单 AND deadline < now` → 自动取消 + 写日志 + 通知商家
- 后续可用 Redisson 延迟队列做精确触发（先做定时任务，简单够用）

---

## 6. 实时与异步

### 6.1 WebSocket 实时推送（复用 `yy-spring-boot-starter-websocket`）
```
用户进入小程序 → 建立 WebSocket 连接（带 token，userType=MEMBER）
商家发单成功 → 广播给所有在线会员
  { type: "dispatch.new", order: { id, title, amount, address, deadline } }
用户抢单成功 → 广播 { type: "dispatch.grabbed", orderId }（其他在线用户移除该单）
            + 定向通知商家 { type: "dispatch.accepted", orderId, userId }
```
- **MVP 广播范围：所有在线会员**。后续可加过滤（区域/技能/等级），starter 的 sender 支持定向发送
- 集群靠 Redis pub/sub 同步（starter 已封装）

### 6.2 微信订阅消息
- **暂不实现**（小程序订阅消息尚未开通）
- 后续开通后，复用 member 模块的 `AppSocialWxaSubscribeTemplate` 作为离线兜底

### 6.3 消息队列（复用 `yy-spring-boot-starter-mq`）
```
商家发单 → 发 MQ 事件 DispatchOrderCreatedEvent
              ├─ 消费者A：WebSocket 广播
              ├─ 消费者B：写操作日志
              └─ 消费者C：（未来）订阅消息
```
- **MVP 用 Redis**（已有），生产可切 RabbitMQ/RocketMQ（starter 支持，改配置即可）

### 6.4 定时任务（复用 `yy-spring-boot-starter-job`）
```
每 1 分钟扫描：status=待接单 AND deadline < now → 自动取消 + 写日志 + 通知商家
```

---

## 7. 前端结构

### 7.1 后台管理端 `yy-ui-admin/`
Fork `yudao-ui-admin-vue3`（Vue3 + element-plus），保留系统/基础设施页面，新增派单菜单：
```
├── 商家管理      # 商家列表、入驻审核（待审核→通过/驳回）
├── 派单订单管理  # 全部订单、按状态/商家/用户筛选、强制取消
├── 结算管理      # 结算记录、退款
└── 数据看板      # 发单量、接单率、结算金额
```
> 后台**只做电脑端 Web**（不做移动端）。

### 7.2 用户/商家小程序 `yy-ui-uniapp/`
Fork `yudao-mall-uniapp`（uni-app），砍掉商城页面，重做派单页面：
```
pages/
├── login/          # 登录（短信/微信，复用 member 登录）
├── identity/       # ★ 身份选择页：进入小程序后选「用户」或「商家」
├── user/           # 用户端
│   ├── hall/       #   抢单大厅（WebSocket 实时刷新新单）
│   ├── orders/     #   我的接单（已接单/服务中/已完成）
│   └── detail/     #   订单详情（开始服务、完成）
├── merchant/       # 商家端
│   ├── publish/    #   发单
│   ├── orders/     #   我的发单
│   ├── detail/     #   订单详情（取消、确认完成）
│   └── apply/      #   商家入驻申请
└── mine/           # 个人中心（切换身份入口）
```

### 7.3 身份切换的前端实现
```
stores/identity.ts        # Pinia 全局身份状态：'user' | 'merchant'
api/dispatch/*.ts         # 用户端接口（/app-api/dispatch/**）
api/merchant/*.ts         # 商家端接口（/app-api/merchant/**）
```
- 登录后调接口判断是否已入驻商家 → 决定是否显示「切换身份」
- 切换只改 `identity` 状态 + 跳转对应首页，**不重新登录**
- 商家身份下，未审核通过则提示「审核中」

### 7.4 WebSocket 接入
- 小程序用 `uni.connectSocket` 建立连接（带 token）
- 收到 `dispatch.new` → 抢单大厅插入新单 + 角标/提示音
- 收到 `dispatch.grabbed` → 从大厅移除该单

### 7.5 端支持范围
- 小程序先支持**微信小程序**（H5/APP 后续）

---

## 8. 落地路径

1. 新建 `D:\IDEA\yy` 后端工程，基于 `yudao-boot-mini`（`master-jdk17`）
2. 全局改名 `cn.iocoder.yudao` → `com.yy`，Maven 坐标改 `com.yy`
3. 迁入 `member` + `pay` 模块（按官方《迁移文档》）
4. 新建 `yy-module-dispatch`，实现商家/订单模型、状态机、抢单、日志
5. 实现身份体系（`@MerchantIdentity` + `MerchantContextInterceptor` + `MerchantContextHolder`）
6. 实现 WebSocket 广播 + MQ（Redis）+ 定时任务
7. 接入 `yy-module-pay` 做结算
8. Fork 并改造 `yy-ui-admin`（商家审核/订单/结算）
9. Fork 并改造 `yy-ui-uniapp`（身份切换/抢单大厅/发单）

---

## 9. 已确认决策清单

| # | 决策 | 结论 |
|---|------|------|
| 1 | 底座 | yudao-boot-mini（master-jdk17） |
| 2 | 项目名 | `yy` |
| 3 | 包名 | 全局改 `com.yy` |
| 4 | 多租户 | 关闭 |
| 5 | 支付模块 | 保留 |
| 6 | 前端 | 包含（后台 Web + 小程序） |
| 7 | 模块清单 | system / infra / member / pay / dispatch |
| 8 | 匹配机制 | 广播抢单 |
| 9 | 身份切换 | URL 前缀 + 拦截器 |
| 10 | 用户-商家关系 | 1:1 |
| 11 | 商家入驻 | 需要审核 |
| 12 | 订单粒度 | 一单一任务 |
| 13 | 订单状态 | 含「服务中」中间态 |
| 14 | 结算时机 | 订单完成后结算 |
| 15 | 广播范围（MVP） | 所有在线会员 |
| 16 | 微信订阅消息 | 暂不实现 |
| 17 | MQ | 先用自建 Redis |
| 18 | 后台移动端 | 不做 |
| 19 | 小程序端 | 微信小程序优先 |

---

## 10. 明确不做（YAGNI）

- 多租户 SaaS
- `dispatch-api` 子模块拆分（暂缓）
- 订单明细/order-item 拆分（暂缓）
- 区域/技能/等级等派单过滤规则（MVP 后）
- 微信订阅消息（待开通）
- H5 / APP 端（后续）
- 后台移动端
- 派单规则引擎（定向派单/自动分配）

---

## 11. 风险与待办

| 项 | 说明 |
|----|------|
| 全局改名工作量 | Java 包名 + Mapper XML namespace + yaml + SQL 引用，需脚本化并回归验证 |
| 结算模型细化 | 结算记录表、商家余额/预充值、退款流程需在后续设计中细化 |
| 抢单压测 | 广播抢单的高并发需压测，验证条件更新 + 锁的表现 |
| 小程序 UI | mall-uniapp 需大幅裁剪，工作量需评估 |
| 数据权限 | 商家端数据隔离需验证 data-permission starter 的接入方式 |
