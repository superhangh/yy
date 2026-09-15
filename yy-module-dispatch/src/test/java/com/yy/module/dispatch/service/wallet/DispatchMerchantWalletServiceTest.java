package com.yy.module.dispatch.service.wallet;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.WALLET_AMOUNT_INVALID;
import static org.junit.jupiter.api.Assertions.*;

@Import(DispatchMerchantWalletServiceImpl.class)
public class DispatchMerchantWalletServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchMerchantWalletService walletService;

    @Test
    public void testRechargeAndDeduct() {
        walletService.recharge(2001L, 5000);
        assertEquals(5000, walletService.getBalance(2001L));
        assertTrue(walletService.deduct(2001L, 3000));
        assertEquals(2000, walletService.getBalance(2001L));
    }

    @Test
    public void testDeduct_insufficient() {
        walletService.recharge(2001L, 1000);
        assertFalse(walletService.deduct(2001L, 2000));
        assertEquals(1000, walletService.getBalance(2001L));
    }

    @Test
    public void testCredit() {
        walletService.recharge(2001L, 1000);
        walletService.credit(2001L, 500);
        assertEquals(1500, walletService.getBalance(2001L));
    }

    @Test
    public void testRecharge_invalidAmount() {
        ServiceException ex = assertThrows(ServiceException.class, () -> walletService.recharge(2001L, 0));
        assertEquals(WALLET_AMOUNT_INVALID.getCode(), ex.getCode());
        assertThrows(ServiceException.class, () -> walletService.recharge(2001L, -100));
        // 余额不受影响
        assertEquals(0, walletService.getBalance(2001L));
    }
}
