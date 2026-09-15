package com.yy.module.dispatch.service.settlement;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.service.order.DispatchOrderService;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.WALLET_INSUFFICIENT_BALANCE;
import static org.junit.jupiter.api.Assertions.*;

@Import({DispatchSettlementServiceImpl.class, DispatchMerchantWalletServiceImpl.class,
        com.yy.module.dispatch.service.order.DispatchOrderServiceImpl.class,
        com.yy.module.dispatch.service.order.DispatchOrderLogServiceImpl.class,
        com.yy.module.dispatch.mq.producer.order.DispatchOrderProducer.class,
        com.yy.module.dispatch.service.merchant.DispatchMerchantServiceImpl.class})
public class DispatchSettlementServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchSettlementService settlementService;
    @Resource
    private DispatchOrderService orderService;
    @Resource
    private DispatchMerchantWalletService walletService;
    @Resource
    private DispatchOrderMapper orderMapper;

    private void createAndComplete(Long merchantId, Integer amount) {
        DispatchOrderCreateReqVO vo = new DispatchOrderCreateReqVO();
        vo.setTitle("结算测试"); vo.setAddress("上海"); vo.setAmount(amount);
        Long id = orderService.createOrder(merchantId, vo);
        orderService.acceptOrder(id, 1001L);
        orderService.startOrder(id, 1001L);
        orderService.finishOrder(id, 1001L);
    }

    @Test
    public void testSettleOrder_success() {
        walletService.recharge(2001L, 5000);
        createAndComplete(2001L, 3000);
        Long orderId = orderMapper.selectList().get(0).getId();
        settlementService.settleOrder(orderId);
        assertEquals(DispatchOrderPayStatusEnum.PAID.getStatus(), orderMapper.selectById(orderId).getPayStatus());
        assertEquals(2000, walletService.getBalance(2001L));
    }

    @Test
    public void testSettleOrder_insufficientBalance() {
        walletService.recharge(2001L, 1000);
        createAndComplete(2001L, 3000);
        Long orderId = orderMapper.selectList().get(0).getId();
        ServiceException ex = assertThrows(ServiceException.class, () -> settlementService.settleOrder(orderId));
        assertEquals(WALLET_INSUFFICIENT_BALANCE.getCode(), ex.getCode());
        // 余额不变
        assertEquals(1000, walletService.getBalance(2001L));
    }

    @Test
    public void testRefundOrder() {
        walletService.recharge(2001L, 5000);
        createAndComplete(2001L, 3000);
        Long orderId = orderMapper.selectList().get(0).getId();
        settlementService.settleOrder(orderId);
        settlementService.refundOrder(orderId);
        assertEquals(DispatchOrderPayStatusEnum.REFUNDED.getStatus(), orderMapper.selectById(orderId).getPayStatus());
        assertEquals(5000, walletService.getBalance(2001L)); // 退款后余额恢复
    }
}
