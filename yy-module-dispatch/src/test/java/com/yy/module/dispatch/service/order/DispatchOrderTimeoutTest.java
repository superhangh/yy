package com.yy.module.dispatch.service.order;

import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Import({DispatchOrderServiceImpl.class, DispatchOrderLogServiceImpl.class,
        com.yy.module.dispatch.mq.producer.order.DispatchOrderProducer.class,
        com.yy.module.dispatch.service.merchant.DispatchMerchantServiceImpl.class})
public class DispatchOrderTimeoutTest extends BaseDbUnitTest {

    @Resource
    private DispatchOrderService orderService;
    @Resource
    private DispatchOrderMapper orderMapper;

    private DispatchOrderCreateReqVO buildReqVO(LocalDateTime deadline) {
        DispatchOrderCreateReqVO vo = new DispatchOrderCreateReqVO();
        vo.setTitle("超时测试");
        vo.setAddress("上海");
        vo.setAmount(1000);
        vo.setDeadline(deadline);
        return vo;
    }

    @Test
    public void testCancelTimeoutOrders() {
        // 已过期的待接单订单
        Long expiredId = orderService.createOrder(2001L, buildReqVO(LocalDateTime.now().minusMinutes(5)));
        // 未过期的待接单订单
        Long futureId = orderService.createOrder(2001L, buildReqVO(LocalDateTime.now().plusMinutes(30)));
        // 已接单（即使过期也不该被系统取消）
        Long acceptedId = orderService.createOrder(2001L, buildReqVO(LocalDateTime.now().minusMinutes(5)));
        orderService.acceptOrder(acceptedId, 1001L);

        int count = orderService.cancelTimeoutOrders();
        assertEquals(1, count);
        assertEquals(DispatchOrderStatusEnum.CANCELED.getStatus(), orderMapper.selectById(expiredId).getStatus());
        assertEquals(DispatchOrderStatusEnum.PENDING.getStatus(), orderMapper.selectById(futureId).getStatus());
        assertEquals(DispatchOrderStatusEnum.ACCEPTED.getStatus(), orderMapper.selectById(acceptedId).getStatus());
    }
}
