package com.yy.module.dispatch.controller.admin.wallet;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家钱包")
@RestController
@RequestMapping("/dispatch/wallet")
@Validated
public class DispatchWalletController {

    @Resource
    private DispatchMerchantWalletService walletService;

    @PostMapping("/recharge")
    @Operation(summary = "商家充值")
    @Parameter(name = "merchantId", description = "商家编号", required = true)
    @Parameter(name = "amount", description = "金额（分）", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:wallet:recharge')")
    public CommonResult<Boolean> recharge(@RequestParam("merchantId") Long merchantId,
                                          @RequestParam("amount") Integer amount) {
        walletService.recharge(merchantId, amount);
        return success(true);
    }

    @GetMapping("/balance")
    @Operation(summary = "查询余额")
    @Parameter(name = "merchantId", description = "商家编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:wallet:query')")
    public CommonResult<Integer> getBalance(@RequestParam("merchantId") Long merchantId) {
        return success(walletService.getBalance(merchantId));
    }
}
