# yy 后台管理端前端 Implementation Plan（Plan 5）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 `yudao-ui-admin-vue3` 搭建 `yy` 后台管理端，添加派单模块的三个页面（商家管理、派单订单、结算管理），并连接到已运行的后端。

**Architecture:** yudao-ui-admin-vue3 是 Vue3 + element-plus + Vite 的管理后台。菜单由后端 `system_menu` 动态加载（已通过 `dispatch_menu.sql` 种入）。前端只需在 `src/api/dispatch/` 加 API 层 + `src/views/dispatch/` 加页面组件，菜单组件路径 `dispatch/merchant/index` 会自动映射到 `src/views/dispatch/merchant/index.vue`。

**Tech Stack:** Vue3 · element-plus · Vite · TypeScript · yudao-ui-admin-vue3

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`（§7.1 后台管理端）

## Global Constraints

- 前端工程在 `D:\IDEA\yy-ui-admin`（独立目录，不在后端 git 仓库内）。
- API 基地址 `http://127.0.0.1:48080`（`.env.local` 的 `VITE_BASE_URL`，已是默认值）。
- 接口前缀 `/admin-api`（`VITE_API_URL`，已是默认值）。
- API 层放 `src/api/dispatch/{merchant,order,settlement,wallet}/index.ts`。
- 页面放 `src/views/dispatch/{merchant,order,settlement,wallet}/index.vue`。
- 菜单组件路径对应：`dispatch/merchant/index` → `src/views/dispatch/merchant/index.vue`（已种入 `system_menu`）。
- 参考 yudao 的 CRUD 模式：`src/api/member/tag/index.ts`（API）、`src/views/pay/app/index.vue`（页面）。
- 用 `pnpm` 或 `npm` 安装依赖。

---

### Task 1: Clone + 配置 + 启动验证

**Files:**
- Create: `D:\IDEA\yy-ui-admin\`（从 yudao-ui-admin-vue3 clone）

- [ ] **Step 1: Clone 前端工程**

Run:
```powershell
git clone -b master --depth 1 https://github.com/yudaocode/yudao-ui-admin-vue3.git "D:\IDEA\yy-ui-admin"
Remove-Item -Recurse -Force "D:\IDEA\yy-ui-admin\.git"
```

- [ ] **Step 2: 确认 .env.local 配置**

`D:\IDEA\yy-ui-admin\.env.local` 的 `VITE_BASE_URL` 应为 `http://localhost:48080`（默认值）。如果是，不改。

- [ ] **Step 3: 安装依赖**

Run:
```powershell
cd "D:\IDEA\yy-ui-admin"; npm install
```
> 也可用 `pnpm install`（更快）。

- [ ] **Step 4: 启动前端**

确保后端 yy-server 在 `127.0.0.1:48080` 运行（`java -jar D:\IDEA\yy\yy-server\target\yy-server.jar --spring.profiles.active=local`）。

Run:
```powershell
cd "D:\IDEA\yy-ui-admin"; npm run dev
```
Expected: Vite dev server 启动，浏览器打开 `http://localhost:80`（或控制台提示的端口）。用 `admin / admin123` 登录成功。

- [ ] **Step 5: 确认派单菜单出现**

登录后左侧菜单应出现「派单管理」→「商家管理」「派单订单」（来自 `dispatch_menu.sql` 种入的数据）。点进去会 404（页面组件不存在），这是预期的——后续 Task 会补上。

---

### Task 2: API 层（4 个文件）

**Files:**
- Create: `D:\IDEA\yy-ui-admin\src\api\dispatch\merchant\index.ts`
- Create: `D:\IDEA\yy-ui-admin\src\api\dispatch\order\index.ts`
- Create: `D:\IDEA\yy-ui-admin\src\api\dispatch\settlement\index.ts`
- Create: `D:\IDEA\yy-ui-admin\src\api\dispatch\wallet\index.ts`

- [ ] **Step 1: 商家 API**

`src/api/dispatch/merchant/index.ts`：
```typescript
import request from '@/config/axios'

export interface MerchantVO {
  id: number
  memberUserId: number
  name: string
  logo: string
  contactName: string
  contactMobile: string
  status: number
  auditTime: string
  auditRemark: string
  createTime: string
}

// 商家分页
export const getDispatchMerchantPage = async (params: any) => {
  return await request.get({ url: `/dispatch/merchant/page`, params })
}

// 商家详情
export const getDispatchMerchant = async (id: number) => {
  return await request.get({ url: `/dispatch/merchant/get?id=` + id })
}

// 审核商家
export const auditDispatchMerchant = async (data: { id: number; status: number; remark?: string }) => {
  return await request.put({ url: `/dispatch/merchant/audit`, data })
}
```

- [ ] **Step 2: 订单 API**

`src/api/dispatch/order/index.ts`：
```typescript
import request from '@/config/axios'

export interface OrderVO {
  id: number
  no: string
  merchantId: number
  userId: number
  status: number
  payStatus: number
  title: string
  address: string
  amount: number
  deadline: string
  acceptTime: string
  startTime: string
  finishTime: string
  cancelReason: string
  createTime: string
}

// 订单分页
export const getDispatchOrderPage = async (params: any) => {
  return await request.get({ url: `/dispatch/order/page`, params })
}

// 订单详情
export const getDispatchOrder = async (id: number) => {
  return await request.get({ url: `/dispatch/order/get?id=` + id })
}

// 管理员强制取消
export const cancelDispatchOrder = async (id: number, reason?: string) => {
  return await request.put({ url: `/dispatch/order/cancel?id=` + id + (reason ? `&reason=${reason}` : '') })
}
```

- [ ] **Step 3: 结算 API**

`src/api/dispatch/settlement/index.ts`：
```typescript
import request from '@/config/axios'

export interface SettlementVO {
  id: number
  orderId: number
  merchantId: number
  userId: number
  amount: number
  status: number
  settleTime: string
  refundTime: string
  createTime: string
}

// 结算分页
export const getDispatchSettlementPage = async (params: any) => {
  return await request.get({ url: `/dispatch/settlement/page`, params })
}

// 结算详情
export const getDispatchSettlement = async (orderId: number) => {
  return await request.get({ url: `/dispatch/settlement/get?orderId=` + orderId })
}

// 退款
export const refundDispatchSettlement = async (orderId: number) => {
  return await request.put({ url: `/dispatch/settlement/refund?orderId=` + orderId })
}

// 手动结算（重试）
export const settleDispatchOrder = async (orderId: number) => {
  return await request.put({ url: `/dispatch/settlement/settle?orderId=` + orderId })
}
```

- [ ] **Step 4: 钱包 API**

`src/api/dispatch/wallet/index.ts`：
```typescript
import request from '@/config/axios'

// 商家充值
export const rechargeDispatchWallet = async (merchantId: number, amount: number) => {
  return await request.post({ url: `/dispatch/wallet/recharge?merchantId=` + merchantId + `&amount=` + amount })
}

// 查询余额
export const getDispatchWalletBalance = async (merchantId: number) => {
  return await request.get({ url: `/dispatch/wallet/balance?merchantId=` + merchantId })
}
```

---

### Task 3: 页面（商家管理 + 派单订单 + 结算管理）

**Files:**
- Create: `D:\IDEA\yy-ui-admin\src\views\dispatch\merchant\index.vue`
- Create: `D:\IDEA\yy-ui-admin\src\views\dispatch\order\index.vue`
- Create: `D:\IDEA\yy-ui-admin\src\views\dispatch\settlement\index.vue`

> 参考样板：`src\views\pay\app\index.vue`（yudao 标准 CRUD 页面结构：ContentWrap + el-form 搜索 + el-table 列表 + Dialog 表单）。

- [ ] **Step 1: 商家管理页面**

`src/views/dispatch/merchant/index.vue`：
- 搜索栏：商家名称（模糊）、状态（下拉：待审核/正常/禁用）
- 表格列：ID、商家名称、联系人、联系电话、状态、创建时间
- 操作列：「审核」（打开 Dialog 选 通过/禁用 + 备注）
- API：`getDispatchMerchantPage`、`auditDispatchMerchant`
- 状态用 yudao 字典或硬编码 tag（`el-tag`）

> 实现者参考 `src/views/pay/app/index.vue` 的整体结构（ContentWrap + el-table + Dialog），替换为商家数据。不需要创建/编辑/删除（商家由小程序端入驻）。

- [ ] **Step 2: 派单订单页面**

`src/views/dispatch/order/index.vue`：
- 搜索栏：商家编号、用户编号、状态（下拉）、标题（模糊）
- 表格列：ID、订单号、商家、用户、状态、标题、金额（分→元显示）、创建时间
- 操作列：「详情」（Dialog 显示完整字段）、「取消」（确认后调 `cancelDispatchOrder`）
- API：`getDispatchOrderPage`、`getDispatchOrder`、`cancelDispatchOrder`
- 状态用 `el-tag`：待接单(warning)、已接单(primary)、服务中(info)、已完成(success)、已取消(danger)

- [ ] **Step 3: 结算管理页面**

`src/views/dispatch/settlement/index.vue`：
- 搜索栏：商家编号、用户编号、状态
- 表格列：ID、订单编号、商家、用户、金额（分→元）、状态、结算时间、创建时间
- 操作列：「退款」（确认后调 `refundDispatchSettlement`）、「结算」（手动重试，调 `settleDispatchOrder`）
- API：`getDispatchSettlementPage`、`refundDispatchSettlement`、`settleDispatchOrder`
- 状态用 `el-tag`：待结算(info)、已结算(success)、已退款(warning)

---

### Task 4: 启动 + 验证

- [ ] **Step 1: 确保后端运行**

```powershell
# 启动 yy-server（如果没在跑）
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
$p = Start-Process -FilePath "java" -ArgumentList "-XX:TieredStopAtLevel=1","-Dspring.jmx.enabled=false","-jar","D:\IDEA\yy\yy-server\target\yy-server.jar","--spring.profiles.active=local" -RedirectStandardOutput "$env:TEMP\yy-server.log" -RedirectStandardError "$env:TEMP\yy-server.err.log" -PassThru -WindowStyle Hidden
$p.Id | Set-Content "$env:TEMP\yy-server.pid"
$ok=$false; for ($i=0; $i -lt 30; $i++) { Start-Sleep -Seconds 2; if (Select-String -Path "$env:TEMP\yy-server.log" -SimpleMatch "Started YyServerApplication" -Quiet -ErrorAction SilentlyContinue) { $ok=$true; break } }
"started=$ok"
```

- [ ] **Step 2: 启动前端**

```powershell
cd "D:\IDEA\yy-ui-admin"; npm run dev
```

- [ ] **Step 3: 验证**

用浏览器打开前端地址，用 `admin / admin123` 登录。验证：
1. 左侧出现「派单管理」菜单
2. 「商家管理」页面能打开，列表为空或显示测试数据
3. 「派单订单」页面能打开，列表显示之前测试创建的订单
4. 「结算管理」页面能打开，列表显示结算记录
5. 在「商家管理」点击「审核」→选择「通过」→操作成功
6. 在「派单订单」点击「取消」→确认→操作成功

> 如果菜单不出现，检查后端 `system_menu` 是否有 `id BETWEEN 90000 AND 90099` 的记录，且 `status=0`（正常）。

- [ ] **Step 4: 截图 / 记录**

记录验证结果（页面截图或文字描述），完成。

---

## 完成标准（Definition of Done）

- `yy-ui-admin` 从 yudao-ui-admin-vue3 clone，`.env.local` 指向 `http://localhost:48080`
- 4 个 API 文件 + 3 个页面文件创建完成
- 前端能启动、登录、菜单出现「派单管理」
- 商家管理（列表+审核）、派单订单（列表+详情+取消）、结算管理（列表+退款+手动结算）页面可用
- 后端 API 调用成功（数据加载/操作成功）

## 已知风险 / 备注

- 不清理 yudao-ui-admin-vue3 自带的其他模块页面（bpm/crm/erp 等）——它们不影响功能，菜单由后端控制（后端没种那些菜单就不会显示）。
- 如果 `npm install` 太慢，可用 `pnpm install` 或配淘宝镜像 `npm config set registry https://registry.npmmirror.com`。
- 菜单的 `component` 字段 `dispatch/merchant/index` 由 yudao 的动态路由机制映射到 `src/views/dispatch/merchant/index.vue`——路由是运行时从后端加载的，不需要手动配 router。
