package com.yy.module.dispatch.service.wallet;

import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;

public interface DispatchMerchantWalletService {

    DispatchMerchantWalletDO getOrCreateWallet(Long merchantId);

    void recharge(Long merchantId, Integer amount);

    Integer getBalance(Long merchantId);

    /** 原子扣款，返回 false=余额不足 */
    boolean deduct(Long merchantId, Integer amount);

    /** 退款入账 */
    void credit(Long merchantId, Integer amount);
}
