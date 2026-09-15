package com.yy.module.dispatch.controller.app.merchant;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.controller.app.merchant.vo.AppDispatchMerchantApplyReqVO;
import com.yy.module.dispatch.controller.app.merchant.vo.AppDispatchMerchantRespVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
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
