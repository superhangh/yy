package com.yy.module.dispatch.service.wallet;

import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;
import com.yy.module.dispatch.dal.mysql.wallet.DispatchMerchantWalletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.WALLET_AMOUNT_INVALID;

@Service
@Validated
public class DispatchMerchantWalletServiceImpl implements DispatchMerchantWalletService {

    @Resource
    private DispatchMerchantWalletMapper walletMapper;

    private void validateAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw exception(WALLET_AMOUNT_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchMerchantWalletDO getOrCreateWallet(Long merchantId) {
        DispatchMerchantWalletDO wallet = walletMapper.selectByMerchantId(merchantId);
        if (wallet != null) {
            return wallet;
        }
        wallet = DispatchMerchantWalletDO.builder()
                .merchantId(merchantId).balance(0).totalRecharge(0).totalConsume(0)
                .build();
        try {
            walletMapper.insert(wallet);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发创建：唯一索引兜底，重新查
            wallet = walletMapper.selectByMerchantId(merchantId);
        }
        return wallet;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(Long merchantId, Integer amount) {
        validateAmount(amount);
        getOrCreateWallet(merchantId);
        walletMapper.updateAdd(merchantId, amount, true);
    }

    @Override
    public Integer getBalance(Long merchantId) {
        DispatchMerchantWalletDO wallet = walletMapper.selectByMerchantId(merchantId);
        return wallet != null ? wallet.getBalance() : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(Long merchantId, Integer amount) {
        validateAmount(amount);
        getOrCreateWallet(merchantId);
        return walletMapper.updateDeduct(merchantId, amount) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(Long merchantId, Integer amount) {
        validateAmount(amount);
        getOrCreateWallet(merchantId);
        walletMapper.updateAdd(merchantId, amount, false);
    }
}
