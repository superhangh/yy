package com.yy.module.dispatch.controller.app.merchant.order;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.controller.app.order.vo.AppDispatchOrderRespVO;
import com.yy.module.dispatch.convert.order.DispatchOrderConvert;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextHolder;
import com.yy.module.dispatch.framework.merchant.core.MerchantIdentity;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "小程序 - 派单订单（商家端）")
@RestController
@RequestMapping("/merchant/dispatch/order")
@Validated
@MerchantIdentity
public class AppDispatchMerchantOrderController {

    @Resource
    private DispatchOrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "商家发单")
    public CommonResult<Long> createOrder(@Valid @RequestBody DispatchOrderCreateReqVO reqVO) {
        return success(orderService.createOrder(MerchantContextHolder.getMerchantId(), reqVO));
    }

    @GetMapping("/my-page")
    @Operation(summary = "我的发单分页")
    public CommonResult<PageResult<AppDispatchOrderRespVO>> getMyPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getMerchantOrderPage(MerchantContextHolder.getMerchantId(), reqVO);
        return success(DispatchOrderConvert.INSTANCE.convertPage(page));
    }

    @PutMapping("/cancel")
    @Operation(summary = "商家取消订单")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id,
                                             @RequestParam(value = "reason", required = false) String reason) {
        orderService.cancelOrder(id, MerchantContextHolder.getMerchantId(), reason);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得订单详情（商家端）")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<AppDispatchOrderRespVO> getOrder(@RequestParam("id") Long id) {
        DispatchOrderDO order = orderService.getMerchantOrder(id, MerchantContextHolder.getMerchantId());
        return success(DispatchOrderConvert.INSTANCE.convert(order));
    }
}
