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
