package com.yy.module.dispatch.job;

import com.yy.framework.quartz.core.handler.JobHandler;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 派单订单超时取消 Job：扫描并取消超时未接的订单
 */
@Slf4j
@Component("dispatchOrderTimeoutJob")
public class DispatchOrderTimeoutJob implements JobHandler {

    @Resource
    private DispatchOrderService orderService;

    @Override
    public String execute(String param) {
        int count = orderService.cancelTimeoutOrders();
        log.info("[execute][超时取消派单订单 {} 个]", count);
        return "超时取消派单订单 " + count + " 个";
    }

}
