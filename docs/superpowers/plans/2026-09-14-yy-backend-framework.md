# yy 后端框架搭建 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 yudao-boot-mini 搭建 `yy` 后端多模块工程（system / infra / member / pay），完成一键改包、数据库初始化，并成功启动。

**Architecture:** 以 yudao-boot-mini（`master-jdk17`）为底座（仅含 system + infra），从完整版 ruoyi-vue-pro 合入 `member`、`pay` 两个模块，再用官方 `ProjectReactor` 工具一键改包（groupId / artifactId / Java 包名 / 路径 / title），最后配置 MySQL + Redis、关闭多租户并启动。

**Tech Stack:** JDK 17 · Spring Boot 3.5.15 · Maven · MySQL 8 · Redis · MyBatis Plus · yudao-boot-mini（`master-jdk17`）

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`

## Global Constraints

- JDK **17**；Spring Boot **3.5.15**；Maven
- Maven groupId **`com.yy`**；artifactId 前缀 **`yy-`**；Java 包名 **`com.yy`**；根工程 revision **`1.0.0-SNAPSHOT`**
- 数据库 **MySQL 8**，库名 **`yy`**；Redis `127.0.0.1:6379` db `0`
- 多租户：**保留** `biz-tenant` starter（实体类继承 `TenantBaseDO`，删模块会编译失败），用配置 **`yy.tenant.enable: false`** 关闭
- 最终模块：`yy-dependencies`、`yy-framework`、`yy-server`、`yy-module-system`、`yy-module-infra`、`yy-module-member`、`yy-module-pay`
- 项目根目录：**`D:\IDEA\yy`**（已有 git 仓库，分支 `main`，已含 spec）
- 底座 clone 源：`https://github.com/yudaocode/yudao-boot-mini.git`，分支 `master-jdk17`
- 业务模块来源（本地完整版）：`D:\IDEA\ruoyi-vue-pro`
- SQL 来源：
  - system / infra：底座 `sql/mysql/ruoyi-vue-pro.sql` + `sql/mysql/quartz.sql`
  - member：`C:\Users\superdai\AppData\Local\Temp\MicrosoftEdgeDownloads\03ffb0ea-01c9-4c01-a771-e2367ef8a62c\member-2026-05-30-传播违法.sql`
  - pay：`C:\Users\superdai\AppData\Local\Temp\MicrosoftEdgeDownloads\ce53d2d0-2786-4367-8c14-f23922612413\pay-2026-04-18-传播违法.sql`
- MySQL 账号密码默认 `root / 123456`（若不同，替换下文命令中的 `-uroot -p123456`）
- 所有命令在 **PowerShell (Windows)** 下执行；含中文路径的命令必须用 `-LiteralPath`

## File Structure

| 路径 | 职责 |
|------|------|
| `D:\IDEA\_yy-base\` | 临时底座工作区（clone 的 mini + 合入的 member/pay），改包前 |
| `D:\IDEA\_yy-base-new\` | ProjectReactor 生成的改包后工程（临时） |
| `D:\IDEA\yy\` | 最终项目根（git 仓库） |
| `D:\IDEA\yy\sql\module\member.sql` | 归档的 member 建表脚本 |
| `D:\IDEA\yy\sql\module\pay.sql` | 归档的 pay 建表脚本 |
| `D:\IDEA\yy\pom.xml` | 根 POM（改包后：groupId `com.yy`，artifactId `yy`） |
| `D:\IDEA\yy\yy-framework\` | 框架层（改包后） |
| `D:\IDEA\yy\yy-server\src\main\resources\application-local.yaml` | 数据源 / Redis 配置 |
| `D:\IDEA\yy\yy-server\src\main\resources\application.yaml` | 全局配置（含 `yy.tenant.enable`） |

---

### Task 1: 初始化数据库 `yy`

**Files:**
- Create: `D:\IDEA\yy\sql\module\member.sql`（从下载目录归档）
- Create: `D:\IDEA\yy\sql\module\pay.sql`（从下载目录归档）

**Interfaces:**
- Produces: MySQL 数据库 `yy`，含 system/infra/member/pay/quartz 全部表

- [ ] **Step 1: 确认 MySQL 可连接**

Run:
```powershell
mysql -uroot -p123456 -e "SELECT VERSION();"
```
Expected: 输出 MySQL 版本（8.x）。若失败，先启动 MySQL 或修正账号密码。

- [ ] **Step 2: 创建数据库 `yy`**

Run:
```powershell
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS yy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```
Expected: 无输出即成功。

- [ ] **Step 3: 归档 member / pay 的 SQL 到项目**

Run:
```powershell
New-Item -ItemType Directory -Path "D:\IDEA\yy\sql\module" -Force | Out-Null
Copy-Item -LiteralPath "C:\Users\superdai\AppData\Local\Temp\MicrosoftEdgeDownloads\03ffb0ea-01c9-4c01-a771-e2367ef8a62c\member-2026-05-30-传播违法.sql" -Destination "D:\IDEA\yy\sql\module\member.sql" -Force
Copy-Item -LiteralPath "C:\Users\superdai\AppData\Local\Temp\MicrosoftEdgeDownloads\ce53d2d0-2786-4367-8c14-f23922612413\pay-2026-04-18-传播违法.sql" -Destination "D:\IDEA\yy\sql\module\pay.sql" -Force
Get-ChildItem "D:\IDEA\yy\sql\module" | Select-Object Name, Length
```
Expected: 列出 `member.sql`（约 85 KB）、`pay.sql`（约 671 KB）。

- [ ] **Step 4: 导入 system / infra 表结构**

Run:
```powershell
mysql -uroot -p123456 yy -e "source C:/Users/superdai/AppData/Local/Temp/opencode/yudao-boot-mini/sql/mysql/ruoyi-vue-pro.sql"
mysql -uroot -p123456 yy -e "source C:/Users/superdai/AppData/Local/Temp/opencode/yudao-boot-mini/sql/mysql/quartz.sql"
```
Expected: 无报错。

- [ ] **Step 5: 导入 member / pay 表结构**

Run:
```powershell
mysql -uroot -p123456 yy -e "source D:/IDEA/yy/sql/module/member.sql"
mysql -uroot -p123456 yy -e "source D:/IDEA/yy/sql/module/pay.sql"
```
Expected: 无报错。

- [ ] **Step 6: 校验关键表存在 + 统计总数**

Run:
```powershell
mysql -uroot -p123456 yy -e "SELECT COUNT(*) AS total FROM information_schema.tables WHERE table_schema='yy';"
mysql -uroot -p123456 yy -e "SHOW TABLES LIKE 'member_user'; SHOW TABLES LIKE 'pay_order'; SHOW TABLES LIKE 'system_users'; SHOW TABLES LIKE 'infra_config'; SHOW TABLES LIKE 'QRTZ_TRIGGERS';"
```
Expected: `total` 约 **84**；5 条 `SHOW TABLES` 均各返回 1 行。

- [ ] **Step 7: 提交 SQL 归档**

Run:
```powershell
git -C "D:\IDEA\yy" add sql/module
git -C "D:\IDEA\yy" commit -m "chore: 归档 member/pay 建表脚本"
```

---

### Task 2: 组装底座（clone mini + 补 test starter + 合入 member/pay）

**Files:**
- Create: `D:\IDEA\_yy-base\`（clone 的 yudao-boot-mini）
- Create: `D:\IDEA\_yy-base\yudao-framework\yudao-spring-boot-starter-test\`（从完整版补入）
- Create: `D:\IDEA\_yy-base\yudao-module-member\`、`D:\IDEA\_yy-base\yudao-module-pay\`
- Modify: `D:\IDEA\_yy-base\yudao-framework\pom.xml`（注册 test starter 模块）
- Modify: `D:\IDEA\_yy-base\pom.xml`（启用 member/pay 模块）
- Modify: `D:\IDEA\_yy-base\yudao-server\pom.xml`（引入 member/pay 依赖）

**Interfaces:**
- Consumes: Task 1（无需代码依赖）
- Produces: 一个可被 ProjectReactor 一键改包的完整底座工程（system/infra/member/pay + test starter）

- [ ] **Step 1: clone 底座到临时工作区**

Run:
```powershell
$base = "D:\IDEA\_yy-base"
if (Test-Path $base) { Remove-Item -Recurse -Force $base }
git clone -b master-jdk17 --depth 1 https://github.com/yudaocode/yudao-boot-mini.git $base
Remove-Item -Recurse -Force "$base\.git"
Get-ChildItem $base | Select-Object Name
```
Expected: 看到 `yudao-dependencies`、`yudao-framework`、`yudao-module-system`、`yudao-module-infra`、`yudao-server` 等。

- [ ] **Step 2: 从完整版补入 `yudao-spring-boot-starter-test`**

Run:
```powershell
Copy-Item -Recurse -Force "D:\IDEA\ruoyi-vue-pro\yudao-framework\yudao-spring-boot-starter-test" "D:\IDEA\_yy-base\yudao-framework\yudao-spring-boot-starter-test"
Test-Path "D:\IDEA\_yy-base\yudao-framework\yudao-spring-boot-starter-test\pom.xml"
```
Expected: `True`。

- [ ] **Step 3: 在 framework pom 注册 test starter**

编辑 `D:\IDEA\_yy-base\yudao-framework\pom.xml`，在 `<module>yudao-spring-boot-starter-biz-ip</module>` 之后新增一行：
```xml
        <module>yudao-spring-boot-starter-test</module>
```

- [ ] **Step 4: 从完整版合入 member / pay 模块源码**

Run:
```powershell
Copy-Item -Recurse -Force "D:\IDEA\ruoyi-vue-pro\yudao-module-member" "D:\IDEA\_yy-base\yudao-module-member"
Copy-Item -Recurse -Force "D:\IDEA\ruoyi-vue-pro\yudao-module-pay" "D:\IDEA\_yy-base\yudao-module-pay"
# 删除可能存在的 target，避免脏产物
Remove-Item -Recurse -Force "D:\IDEA\_yy-base\yudao-module-member\target" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "D:\IDEA\_yy-base\yudao-module-pay\target" -ErrorAction SilentlyContinue
Test-Path "D:\IDEA\_yy-base\yudao-module-member\pom.xml"; Test-Path "D:\IDEA\_yy-base\yudao-module-pay\pom.xml"
```
Expected: 两行 `True`。

- [ ] **Step 5: 启用根 pom 的 member / pay 模块**

编辑 `D:\IDEA\_yy-base\pom.xml`，把被注释的 member/pay 模块行改为启用（去掉注释）：
```xml
        <module>yudao-module-member</module>
        <module>yudao-module-pay</module>
```
（原文件中它们是 `<!--        <module>yudao-module-member</module>-->` 与 `<!--        <module>yudao-module-pay</module>-->`；其余模块的注释保持不动。）

- [ ] **Step 6: 在 server pom 引入 member / pay 依赖**

编辑 `D:\IDEA\_yy-base\yudao-server\pom.xml`，取消 `yudao-module-member`、`yudao-module-pay` 两段依赖的注释，得到：
```xml
        <!-- 会员中心 -->
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-module-member</artifactId>
            <version>${revision}</version>
        </dependency>

        <!-- 支付服务 -->
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-module-pay</artifactId>
            <version>${revision}</version>
        </dependency>
```

- [ ] **Step 7: 验证底座可编译（改包前基线）**

Run:
```powershell
mvn -f "D:\IDEA\_yy-base\pom.xml" -q -DskipTests clean compile
```
Expected: `BUILD SUCCESS`。（若失败，先修复依赖再继续；不要带着编译错误进入改包。）

---

### Task 3: 一键改包（ProjectReactor）

**Files:**
- Modify: `D:\IDEA\_yy-base\yudao-server\src\test\java\cn\iocoder\yudao\ProjectReactor.java`（填写新坐标）
- Create: `D:\IDEA\_yy-base-new\`（改包产物）
- Copy into: `D:\IDEA\yy\`

**Interfaces:**
- Consumes: Task 2 的 `_yy-base`
- Produces: 包名 `com.yy`、groupId `com.yy`、artifactId `yy-*` 的工程，复制进 `D:\IDEA\yy`

- [ ] **Step 1: 填写 ProjectReactor 的新坐标**

编辑 `D:\IDEA\_yy-base\yudao-server\src\test\java\cn\iocoder\yudao\ProjectReactor.java` 第 46-50 行的四个变量，改为：
```java
        String groupIdNew = "com.yy";
        String artifactIdNew = "yy";
        String packageNameNew = "com.yy";
        String titleNew = "yy派单系统";
        String projectBaseDirNew = projectBaseDir + "-new"; // 一键改名后，“新”项目所在的目录
```
> 注意：`projectBaseDir` 由 `System.getProperty("user.dir")` 得到，因此**必须在 `_yy-base` 目录下执行**（见 Step 2）。

- [ ] **Step 2: 运行 ProjectReactor 生成改包工程**

> 关键：`ProjectReactor` 用 `System.getProperty("user.dir")` 定位工程根，所以**必须让工作目录为 `_yy-base`**（不能靠 `mvn -f`，那不会改变 `user.dir`）。

Run:
```powershell
Push-Location "D:\IDEA\_yy-base"
mvn -pl yudao-server -am -q -DskipTests test-compile
mvn -pl yudao-server org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.mainClass=cn.iocoder.yudao.ProjectReactor" "-Dexec.classpathScope=test"
Pop-Location
```
Expected: 日志出现 `[main][重写完成]共耗时：X 秒`，且生成 `D:\IDEA\_yy-base-new\`。
> 备选：若 exec 插件不可用，用 IDEA 打开 `_yy-base`，右键运行 `ProjectReactor#main`（工作目录需为 `_yy-base`）。

- [ ] **Step 3: 校验改包结果**

Run:
```powershell
# 1) 新工程存在
Test-Path "D:\IDEA\_yy-base-new\pom.xml"
# 2) 不应再残留旧包名 / 旧 artifactId（预期均为 0）
(Get-ChildItem -Recurse -File "D:\IDEA\_yy-base-new" -Include *.java,*.xml,*.yaml | Select-String -SimpleMatch "cn.iocoder.yudao").Count
(Get-ChildItem -Recurse -File "D:\IDEA\_yy-base-new" -Include *.java,*.xml,*.yaml | Select-String -SimpleMatch "cn.iocoder.boot").Count
# 3) 新包名目录存在（改包后模块目录为 yy-server）
Test-Path "D:\IDEA\_yy-base-new\yy-server\src\main\java\com\yy"
```
Expected: `True`、`0`、`0`、`True`。
> 说明：`ProjectReactor` 会把 `yudao-*` 目录名同步改为 `yy-*`，故模块目录是 `yy-server`、`yy-module-member` 等。

- [ ] **Step 4: 把改包产物合入 `D:\IDEA\yy`（保留 .git 与 docs）**

Run:
```powershell
$src = "D:\IDEA\_yy-base-new"
$dst = "D:\IDEA\yy"
Get-ChildItem -Force $src | Where-Object { $_.Name -ne ".git" -and $_.Name -ne ".gitignore" } | ForEach-Object {
    Copy-Item -Recurse -Force $_.FullName -Destination $dst
}
Get-ChildItem $dst | Select-Object Name
```
Expected: `D:\IDEA\yy` 下出现 `pom.xml`、`yy-dependencies`、`yy-framework`、`yy-module-system`、`yy-module-infra`、`yy-module-member`、`yy-module-pay`、`yy-server`，且 `docs`、`.git`、`.gitignore`（保留本项目版本）仍在。

- [ ] **Step 5: 校验最终工程无旧包名残留**

Run:
```powershell
(Get-ChildItem -Recurse -File "D:\IDEA\yy" -Include *.java,*.xml,*.yaml -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "\\docs\\" } | Select-String -SimpleMatch "cn.iocoder").Count
```
Expected: `0`。

- [ ] **Step 6: 提交改包结果**

Run:
```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat: 基于 yudao-boot-mini 搭建 yy 后端骨架并一键改包为 com.yy"
```

---

### Task 4: 配置数据源与关闭多租户

**Files:**
- Modify: `D:\IDEA\yy\yy-server\src\main\resources\application-local.yaml`（数据源库名）
- Modify: `D:\IDEA\yy\yy-server\src\main\resources\application.yaml`（`yy.tenant.enable: false`）

**Interfaces:**
- Consumes: Task 3 的 `com.yy` 工程
- Produces: 指向库 `yy` 的数据源配置 + 关闭多租户的配置

- [ ] **Step 1: 修改数据源库名为 `yy`**

在 `D:\IDEA\yy\yy-server\src\main\resources\application-local.yaml` 中，把 master 与 slave 两处 URL 的库名 `ruoyi-vue-pro` 改为 `yy`：
```yaml
        master:
          url: jdbc:mysql://127.0.0.1:3306/yy?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true
...
        slave:
          lazy: true
          url: jdbc:mysql://127.0.0.1:3306/yy?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&nullCatalogMeansCurrent=true
```

- [ ] **Step 2: 关闭多租户**

在 `D:\IDEA\yy\yy-server\src\main\resources\application.yaml` 的 `yy:` 配置块内新增 `tenant` 节点（若 `application-local.yaml` 已存在 `yy:` 块，则加到该块；推荐加到 `application.yaml` 的全局 `yy:` 块）：
```yaml
yy:
  tenant:
    enable: false # 关闭多租户（保留 biz-tenant starter，仅禁用其自动配置）
```

- [ ] **Step 3: 校验配置项无遗漏**

Run:
```powershell
Select-String -Path "D:\IDEA\yy\yy-server\src\main\resources\application-local.yaml" -SimpleMatch "jdbc:mysql://127.0.0.1:3306/yy"
Select-String -Path "D:\IDEA\yy\yy-server\src\main\resources\application.yaml" -Pattern "tenant" -Context 0,1
```
Expected: 命中 2 条 `jdbc:mysql://.../yy`；`tenant` 命中且 `enable: false`。

- [ ] **Step 4: 提交配置**

Run:
```powershell
git -C "D:\IDEA\yy" add yy-server/src/main/resources
git -C "D:\IDEA\yy" commit -m "chore: 配置数据源指向 yy 库并关闭多租户"
```

---

### Task 5: 构建、启动、验证

**Files:**
- Verify: `D:\IDEA\yy` 全工程
- Run: `D:\IDEA\yy\yy-server`

**Interfaces:**
- Consumes: Task 1（数据库）、Task 4（配置）
- Produces: 可启动的 `yy-server`，后台登录接口可用

- [ ] **Step 1: 全量构建（跳过测试）**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 确认 Redis 可用**

Run:
```powershell
redis-cli ping
```
Expected: `PONG`。（不可用则启动 Redis 或修正 `application-local.yaml` 的 `spring.data.redis` 配置。）

- [ ] **Step 3: 启动 yy-server**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-server spring-boot:run "-Dspring-boot.run.profiles=local"
```
Expected: 日志出现 `Started YyServerApplication in X seconds`，端口 `48080`。
> 注：改包后主类名为 `YyServerApplication`（由 `YudaoServerApplication` 改名而来）。

- [ ] **Step 4: 验证健康检查**

新开一个终端，Run:
```powershell
curl.exe -s http://127.0.0.1:48080/actuator/health
```
Expected: `{"status":"UP"}`。

- [ ] **Step 5: 验证后台登录接口（system 模块）**

Run:
```powershell
curl.exe -s -X POST "http://127.0.0.1:48080/admin-api/system/auth/login" -H "Content-Type: application/json" -H "tenant-id: 1" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```
Expected: 返回 `{"code":0,...,"data":{"accessToken":"..."}}`。
> 说明：多租户已关闭，`tenant-id` 头可留可去；此处保留以兼容默认拦截器顺序。若登录返回验证码相关错误，确认 `application-local.yaml` 中 `yy.captcha.enable: false`。

- [ ] **Step 6: 验证 member 模块接口（用户端登录）**

Run:
```powershell
curl.exe -s -X POST "http://127.0.0.1:48080/app-api/member/auth/sms-login" -H "Content-Type: application/json" -H "tenant-id: 1" -d "{\"mobile\":\"13800138000\",\"code\":\"9999\"}"
```
Expected: 返回 `{"code":0,...}` 或明确的业务错误码（说明 member 路由已加载）。若 404，说明 member 模块未生效，回到 Task 2 Step 5/6 检查。

- [ ] **Step 7: 提交（如有零星修复）**

Run:
```powershell
git -C "D:\IDEA\yy" status --short
```
Expected: 无未提交改动；若有，针对修复内容单独提交。

---

## 完成标准（Definition of Done）

- `D:\IDEA\yy` 为 `com.yy` 包名的多模块工程，模块含 system / infra / member / pay
- 数据库 `yy` 含 system/infra/member/pay/quartz 全部表（约 84 张）
- `yy.tenant.enable: false` 生效，多租户关闭
- `yy-server` 可启动，`/actuator/health` 返回 UP
- 后台登录、会员登录接口可访问
- 全部改动已提交到 `main` 分支

## 已知风险 / 备注

- `ProjectReactor` 用 `replaceAll` 替换，会把配置前缀 `yudao.*` 一并改为 `yy.*`（含 `@ConfigurationProperties` 与 yaml key），属预期行为，需整体一致。
- 若 `exec-maven-plugin` 执行 `ProjectReactor` 失败，退化为在 IDEA 中运行其 `main`（工作目录必须是 `_yy-base`）。
- `member` 依赖 `yudao-spring-boot-starter-biz-ip`，`pay` 依赖 `alipay-sdk-java` / `weixin-java-pay`（由 `yy-dependencies` BOM 管理）；若 BOM 缺版本，构建会报错，需在 BOM 补齐。
- 多租户关闭后，`TenantBaseDO.tenantId` 为 null，依赖 MyBatis Plus 默认「非空才插入」策略 + 表默认值 `0`；Task 5 Step 6 若报 `tenant_id cannot be null`，需在实体或 DB 层补默认。
