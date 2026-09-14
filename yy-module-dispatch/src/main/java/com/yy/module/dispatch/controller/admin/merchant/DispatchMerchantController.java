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
