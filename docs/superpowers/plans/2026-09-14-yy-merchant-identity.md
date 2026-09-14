# yy 商家领域 + 身份框架 Implementation Plan（Plan 2a）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `yy-module-dispatch` 模块，实现「商家」领域（入驻、审核、查询）与「身份框架」（会员在 `/app-api/merchant/**` 下以商家身份操作），为 Plan 2b 的派单订单打好基础。

**Architecture:** 复用 yudao 既有分层模式（Controller / Service / Mapper / DO / Convert / VO / Enums / ErrorCode）。商家不是独立账号，而是绑定在 `member_user` 上的一条档案；身份由 URL 前缀 `/app-api/merchant/**` 区分，由 `MerchantContextInterceptor` 从登录态推导商家并写入 `MerchantContextHolder`（ThreadLocal），Service 层只信任该上下文，不信任前端传参。

**Tech Stack:** JDK 17 · Spring Boot 3.5.15 · MyBatis Plus（`BaseMapperX`）· MapStruct · MySQL 8（库 `yy`）· 已建好的 `com.yy` 后端骨架（Plan 1）

**Spec:** `docs/superpowers/specs/2026-09-14-yy-dispatch-framework-design.md`（第 4 节 身份体系）

## Global Constraints

- 包名 `com.yy`；新模块 `yy-module-dispatch`，groupId `com.yy`，artifactId `yy-module-dispatch`，parent `com.yy:yy:${revision}`。
- 模块目录 `D:\IDEA\yy\yy-module-dispatch\`，Java 根包 `com.yy.module.dispatch`。
- 数据库 MySQL 8，库 `yy`，root/123456；**无多租户**（`yy.tenant.enable=false`），新表**不加 `tenant_id`**，DO 继承 `com.yy.framework.mybatis.core.dataobject.BaseDO`。
- 错误码模块号用 **`1-099-000-000`**（dispatch 自定义段）。
- Controller 前缀由包名决定：`**.controller.admin.**` → `/admin-api`；`**.controller.app.**` → `/app-api`（见 `WebProperties`）。商家端接口放 `controller.app.merchant.*`，路径即 `/app-api/merchant/**`。
- 用户/商家登录主体是 `member_user`；商家身份不重新登录。
- 商家状态：`0 待审核 / 1 正常 / 2 禁用`（`DispatchMerchantStatusEnum`）。
- 代码风格沿用 yudao：`@Tag/@Operation/@PreAuthorize("@ss.hasPermission('...')")`、`CommonResult.success(...)`、MapStruct `XxxConvert.INSTANCE`、`ServiceExceptionUtil.exception(...)`。
- 命令在 Windows PowerShell 5.1 执行；`mvn` 3.9.4、JDK 21（source/target 17）。
- 参考样板（同仓库，改包后）：
  - DO：`yy-module-member\...\dal\dataobject\group\MemberGroupDO.java`
  - Mapper：`yy-module-member\...\dal\mysql\group\MemberGroupMapper.java`
  - Service/Impl：`yy-module-member\...\service\group\MemberGroupService(Impl).java`
  - Convert：`yy-module-member\...\convert\group\MemberGroupConvert.java`
  - Admin Controller：`yy-module-member\...\controller\admin\group\MemberGroupController.java`
  - ErrorCode：`yy-module-member\...\enums\ErrorCodeConstants.java`
  - Web 配置：`yy-module-member\...\framework\web\config\MemberWebConfiguration.java`

---

### Task 1: 新建 `yy-module-dispatch` 模块 + 建表 + 字典菜单

**Files:**
- Create: `D:\IDEA\yy\yy-module-dispatch\pom.xml`
- Create: `D:\IDEA\yy\yy-module-dispatch\src\main\java\com\yy\module\dispatch\package-info.java`
- Create: `D:\IDEA\yy\yy-module-dispatch\src\main\java\com\yy\module\dispatch\framework\web\config\DispatchWebConfiguration.java`
- Create: `D:\IDEA\yy\sql\module\dispatch_merchant.sql`
- Modify: `D:\IDEA\yy\pom.xml`（`<modules>` 增加 `yy-module-dispatch`）
- Modify: `D:\IDEA\yy\yy-server\pom.xml`（增加 `yy-module-dispatch` 依赖）

**Interfaces:**
- Produces: 可编译的 `yy-module-dispatch` 模块；数据库表 `dispatch_merchant`；`DispatchWebConfiguration` 的 Swagger 分组

- [ ] **Step 1: 创建模块 pom**

创建 `D:\IDEA\yy\yy-module-dispatch\pom.xml`，内容：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.yy</groupId>
        <artifactId>yy</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>yy-module-dispatch</artifactId>
    <packaging>jar</packaging>

    <name>${project.artifactId}</name>
    <description>
        dispatch 模块，派单业务。
        商家发单、用户抢单、订单流转、结算。
    </description>

    <dependencies>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-module-system</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-module-infra</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-module-member</artifactId>
            <version>${revision}</version>
        </dependency>

        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-spring-boot-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-spring-boot-starter-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-spring-boot-starter-mq</artifactId>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-spring-boot-starter-excel</artifactId>
        </dependency>
    </dependencies>

</project>
```

- [ ] **Step 2: 创建包信息与 Web 配置**

`D:\IDEA\yy\yy-module-dispatch\src\main\java\com\yy\module\dispatch\package-info.java`：
```java
/**
 * dispatch 模块，派单业务。
 */
package com.yy.module.dispatch;
```

`...\framework\web\config\DispatchWebConfiguration.java`：
```java
package com.yy.module.dispatch.framework.web.config;

import com.yy.framework.swagger.config.YySwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * dispatch 模块的 web 组件 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class DispatchWebConfiguration {

    @Bean
    public GroupedOpenApi dispatchGroupedOpenApi() {
        return YySwaggerAutoConfiguration.buildGroupedOpenApi("dispatch");
    }

}
```

- [ ] **Step 3: 建表 SQL**

创建 `D:\IDEA\yy\sql\module\dispatch_merchant.sql`：
```sql
DROP TABLE IF EXISTS `dispatch_merchant`;
CREATE TABLE `dispatch_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_user_id` bigint NOT NULL COMMENT '会员用户编号',
  `name` varchar(64) NOT NULL COMMENT '商家名称',
  `logo` varchar(255) NOT NULL DEFAULT '' COMMENT '商家 Logo',
  `contact_name` varchar(32) NOT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) NOT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1正常 2禁用',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(255) NOT NULL DEFAULT '' COMMENT '审核备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_member_user_id` (`member_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单商家';
```

导入：
```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 yy -e "source D:/IDEA/yy/sql/module/dispatch_merchant.sql"
mysql -uroot -p123456 yy -e "SHOW TABLES LIKE 'dispatch_merchant';"
```
Expected: 返回 1 行 `dispatch_merchant`。

- [ ] **Step 4: 注册模块**

在 `D:\IDEA\yy\pom.xml` 的 `<modules>` 中，`<module>yy-module-pay</module>` 之后新增：
```xml
        <module>yy-module-dispatch</module>
```
在 `D:\IDEA\yy\yy-server\pom.xml` 的 `yy-module-pay` 依赖块之后新增：
```xml
        <!-- 派单服务 -->
        <dependency>
            <groupId>com.yy</groupId>
            <artifactId>yy-module-dispatch</artifactId>
            <version>${revision}</version>
        </dependency>
```

- [ ] **Step 5: 编译验证**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS（`yy-module-dispatch` 出现在 reactor 中）。

- [ ] **Step 6: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 新建 yy-module-dispatch 模块与 dispatch_merchant 建表"
```

---

### Task 2: 商家领域（枚举 / 错误码 / DO / Mapper / Service）+ 单测

**Files:**
- Create: `...\enums\ErrorCodeConstants.java`
- Create: `...\enums\merchant\DispatchMerchantStatusEnum.java`
- Create: `...\dal\dataobject\merchant\DispatchMerchantDO.java`
- Create: `...\dal\mysql\merchant\DispatchMerchantMapper.java`
- Create: `...\service\merchant\DispatchMerchantService.java`
- Create: `...\service\merchant\DispatchMerchantServiceImpl.java`
- Create: `...\controller\admin\merchant\vo\DispatchMerchantSaveReqVO.java`
- Create: `...\controller\admin\merchant\vo\DispatchMerchantPageReqVO.java`
- Test: `...\yy-module-dispatch\src\test\java\com\yy\module\dispatch\service\merchant\DispatchMerchantServiceTest.java`

**Interfaces:**
- Consumes: Task 1（模块与表）
- Produces:
  - `DispatchMerchantDO`（字段见下）
  - `DispatchMerchantService.applyMerchant(Long memberUserId, DispatchMerchantSaveReqVO)` → `Long`
  - `DispatchMerchantService.auditMerchant(Long id, Integer status, String remark)`
  - `DispatchMerchantService.getMerchantByMemberUserId(Long memberUserId)` → `DispatchMerchantDO`
  - `DispatchMerchantService.getMerchant(Long id)` → `DispatchMerchantDO`
  - `DispatchMerchantService.getMerchantPage(DispatchMerchantPageReqVO)` → `PageResult<DispatchMerchantDO>`
  - `DispatchMerchantStatusEnum`

- [ ] **Step 1: 错误码**

`...\enums\ErrorCodeConstants.java`：
```java
package com.yy.module.dispatch.enums;

import com.yy.framework.common.exception.ErrorCode;

/**
 * Dispatch 错误码枚举类
 *
 * dispatch 系统，使用 1-099-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 商家 1-099-001-000 ==========
    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_099_001_000, "商家不存在");
    ErrorCode MERCHANT_APPLY_EXISTS = new ErrorCode(1_099_001_001, "该会员已提交过商家入驻申请");
    ErrorCode MERCHANT_NOT_ENABLED = new ErrorCode(1_099_001_002, "商家状态非正常，无法操作");
    ErrorCode MERCHANT_STATUS_ERROR = new ErrorCode(1_099_001_003, "商家审核状态不合法");

}
```

- [ ] **Step 2: 商家状态枚举**

`...\enums\merchant\DispatchMerchantStatusEnum.java`：
```java
package com.yy.module.dispatch.enums.merchant;

import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 商家状态枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchMerchantStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待审核"),
    ENABLED(1, "正常"),
    DISABLED(2, "禁用");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchMerchantStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
```

- [ ] **Step 3: DO**

`...\dal\dataobject\merchant\DispatchMerchantDO.java`（`@TableName("dispatch_merchant")`，继承 `BaseDO`）：
```java
package com.yy.module.dispatch.dal.dataobject.merchant;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 派单商家 DO
 */
@TableName("dispatch_merchant")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchMerchantDO extends BaseDO {

    @TableId
    private Long id;
    /** 会员用户编号 */
    private Long memberUserId;
    /** 商家名称 */
    private String name;
    /** 商家 Logo */
    private String logo;
    /** 联系人 */
    private String contactName;
    /** 联系电话 */
    private String contactMobile;
    /** 状态，见 DispatchMerchantStatusEnum */
    private Integer status;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 审核备注 */
    private String auditRemark;

}
```

- [ ] **Step 4: Mapper**

`...\dal\mysql\merchant\DispatchMerchantMapper.java`：
```java
package com.yy.module.dispatch.dal.mysql.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantPageReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispatchMerchantMapper extends BaseMapperX<DispatchMerchantDO> {

    default DispatchMerchantDO selectByMemberUserId(Long memberUserId) {
        return selectOne(DispatchMerchantDO::getMemberUserId, memberUserId);
    }

    default PageResult<DispatchMerchantDO> selectPage(DispatchMerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DispatchMerchantDO>()
                .likeIfPresent(DispatchMerchantDO::getName, reqVO.getName())
                .eqIfPresent(DispatchMerchantDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DispatchMerchantDO::getMemberUserId, reqVO.getMemberUserId())
                .orderByDesc(DispatchMerchantDO::getId));
    }
}
```

- [ ] **Step 5: VO（管理端）**

`...\controller\admin\merchant\vo\DispatchMerchantSaveReqVO.java`：
```java
package com.yy.module.dispatch.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 商家创建/更新 Request VO")
@Data
public class DispatchMerchantSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "商家名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张记跑腿")
    @NotEmpty(message = "商家名称不能为空")
    private String name;

    @Schema(description = "商家 Logo")
    private String logo;

    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系人不能为空")
    private String contactName;

    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系电话不能为空")
    private String contactMobile;

    @Schema(description = "会员用户编号（管理端创建时必填）")
    private Long memberUserId;
}
```

`...\controller\admin\merchant\vo\DispatchMerchantPageReqVO.java`：
```java
package com.yy.module.dispatch.controller.admin.merchant.vo;

import com.yy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商家分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DispatchMerchantPageReqVO extends PageParam {

    @Schema(description = "商家名称", example = "张记")
    private String name;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "会员用户编号", example = "1024")
    private Long memberUserId;
}
```

- [ ] **Step 6: Service 接口 + 实现**

`...\service\merchant\DispatchMerchantService.java`：
```java
package com.yy.module.dispatch.service.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantPageReqVO;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;

public interface DispatchMerchantService {

    /** 会员提交入驻申请（status=待审核） */
    Long applyMerchant(Long memberUserId, DispatchMerchantSaveReqVO reqVO);

    /** 管理端审核：status 1 通过 / 2 禁用 */
    void auditMerchant(Long id, Integer status, String remark);

    /** 按会员编号查询商家 */
    DispatchMerchantDO getMerchantByMemberUserId(Long memberUserId);

    DispatchMerchantDO getMerchant(Long id);

    PageResult<DispatchMerchantDO> getMerchantPage(DispatchMerchantPageReqVO reqVO);

}
```

`...\service\merchant\DispatchMerchantServiceImpl.java`：
```java
package com.yy.module.dispatch.service.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantPageReqVO;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.dal.mysql.merchant.DispatchMerchantMapper;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DispatchMerchantServiceImpl implements DispatchMerchantService {

    @Resource
    private DispatchMerchantMapper merchantMapper;

    @Override
    public Long applyMerchant(Long memberUserId, DispatchMerchantSaveReqVO reqVO) {
        // 一个会员仅可入驻一次
        if (merchantMapper.selectByMemberUserId(memberUserId) != null) {
            throw exception(MERCHANT_APPLY_EXISTS);
        }
        DispatchMerchantDO merchant = DispatchMerchantDO.builder()
                .memberUserId(memberUserId)
                .name(reqVO.getName())
                .logo(reqVO.getLogo())
                .contactName(reqVO.getContactName())
                .contactMobile(reqVO.getContactMobile())
                .status(DispatchMerchantStatusEnum.PENDING.getStatus())
                .build();
        merchantMapper.insert(merchant);
        return merchant.getId();
    }

    @Override
    public void auditMerchant(Long id, Integer status, String remark) {
        validateMerchantExists(id);
        if (!DispatchMerchantStatusEnum.ENABLED.getStatus().equals(status)
                && !DispatchMerchantStatusEnum.DISABLED.getStatus().equals(status)) {
            throw exception(MERCHANT_STATUS_ERROR);
        }
        DispatchMerchantDO update = DispatchMerchantDO.builder()
                .id(id).status(status).auditTime(LocalDateTime.now()).auditRemark(remark)
                .build();
        merchantMapper.updateById(update);
    }

    @Override
    public DispatchMerchantDO getMerchantByMemberUserId(Long memberUserId) {
        return merchantMapper.selectByMemberUserId(memberUserId);
    }

    @Override
    public DispatchMerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public PageResult<DispatchMerchantDO> getMerchantPage(DispatchMerchantPageReqVO reqVO) {
        return merchantMapper.selectPage(reqVO);
    }

    private DispatchMerchantDO validateMerchantExists(Long id) {
        DispatchMerchantDO merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }
}
```

- [ ] **Step 7: 单元测试（先写失败）**

`...\yy-module-dispatch\src\test\java\com\yy\module\dispatch\service\merchant\DispatchMerchantServiceTest.java`（沿用 yudao `BaseDbUnitTest` 模式，参考 `yy-module-member\src\test\java\com\yy\module\member\service\group\MemberGroupServiceTest.java`）：
```java
package com.yy.module.dispatch.service.merchant;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.dal.mysql.merchant.DispatchMerchantMapper;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_APPLY_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

@Import(DispatchMerchantServiceImpl.class)
public class DispatchMerchantServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchMerchantService merchantService;
    @Resource
    private DispatchMerchantMapper merchantMapper;

    private DispatchMerchantSaveReqVO buildReqVO() {
        DispatchMerchantSaveReqVO vo = new DispatchMerchantSaveReqVO();
        vo.setName("张记跑腿");
        vo.setContactName("张三");
        vo.setContactMobile("13800138000");
        return vo;
    }

    @Test
    public void testApplyMerchant_success() {
        Long id = merchantService.applyMerchant(1001L, buildReqVO());
        assertNotNull(id);
        DispatchMerchantDO merchant = merchantMapper.selectById(id);
        assertEquals(1001L, merchant.getMemberUserId());
        assertEquals(DispatchMerchantStatusEnum.PENDING.getStatus(), merchant.getStatus());
    }

    @Test
    public void testApplyMerchant_duplicate() {
        merchantService.applyMerchant(1001L, buildReqVO());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> merchantService.applyMerchant(1001L, buildReqVO()));
        assertEquals(MERCHANT_APPLY_EXISTS.getCode(), ex.getCode());
    }

    @Test
    public void testAuditMerchant_enable() {
        Long id = merchantService.applyMerchant(1001L, buildReqVO());
        merchantService.auditMerchant(id, DispatchMerchantStatusEnum.ENABLED.getStatus(), "ok");
        assertEquals(DispatchMerchantStatusEnum.ENABLED.getStatus(),
                merchantMapper.selectById(id).getStatus());
    }
}
```

- [ ] **Step 8: 建测试表结构（H2）**

在 `...\yy-module-dispatch\src\test\resources\sql\create_tables.sql` 增加 `dispatch_merchant` 的 H2 建表（参考 `yy-module-member\src\test\resources\sql\create_tables.sql` 的 H2 语法；字段与 MySQL 一致，去掉 `ENGINE/CHARSET`）：
```sql
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
    PRIMARY KEY ("id")
);
```

- [ ] **Step 9: 运行测试**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am -Dtest=DispatchMerchantServiceTest test
```
Expected: 3 个测试全部通过。

- [ ] **Step 10: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 商家领域 DO/Mapper/Service 与单测"
```

---

### Task 3: 身份框架（MerchantContextHolder / @MerchantIdentity / Interceptor）

**Files:**
- Create: `...\framework\merchant\core\MerchantContextHolder.java`
- Create: `...\framework\merchant\core\MerchantIdentity.java`
- Create: `...\framework\merchant\core\MerchantContextInterceptor.java`
- Modify: `...\framework\web\config\DispatchWebConfiguration.java`（注册拦截器）
- Test: `...\src\test\java\com\yy\module\dispatch\framework\merchant\MerchantContextInterceptorTest.java`

**Interfaces:**
- Consumes: `DispatchMerchantService.getMerchantByMemberUserId`（Task 2）、`SecurityFrameworkUtils.getLoginUserId()`、`WebProperties`
- Produces: `MerchantContextHolder.getMerchantId()` / `setMerchantId(Long)` / `clear()`；`@MerchantIdentity` 注解；拦截 `/app-api/merchant/**` 的拦截器

- [ ] **Step 1: MerchantContextHolder**

`...\framework\merchant\core\MerchantContextHolder.java`：
```java
package com.yy.module.dispatch.framework.merchant.core;

/**
 * 商家上下文 Holder，基于 ThreadLocal
 *
 * 仅由 MerchantContextInterceptor 写入，业务代码只读取。
 */
public class MerchantContextHolder {

    private static final ThreadLocal<Long> MERCHANT_ID = new ThreadLocal<>();

    public static void setMerchantId(Long merchantId) {
        MERCHANT_ID.set(merchantId);
    }

    public static Long getMerchantId() {
        return MERCHANT_ID.get();
    }

    public static void clear() {
        MERCHANT_ID.remove();
    }
}
```

- [ ] **Step 2: @MerchantIdentity 注解**

`...\framework\merchant\core\MerchantIdentity.java`：
```java
package com.yy.module.dispatch.framework.merchant.core;

import java.lang.annotation.*;

/**
 * 标记商家身份接口：请求必须是「正常状态」的商家
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MerchantIdentity {
}
```

- [ ] **Step 3: Interceptor**

`...\framework\merchant\core\MerchantContextInterceptor.java`：
```java
package com.yy.module.dispatch.framework.merchant.core;

import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_NOT_ENABLED;

/**
 * 商家身份拦截器：拦截 /app-api/merchant/**，从登录态推导商家并写入上下文
 *
 * 只信任登录态，不信任前端传入的 merchantId。
 *
 * 注意：本类由 DispatchWebConfiguration 用 new 创建，故依赖走「构造器注入」，
 * 不能用 @Resource 字段注入（new 出来的对象不会被 Spring 注入字段）。
 */
public class MerchantContextInterceptor implements HandlerInterceptor {

    private final DispatchMerchantService merchantService;

    public MerchantContextInterceptor(DispatchMerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 仅处理标注了 @MerchantIdentity 的接口
        boolean needMerchant = handlerMethod.hasMethodAnnotation(MerchantIdentity.class)
                || handlerMethod.getBeanType().isAnnotationPresent(MerchantIdentity.class);
        if (!needMerchant) {
            return true;
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        DispatchMerchantDO merchant = merchantService.getMerchantByMemberUserId(userId);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        if (!DispatchMerchantStatusEnum.ENABLED.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_NOT_ENABLED);
        }
        MerchantContextHolder.setMerchantId(merchant.getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MerchantContextHolder.clear();
    }
}
```

- [ ] **Step 4: 注册拦截器**

修改 `...\framework\web\config\DispatchWebConfiguration.java`，改为：
```java
package com.yy.module.dispatch.framework.web.config;

import com.yy.framework.swagger.config.YySwaggerAutoConfiguration;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextInterceptor;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * dispatch 模块的 web 组件 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class DispatchWebConfiguration implements WebMvcConfigurer {

    @Bean
    public GroupedOpenApi dispatchGroupedOpenApi() {
        return YySwaggerAutoConfiguration.buildGroupedOpenApi("dispatch");
    }

    @Bean
    public MerchantContextInterceptor merchantContextInterceptor(DispatchMerchantService merchantService) {
        return new MerchantContextInterceptor(merchantService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(merchantContextInterceptor())
                .addPathPatterns("/app-api/merchant/**");
    }

}
```

- [ ] **Step 5: 单测（拦截器行为）**

`...\src\test\java\com\yy\module\dispatch\framework\merchant\MerchantContextInterceptorTest.java`：
```java
package com.yy.module.dispatch.framework.merchant;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextHolder;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextInterceptor;
import com.yy.module.dispatch.framework.merchant.core.MerchantIdentity;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.method.HandlerMethod;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_NOT_ENABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MerchantContextInterceptorTest {

    @AfterEach
    public void tearDown() {
        MerchantContextHolder.clear();
    }

    @Test
    public void testPreHandle_nonHandler() {
        MerchantContextInterceptor interceptor = new MerchantContextInterceptor(mock(DispatchMerchantService.class));
        assertTrue(interceptor.preHandle(null, null, new Object()));
    }

    @Test
    public void testPreHandle_merchantEnabled() throws Exception {
        DispatchMerchantService service = mock(DispatchMerchantService.class);
        when(service.getMerchantByMemberUserId(1001L)).thenReturn(
                DispatchMerchantDO.builder().id(2001L)
                        .status(DispatchMerchantStatusEnum.ENABLED.getStatus()).build());
        MerchantContextInterceptor interceptor = new MerchantContextInterceptor(service);
        HandlerMethod handlerMethod = new HandlerMethod(new FakeMerchantController(),
                FakeMerchantController.class.getMethod("doSomething"));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            assertTrue(interceptor.preHandle(null, null, handlerMethod));
            assertEquals(2001L, MerchantContextHolder.getMerchantId());
        }
    }

    @Test
    public void testPreHandle_merchantNotEnabled() throws Exception {
        DispatchMerchantService service = mock(DispatchMerchantService.class);
        when(service.getMerchantByMemberUserId(1001L)).thenReturn(
                DispatchMerchantDO.builder().id(2001L)
                        .status(DispatchMerchantStatusEnum.PENDING.getStatus()).build());
        MerchantContextInterceptor interceptor = new MerchantContextInterceptor(service);
        HandlerMethod handlerMethod = new HandlerMethod(new FakeMerchantController(),
                FakeMerchantController.class.getMethod("doSomething"));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(null, null, handlerMethod));
            assertEquals(MERCHANT_NOT_ENABLED.getCode(), ex.getCode());
        }
    }

    static class FakeMerchantController {
        @MerchantIdentity
        public void doSomething() {
        }
    }
}
```
> 备注：**不要**用 `SecurityFrameworkUtils.setLoginUser(loginUser, null)` 来伪造登录态——它内部 `buildAuthentication` 会用 request，传 null 会 NPE。用 `MockedStatic<SecurityFrameworkUtils>` 静态 mock `getLoginUserId()`（需 `mockito-inline`，BOM 已含）。三条分支：非 Handler 放行、商家正常写入上下文、商家非正常抛 `MERCHANT_NOT_ENABLED`。

- [ ] **Step 6: 运行测试**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -pl yy-module-dispatch -am -Dtest=MerchantContextInterceptorTest test
```
Expected: 通过（若 Step 5 备注触发调整，仍须覆盖三分支）。

- [ ] **Step 7: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 商家身份框架（上下文/Holder/拦截器）"
```

---

### Task 4: 商家 Controller（管理端审核 + 小程序端入驻）

**Files:**
- Create: `...\controller\admin\merchant\DispatchMerchantController.java`
- Create: `...\controller\admin\merchant\vo\DispatchMerchantAuditReqVO.java`
- Create: `...\controller\admin\merchant\vo\DispatchMerchantRespVO.java`
- Create: `...\convert\merchant\DispatchMerchantConvert.java`
- Create: `...\controller\app\merchant\AppDispatchMerchantController.java`
- Create: `...\controller\app\merchant\vo\AppDispatchMerchantApplyReqVO.java`
- Create: `...\controller\app\merchant\vo\AppDispatchMerchantRespVO.java`

**Interfaces:**
- Consumes: Task 2 的 Service、Task 3 的 `@MerchantIdentity` / `MerchantContextHolder`
- Produces:
  - 管理端：`POST /admin-api/dispatch/merchant/audit`、`GET /admin-api/dispatch/merchant/get`、`GET /admin-api/dispatch/merchant/page`
  - 小程序端：`POST /app-api/merchant/profile/apply`、`GET /app-api/merchant/profile/get`

- [ ] **Step 1: 管理端 VO + Convert**

`DispatchMerchantAuditReqVO`：
```java
package com.yy.module.dispatch.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 商家审核 Request VO")
@Data
public class DispatchMerchantAuditReqVO {

    @Schema(description = "商家编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2001")
    @NotNull(message = "商家编号不能为空")
    private Long id;

    @Schema(description = "审核状态：1 通过 2 禁用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "审核状态不能为空")
    private Integer status;

    @Schema(description = "审核备注", example = "资料齐全")
    private String remark;
}
```

`DispatchMerchantRespVO`：
```java
package com.yy.module.dispatch.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商家 Response VO")
@Data
public class DispatchMerchantRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "会员用户编号")
    private Long memberUserId;
    @Schema(description = "商家名称")
    private String name;
    @Schema(description = "商家 Logo")
    private String logo;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系电话")
    private String contactMobile;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "审核时间")
    private LocalDateTime auditTime;
    @Schema(description = "审核备注")
    private String auditRemark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
```

`DispatchMerchantConvert`：
```java
package com.yy.module.dispatch.convert.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantRespVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface DispatchMerchantConvert {

    DispatchMerchantConvert INSTANCE = Mappers.getMapper(DispatchMerchantConvert.class);

    DispatchMerchantRespVO convert(DispatchMerchantDO bean);

    List<DispatchMerchantRespVO> convertList(List<DispatchMerchantDO> list);

    PageResult<DispatchMerchantRespVO> convertPage(PageResult<DispatchMerchantDO> page);
}
```

- [ ] **Step 2: 管理端 Controller**

`...\controller\admin\merchant\DispatchMerchantController.java`：
```java
package com.yy.module.dispatch.controller.admin.merchant;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.*;
import com.yy.module.dispatch.convert.merchant.DispatchMerchantConvert;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 派单商家")
@RestController
@RequestMapping("/dispatch/merchant")
@Validated
public class DispatchMerchantController {

    @Resource
    private DispatchMerchantService merchantService;

    @PutMapping("/audit")
    @Operation(summary = "审核商家")
    @PreAuthorize("@ss.hasPermission('dispatch:merchant:audit')")
    public CommonResult<Boolean> auditMerchant(@Valid @RequestBody DispatchMerchantAuditReqVO reqVO) {
        merchantService.auditMerchant(reqVO.getId(), reqVO.getStatus(), reqVO.getRemark());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得商家")
    @Parameter(name = "id", description = "编号", required = true, example = "2001")
    @PreAuthorize("@ss.hasPermission('dispatch:merchant:query')")
    public CommonResult<DispatchMerchantRespVO> getMerchant(@RequestParam("id") Long id) {
        DispatchMerchantDO merchant = merchantService.getMerchant(id);
        return success(DispatchMerchantConvert.INSTANCE.convert(merchant));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商家分页")
    @PreAuthorize("@ss.hasPermission('dispatch:merchant:query')")
    public CommonResult<PageResult<DispatchMerchantRespVO>> getMerchantPage(@Valid DispatchMerchantPageReqVO reqVO) {
        PageResult<DispatchMerchantDO> page = merchantService.getMerchantPage(reqVO);
        return success(DispatchMerchantConvert.INSTANCE.convertPage(page));
    }
}
```

- [ ] **Step 3: 小程序端 VO + Controller**

`AppDispatchMerchantApplyReqVO`（复用 `DispatchMerchantSaveReqVO` 字段，但不要 `id`/`memberUserId`，由登录态注入）：
```java
package com.yy.module.dispatch.controller.app.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "小程序 - 商家入驻申请 Request VO")
@Data
public class AppDispatchMerchantApplyReqVO {

    @Schema(description = "商家名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张记跑腿")
    @NotEmpty(message = "商家名称不能为空")
    private String name;

    @Schema(description = "商家 Logo")
    private String logo;

    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系人不能为空")
    private String contactName;

    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系电话不能为空")
    private String contactMobile;
}
```

`AppDispatchMerchantRespVO`：
```java
package com.yy.module.dispatch.controller.app.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "小程序 - 商家信息 Response VO")
@Data
public class AppDispatchMerchantRespVO {

    @Schema(description = "商家编号")
    private Long id;
    @Schema(description = "商家名称")
    private String name;
    @Schema(description = "商家 Logo")
    private String logo;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系电话")
    private String contactMobile;
    @Schema(description = "状态：0待审核 1正常 2禁用")
    private Integer status;
}
```

`...\controller\app\merchant\AppDispatchMerchantController.java`：
```java
package com.yy.module.dispatch.controller.app.merchant;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.controller.app.merchant.vo.AppDispatchMerchantApplyReqVO;
import com.yy.module.dispatch.controller.app.merchant.vo.AppDispatchMerchantRespVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextHolder;
import com.yy.module.dispatch.framework.merchant.core.MerchantIdentity;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "小程序 - 商家入驻与信息")
@RestController
@RequestMapping("/merchant/profile")
@Validated
public class AppDispatchMerchantController {

    @Resource
    private DispatchMerchantService merchantService;

    @PostMapping("/apply")
    @Operation(summary = "提交商家入驻申请")
    public CommonResult<Long> applyMerchant(@Valid @RequestBody AppDispatchMerchantApplyReqVO reqVO) {
        DispatchMerchantSaveReqVO saveReqVO = new DispatchMerchantSaveReqVO();
        BeanUtils.copyProperties(reqVO, saveReqVO);
        Long id = merchantService.applyMerchant(SecurityFrameworkUtils.getLoginUserId(), saveReqVO);
        return success(id);
    }

    @GetMapping("/get")
    @Operation(summary = "获得当前登录会员的商家信息")
    public CommonResult<AppDispatchMerchantRespVO> getMerchant() {
        DispatchMerchantDO merchant = merchantService.getMerchantByMemberUserId(SecurityFrameworkUtils.getLoginUserId());
        if (merchant == null) {
            return success(null);
        }
        AppDispatchMerchantRespVO vo = new AppDispatchMerchantRespVO();
        BeanUtils.copyProperties(merchant, vo);
        return success(vo);
    }
}
```
> 说明：`/merchant/profile/get` 用于前端判断是否显示「切换到商家」，**不需要** `@MerchantIdentity`（未通过审核也要能查到自己的状态）。

- [ ] **Step 4: 编译**

Run:
```powershell
mvn -f "D:\IDEA\yy\pom.xml" -q -DskipTests clean install
```
Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```powershell
git -C "D:\IDEA\yy" add -A
git -C "D:\IDEA\yy" commit -m "feat(dispatch): 商家管理端审核与小程序端入驻接口"
```

---

### Task 5: 集成验证（启动 + 入驻 → 审核 → 身份拦截）

**Files:**
- Verify: `D:\IDEA\yy` 全工程
- Create: `D:\IDEA\yy\sql\module\dispatch_menu.sql`（管理端菜单，可选但推荐）

**Interfaces:**
- Consumes: Task 1–4 全部
- Produces: 可运行、可端到端验证的商家与身份能力

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
$ok=$false; for ($i=0; $i -lt 20; $i++) { Start-Sleep -Seconds 3; if (Select-String -Path $log -SimpleMatch "Started YyServerApplication" -Quiet -ErrorAction SilentlyContinue) { $ok=$true; break } }
"started=$ok"
if (-not $ok) { Get-Content $log -Tail 40 }
```
Expected: `started=True`。（注意：不要用 `$pid` 变量名，它是 PowerShell 保留变量。）

- [ ] **Step 3: 管理员登录拿 token**

Run:
```powershell
$login = Invoke-RestMethod -Uri "http://127.0.0.1:48080/admin-api/system/auth/login" -Method POST -ContentType "application/json" -Headers @{ "tenant-id"="1" } -Body '{"username":"admin","password":"admin123"}'
$token = $login.data.accessToken
"token=$token"
```
Expected: 打印非空 token。

- [ ] **Step 4: 管理端商家分页接口可用**

Run:
```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:48080/admin-api/dispatch/merchant/page?pageNo=1&pageSize=10" -Headers @{ "Authorization"="Bearer $token"; "tenant-id"="1" } | ConvertTo-Json -Compress
```
Expected: `{"code":0,...,"data":{"list":[],"total":0}}`（无数据但接口存在）。

- [ ] **Step 5: 未登录访问商家身份接口被拒**

Run:
```powershell
try { Invoke-RestMethod -Uri "http://127.0.0.1:48080/app-api/merchant/profile/get" -Headers @{ "tenant-id"="1" } -ErrorAction Stop } catch { $_.Exception.Message; $_.ErrorDetails.Message }
```
Expected: 返回未认证/401 类错误（证明该路径受登录保护）。

- [ ] **Step 6: 关停服务器**

Run:
```powershell
$srvPid = Get-Content "$env:TEMP\yy-server.pid" -ErrorAction SilentlyContinue
if ($srvPid) { Stop-Process -Id $srvPid -Force -ErrorAction SilentlyContinue }
Get-NetTCPConnection -LocalPort 48080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
```

- [ ] **Step 7: 提交（如有）

```powershell
git -C "D:\IDEA\yy" status --short
```
Expected: 无未提交改动，或仅针对修复内容提交。

---

## 完成标准（Definition of Done）

- `yy-module-dispatch` 已注册进 reactor 与 `yy-server`，全量构建 BUILD SUCCESS
- 表 `dispatch_merchant` 存在；`DispatchMerchantStatusEnum`（0/1/2）
- 商家 Service 单测通过（入驻、重复入驻、审核）
- `MerchantContextHolder` + `@MerchantIdentity` + `MerchantContextInterceptor` 生效：`/app-api/merchant/**` 下标注 `@MerchantIdentity` 的接口，未入驻/未审核通过时被拒
- 管理端审核接口、小程序端入驻/查询接口可用
- 全部改动提交到 `feature/yy-backend-framework`

## 已知风险 / 备注

- 拦截器注册路径 `/app-api/merchant/**`：若因前缀注入方式导致不生效，退化为「拦截全部 `/app-api/**` 并仅对带 `@MerchantIdentity` 的 handler 生效」（preHandle 已有该判断）。
- 单测中 `SecurityFrameworkUtils` 依赖请求上下文；若不便，用 `MockedStatic` 静态 mock，务必覆盖三条分支。
- `AppDispatchMerchantController` 未使用 `@MerchantIdentity`（入驻与查自己状态不需要已是商家），这是刻意设计。
