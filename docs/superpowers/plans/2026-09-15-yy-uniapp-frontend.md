# yy 小程序前端 Implementation Plan（Plan 6）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 `yudao-mall-uniapp` 搭建 `yy` 用户/商家小程序：同一小程序内切换身份（用户/商家），用户抢单、商家发单。

**Architecture:** yudao-mall-uniapp 是 Vue3 uni-app（shopro 模板）。复用其登录（member 短信/微信登录）与请求层 `sheep/request`。新增 `sheep/api/dispatch/` 接口层与 `pages/dispatch/` 页面，在 `pages.json` 注册路由。身份用前端状态（pinia `sheep/store`）切换，后端靠 URL 前缀区分（`/app-api/dispatch/**` vs `/app-api/merchant/dispatch/**`）。

**Tech Stack:** Vue3 · uni-app（shopro）· HBuilderX · luch-request · pinia

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`（§7.2 小程序、§4 身份体系）

## Global Constraints

- 前端工程在 `D:\IDEA\yy-ui-uniapp`（独立目录，不在后端 git 仓库内）。
- 后端地址：`.env` 的 `SHOPRO_DEV_BASE_URL=http://127.0.0.1:48080`（默认已是），接口前缀 `SHOPRO_API_PATH=/app-api`，WebSocket 前缀 `SHOPRO_WEBSOCKET_PATH=/infra/ws`。
- 复用登录：`/member/auth/sms-login`（会员即用户端/商家端同一登录主体）。
- 新增 API：`sheep/api/dispatch/order.js`、`sheep/api/dispatch/merchant.js`（`sheep/api/index.js` 自动 glob 加载，无需注册）。
- 新增页面：`pages/dispatch/*.vue`，并在 `pages.json` 注册。
- 用户端接口 `/dispatch/order/**`；商家端接口 `/merchant/dispatch/order/**`、`/merchant/profile/**`。
- 运行方式：HBuilderX（用户手动运行到浏览器 / 微信小程序）。

---

### Task 1: Clone + 配置 + 基线运行

**Files:**
- Create: `D:\IDEA\yy-ui-uniapp\`（从 yudao-mall-uniapp clone）

- [ ] **Step 1: Clone**

```powershell
git clone -b master --depth 1 https://github.com/yudaocode/yudao-mall-uniapp.git "D:\IDEA\yy-ui-uniapp"
Remove-Item -Recurse -Force "D:\IDEA\yy-ui-uniapp\.git"
```

- [ ] **Step 2: 确认 `.env`**

`D:\IDEA\yy-ui-uniapp\.env` 的 `SHOPRO_DEV_BASE_URL` 应为 `http://127.0.0.1:48080`（默认已是），`SHOPRO_API_PATH=/app-api`，`SHOPRO_WEBSOCKET_PATH=/infra/ws`。若是则不改。

- [ ] **Step 3: 基线运行（用户操作）**

用 **HBuilderX** 打开 `D:\IDEA\yy-ui-uniapp`，运行到「浏览器（H5）」。确认：
- 商城首页能打开（说明工程正常）
- 能登录（会员短信登录，用 mock 或后端短信）

> 后端 yy-server 需在 `127.0.0.1:48080` 运行。

---

### Task 2: 派单 API 层

**Files:**
- Create: `D:\IDEA\yy-ui-uniapp\sheep\api\dispatch\order.js`
- Create: `D:\IDEA\yy-ui-uniapp\sheep\api\dispatch\merchant.js`

- [ ] **Step 1: 订单 API**

`sheep/api/dispatch/order.js`：
```javascript
import request from '@/sheep/request';

const DispatchOrderApi = {
  // 抢单大厅分页（待接单）
  getHallPage: (params) => {
    return request({ url: '/dispatch/order/hall-page', method: 'GET', params });
  },
  // 抢单
  acceptOrder: (id) => {
    return request({ url: '/dispatch/order/accept?id=' + id, method: 'POST' });
  },
  // 我的接单分页
  getMyPage: (params) => {
    return request({ url: '/dispatch/order/my-page', method: 'GET', params });
  },
  // 开始服务
  startOrder: (id) => {
    return request({ url: '/dispatch/order/start?id=' + id, method: 'PUT' });
  },
  // 完成服务
  finishOrder: (id) => {
    return request({ url: '/dispatch/order/finish?id=' + id, method: 'PUT' });
  },
  // 订单详情
  getOrder: (id) => {
    return request({ url: '/dispatch/order/get?id=' + id, method: 'GET' });
  },
  // 商家发单
  createOrder: (data) => {
    return request({ url: '/merchant/dispatch/order/create', method: 'POST', data });
  },
  // 商家我的发单分页
  getMerchantPage: (params) => {
    return request({ url: '/merchant/dispatch/order/my-page', method: 'GET', params });
  },
  // 商家取消
  cancelOrder: (id, reason) => {
    return request({
      url: '/merchant/dispatch/order/cancel?id=' + id + (reason ? '&reason=' + encodeURIComponent(reason) : ''),
      method: 'PUT',
    });
  },
};

export default DispatchOrderApi;
```

- [ ] **Step 2: 商家 API**

`sheep/api/dispatch/merchant.js`：
```javascript
import request from '@/sheep/request';

const DispatchMerchantApi = {
  // 提交入驻申请
  apply: (data) => {
    return request({ url: '/merchant/profile/apply', method: 'POST', data });
  },
  // 获得当前会员的商家信息（用于判断是否显示「切换商家」）
  getMy: () => {
    return request({ url: '/merchant/profile/get', method: 'GET' });
  },
};

export default DispatchMerchantApi;
```

- [ ] **Step 3: 提交（后端仓库仅存文档；前端目录另行管理）**

前端目录不在后端 git 仓库内，此任务无需在后端仓库提交。前端是否需要独立 git 由用户决定。

---

### Task 3: 页面 + 路由

**Files:**
- Create: `D:\IDEA\yy-ui-uniapp\pages\dispatch\index.vue`（身份选择/切换）
- Create: `D:\IDEA\yy-ui-uniapp\pages\dispatch\hall.vue`（抢单大厅）
- Create: `D:\IDEA\yy-ui-uniapp\pages\dispatch\my-accepted.vue`（我的接单）
- Create: `D:\IDEA\yy-ui-uniapp\pages\dispatch\publish.vue`（发单）
- Create: `D:\IDEA\yy-ui-uniapp\pages\dispatch\my-published.vue`（我的发单）
- Create: `D:\IDEA\yy-ui-uniapp\pages\dispatch\detail.vue`（订单详情）
- Modify: `D:\IDEA\yy-ui-uniapp\pages.json`（注册上述页面）

- [ ] **Step 1: 身份页**

`pages/dispatch/index.vue`：
```vue
<template>
  <view class="dispatch-identity">
    <view class="title">选择身份</view>
    <view class="card" @click="goUser">
      <view class="card-title">我是用户</view>
      <view class="card-desc">去抢单大厅接单</view>
    </view>
    <view class="card" @click="goMerchant">
      <view class="card-title">我是商家</view>
      <view class="card-desc">发布任务、管理我的发单</view>
    </view>
    <view v-if="!merchant" class="apply" @click="goApply">
      <text>还没有商家身份？去入驻</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import DispatchMerchantApi from '@/sheep/api/dispatch/merchant';

const merchant = ref(null);

onShow(async () => {
  try {
    const data = await DispatchMerchantApi.getMy();
    merchant.value = data;
  } catch (e) {
    merchant.value = null;
  }
});

const goUser = () => uni.navigateTo({ url: '/pages/dispatch/hall' });
const goMerchant = () => {
  if (!merchant.value || merchant.value.status !== 1) {
    uni.showToast({ title: '商家未通过审核', icon: 'none' });
    return;
  }
  uni.navigateTo({ url: '/pages/dispatch/my-published' });
};
const goApply = () => uni.navigateTo({ url: '/pages/dispatch/publish?apply=1' });
</script>

<style lang="scss" scoped>
.dispatch-identity { padding: 40rpx; }
.title { font-size: 40rpx; font-weight: bold; margin-bottom: 40rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 40rpx; margin-bottom: 30rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
.card-title { font-size: 34rpx; font-weight: bold; margin-bottom: 12rpx; }
.card-desc { font-size: 26rpx; color: #999; }
.apply { text-align: center; color: #2979ff; font-size: 28rpx; margin-top: 20rpx; }
</style>
```

- [ ] **Step 2: 抢单大厅**

`pages/dispatch/hall.vue`：
```vue
<template>
  <view class="hall">
    <view v-for="order in list" :key="order.id" class="order-card">
      <view class="row">
        <text class="title">{{ order.title }}</text>
        <text class="amount">￥{{ (order.amount / 100).toFixed(2) }}</text>
      </view>
      <view class="addr">{{ order.address }}</view>
      <view class="row">
        <text class="time">{{ order.createTime }}</text>
        <button size="mini" type="primary" @click="accept(order)">抢单</button>
      </view>
    </view>
    <view v-if="!list.length" class="empty">暂无可抢订单</view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import DispatchOrderApi from '@/sheep/api/dispatch/order';

const list = ref([]);

const load = async () => {
  const data = await DispatchOrderApi.getHallPage({ pageNo: 1, pageSize: 20 });
  list.value = data.list || [];
};
onShow(load);

const accept = async (order) => {
  try {
    await DispatchOrderApi.acceptOrder(order.id);
    uni.showToast({ title: '抢单成功', icon: 'success' });
    await load();
  } catch (e) {
    uni.showToast({ title: '手慢了，已被抢走', icon: 'none' });
    await load();
  }
};
</script>

<style lang="scss" scoped>
.hall { padding: 24rpx; }
.order-card { background: #fff; border-radius: 16rpx; padding: 30rpx; margin-bottom: 24rpx; }
.row { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 32rpx; font-weight: bold; }
.amount { color: #f56c6c; font-size: 32rpx; font-weight: bold; }
.addr { color: #999; font-size: 26rpx; margin: 16rpx 0; }
.time { color: #bbb; font-size: 24rpx; }
.empty { text-align: center; color: #999; margin-top: 100rpx; }
</style>
```
> WebSocket 实时刷新（收到 `dispatch.new` 插入、`dispatch.grabbed` 移除）可在 Task 3 之后追加；MVP 先用 `onShow` + 下拉刷新。WebSocket 接入见「已知风险」。

- [ ] **Step 3: 我的接单**

`pages/dispatch/my-accepted.vue`：分页列表（`getMyPage`），每条按状态显示操作按钮：`已接单(10)`→「开始服务」；`服务中(20)`→「完成服务」。点击进详情。

- [ ] **Step 4: 发单页**

`pages/dispatch/publish.vue`：表单（标题、描述、地址、联系人、电话、金额、截止时间），提交调 `createOrder`。若 `?apply=1` 则显示「商家入驻申请」表单（调 `DispatchMerchantApi.apply`）。

- [ ] **Step 5: 我的发单**

`pages/dispatch/my-published.vue`：分页列表（`getMerchantPage`），按状态显示「取消」（仅待接单）。顶部提供「去发单」入口。

- [ ] **Step 6: 订单详情**

`pages/dispatch/detail.vue`：接收 `?id=`，调 `getOrder` 显示完整信息。

- [ ] **Step 7: 注册路由**

在 `pages.json` 的 `pages` 数组追加：
```json
{ "path": "pages/dispatch/index", "style": { "navigationBarTitleText": "派单" } },
{ "path": "pages/dispatch/hall", "style": { "navigationBarTitleText": "抢单大厅" } },
{ "path": "pages/dispatch/my-accepted", "style": { "navigationBarTitleText": "我的接单" } },
{ "path": "pages/dispatch/publish", "style": { "navigationBarTitleText": "发单" } },
{ "path": "pages/dispatch/my-published", "style": { "navigationBarTitleText": "我的发单" } },
{ "path": "pages/dispatch/detail", "style": { "navigationBarTitleText": "订单详情" } }
```

- [ ] **Step 8: 入口**

在「我的」（`pages/index/user.vue` 或 tabbar 用户中心）加一个「派单」入口，跳 `/pages/dispatch/index`。

---

### Task 4: 验证（HBuilderX，用户操作）

- [ ] **Step 1: 确保后端运行**（`127.0.0.1:48080`）

- [ ] **Step 2: HBuilderX 运行到浏览器（H5）**

- [ ] **Step 3: 验证流程**

1. 会员登录（短信登录）
2. 进入「派单」→ 身份页
3. 切「用户」→ 抢单大厅 → 抢一单 → 我的接单 → 开始服务 → 完成
4. 切「商家」（需先入驻 + 后端审核通过）→ 发单 → 我的发单

> 商家入驻审核需在后台管理端操作（Plan 5 的商家管理页面）。

---

## 完成标准（Definition of Done）

- `yy-ui-uniapp` 从 yudao-mall-uniapp clone，`.env` 指向本地后端
- 派单 API 层（order/merchant）创建
- 6 个页面（身份/大厅/我的接单/发单/我的发单/详情）创建并在 `pages.json` 注册
- HBuilderX 运行后：能登录、切身份、用户抢单→开始→完成、商家发单
- 与后端真实交互成功

## 已知风险 / 备注

- **WebSocket 实时刷新**：MVP 先轮询/`onShow` 刷新；后续接 `sheep` 的 WebSocket（`SHOPRO_WEBSOCKET_PATH=/infra/ws`）实现 `dispatch.new`/`dispatch.grabbed` 实时更新。
- mall-uniapp 自带大量商城页面（商品/购物车/订单等），本计划不清理（不影响功能）；如需精简后续单独做。
- 微信小程序真机需配 `manifest.json` 的 appid + 微信开发者工具。
- 用户端接口 `/app-api/dispatch/order/**` 需登录态（member token），由 `sheep/request` 自动带 token。
