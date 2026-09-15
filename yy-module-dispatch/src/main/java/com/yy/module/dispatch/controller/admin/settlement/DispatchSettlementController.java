package com.yy.module.dispatch.controller.admin.settlement;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.common.util.object.BeanUtils;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementRespVO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;
import com.yy.module.dispatch.service.settlement.DispatchSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 派单结算")
@RestController
@RequestMapping("/dispatch/settlement")
@Validated
public class DispatchSettlementController {

    @Resource
    private DispatchSettlementService settlementService;

    @GetMapping("/page")
    @Operation(summary = "结算分页")
    @PreAuthorize("@ss.hasPermission('dispatch:settlement:query')")
    public CommonResult<PageResult<DispatchSettlementRespVO>> getSettlementPage(@Valid DispatchSettlementPageReqVO reqVO) {
        PageResult<DispatchSettlementDO> page = settlementService.getSettlementPage(reqVO);
        return success(BeanUtils.toBean(page, DispatchSettlementRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "结算详情")
    @Parameter(name = "orderId", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:settlement:query')")
    public CommonResult<DispatchSettlementRespVO> getSettlement(@RequestParam("orderId") Long orderId) {
        DispatchSettlementDO settlement = settlementService.getSettlement(orderId);
        return success(BeanUtils.toBean(settlement, DispatchSettlementRespVO.class));
    }

    @PutMapping("/refund")
    @Operation(summary = "退款")
    @Parameter(name = "orderId", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:settlement:refund')")
    public CommonResult<Boolean> refundOrder(@RequestParam("orderId") Long orderId) {
        settlementService.refundOrder(orderId);
        return success(true);
    }
}
