package com.yy.module.dispatch.controller.app.order;

import com.yy.framework.common.pojo.CommonResult;
import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.controller.app.order.vo.AppDispatchOrderRespVO;
import com.yy.module.dispatch.convert.order.DispatchOrderConvert;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.yy.framework.common.pojo.CommonResult.success;

@Tag(name = "小程序 - 派单订单（用户端）")
@RestController
@RequestMapping("/dispatch/order")
@Validated
public class AppDispatchOrderController {

    @Resource
    private DispatchOrderService orderService;

    @GetMapping("/hall-page")
    @Operation(summary = "抢单大厅分页（待接单）")
    public CommonResult<PageResult<AppDispatchOrderRespVO>> getHallPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getHallPage(reqVO);
        return success(DispatchOrderConvert.INSTANCE.convertPage(page));
    }

    @PostMapping("/accept")
    @Operation(summary = "抢单")
    @Parameter(name = "id", description = "订单编号", required = true, example = "1")
    public CommonResult<Boolean> acceptOrder(@RequestParam("id") Long id) {
        orderService.acceptOrder(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/my-page")
    @Operation(summary = "我的接单分页")
    public CommonResult<PageResult<AppDispatchOrderRespVO>> getMyPage(@Valid DispatchOrderPageReqVO reqVO) {
        PageResult<DispatchOrderDO> page = orderService.getUserOrderPage(SecurityFrameworkUtils.getLoginUserId(), reqVO);
        return success(DispatchOrderConvert.INSTANCE.convertPage(page));
    }

    @PutMapping("/start")
    @Operation(summary = "开始服务")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> startOrder(@RequestParam("id") Long id) {
        orderService.startOrder(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成服务")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> finishOrder(@RequestParam("id") Long id) {
        orderService.finishOrder(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得订单详情")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<AppDispatchOrderRespVO> getOrder(@RequestParam("id") Long id) {
        DispatchOrderDO order = orderService.getOrder(id);
        return success(DispatchOrderConvert.INSTANCE.convert(order));
    }
}
