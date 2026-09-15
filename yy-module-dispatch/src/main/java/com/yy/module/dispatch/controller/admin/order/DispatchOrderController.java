package com.yy.module.dispatch.controller.admin.order;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderRespVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 派单订单")
@RestController
@RequestMapping("/dispatch/order")
@Validated
public class DispatchOrderController {

    @Resource
    private DispatchOrderService orderService;

    @GetMapping("/page")
    @Operation(summary = "获得派单订单分页")
    @PreAuthorize("@ss.hasPermission('dispatch:order:query')")
    public CommonResult<PageResult<DispatchOrderRespVO>> getOrderPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getAdminOrderPage(reqVO);
        PageResult<DispatchOrderRespVO> result = new PageResult<>();
        result.setList(page.getList().stream().map(o -> {
            DispatchOrderRespVO vo = new DispatchOrderRespVO();
            BeanUtils.copyProperties(o, vo);
            return vo;
        }).toList());
        result.setTotal(page.getTotal());
        return success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "获得派单订单详情")
    @Parameter(name = "id", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('dispatch:order:query')")
    public CommonResult<DispatchOrderRespVO> getOrder(@RequestParam("id") Long id) {
        DispatchOrderDO order = orderService.getOrder(id);
        DispatchOrderRespVO vo = new DispatchOrderRespVO();
        BeanUtils.copyProperties(order, vo);
        return success(vo);
    }

    @PutMapping("/cancel")
    @Operation(summary = "管理员强制取消订单")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id,
                                             @RequestParam(value = "reason", required = false) String reason) {
        orderService.cancelOrderByAdmin(id, reason);
        return success(true);
    }
}
