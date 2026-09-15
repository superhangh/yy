package com.yy.module.dispatch.service.order;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.ORDER_ALREADY_ACCEPTED;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.ORDER_NOT_EXISTS;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.ORDER_NOT_USER_OWNER;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.ORDER_STATUS_ERROR;
import static org.junit.jupiter.api.Assertions.*;

@Import({DispatchOrderServiceImpl.class, DispatchOrderLogServiceImpl.class})
public class DispatchOrderServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchOrderService orderService;
    @Resource
    private DispatchOrderMapper orderMapper;

    private DispatchOrderCreateReqVO buildReqVO() {
        DispatchOrderCreateReqVO vo = new DispatchOrderCreateReqVO();
        vo.setTitle("帮买咖啡");
        vo.setAddress("上海市浦东新区");
        vo.setAmount(1000);
        return vo;
    }

    @Test
    public void testCreateOrder() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        DispatchOrderDO order = orderMapper.selectById(id);
        assertEquals(DispatchOrderStatusEnum.PENDING.getStatus(), order.getStatus());
        assertEquals(2001L, order.getMerchantId());
        assertNotNull(order.getNo());
    }

    @Test
    public void testAcceptOrder_success() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        DispatchOrderDO order = orderMapper.selectById(id);
        assertEquals(DispatchOrderStatusEnum.ACCEPTED.getStatus(), order.getStatus());
        assertEquals(1001L, order.getUserId());
    }

    @Test
    public void testAcceptOrder_alreadyAccepted() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        // 第二个人抢同一单 -> 条件更新影响行数为 0
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.acceptOrder(id, 1002L));
        assertEquals(ORDER_ALREADY_ACCEPTED.getCode(), ex.getCode());
        // 仍然是第一个用户
        assertEquals(1001L, orderMapper.selectById(id).getUserId());
    }

    @Test
    public void testStartAndFinishOrder() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        orderService.startOrder(id, 1001L);
        assertEquals(DispatchOrderStatusEnum.SERVING.getStatus(), orderMapper.selectById(id).getStatus());
        orderService.finishOrder(id, 1001L);
        assertEquals(DispatchOrderStatusEnum.COMPLETED.getStatus(), orderMapper.selectById(id).getStatus());
    }

    @Test
    public void testStartOrder_wrongStatus() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        // 未接单就 start -> 状态错误
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.startOrder(id, 1001L));
        assertEquals(ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }

    @Test
    public void testGetUserOrder_permission() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        // 待接单：大厅公开，任何用户可见
        assertNotNull(orderService.getUserOrder(id, 9999L));
        // 接单后：仅接单本人可见，他人抛 ORDER_NOT_EXISTS
        orderService.acceptOrder(id, 1001L);
        assertNotNull(orderService.getUserOrder(id, 1001L));
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.getUserOrder(id, 1002L));
        assertEquals(ORDER_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    public void testCancelOrder() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.cancelOrder(id, 2001L, "不需要了");
        assertEquals(DispatchOrderStatusEnum.CANCELED.getStatus(), orderMapper.selectById(id).getStatus());
        // 已取消，再次取消 -> 状态错误
        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.cancelOrder(id, 2001L, "again"));
        assertEquals(ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }

    @Test
    public void testCancelOrderByAdmin() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        orderService.cancelOrderByAdmin(id, "管理端取消");
        assertEquals(DispatchOrderStatusEnum.CANCELED.getStatus(), orderMapper.selectById(id).getStatus());
    }

    @Test
    public void testStartOrder_notOwner() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        orderService.acceptOrder(id, 1001L);
        // 非接单人 start -> 归属错误
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.startOrder(id, 1002L));
        assertEquals(ORDER_NOT_USER_OWNER.getCode(), ex.getCode());
    }

    @Test
    public void testOrderNotExists() {
        ServiceException ex1 = assertThrows(ServiceException.class, () -> orderService.startOrder(999999L, 1001L));
        assertEquals(ORDER_NOT_EXISTS.getCode(), ex1.getCode());
        ServiceException ex2 = assertThrows(ServiceException.class, () -> orderService.getUserOrder(999999L, 1001L));
        assertEquals(ORDER_NOT_EXISTS.getCode(), ex2.getCode());
    }

    @Test
    public void testGetMerchantOrder_owner() {
        Long id = orderService.createOrder(2001L, buildReqVO());
        assertNotNull(orderService.getMerchantOrder(id, 2001L));
        ServiceException ex = assertThrows(ServiceException.class, () -> orderService.getMerchantOrder(id, 9999L));
        assertEquals(ORDER_NOT_EXISTS.getCode(), ex.getCode());
    }
}
