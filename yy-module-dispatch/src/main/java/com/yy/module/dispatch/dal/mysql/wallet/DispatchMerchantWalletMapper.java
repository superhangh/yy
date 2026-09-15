package com.yy.module.dispatch.dal.mysql.wallet;

import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.module.dispatch.dal.dataobject.wallet.DispatchMerchantWalletDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispatchMerchantWalletMapper extends BaseMapperX<DispatchMerchantWalletDO> {

    default DispatchMerchantWalletDO selectByMerchantId(Long merchantId) {
        return selectOne(DispatchMerchantWalletDO::getMerchantId, merchantId);
    }

    /** 原子扣款：balance >= amount 才扣，返回影响行数 */
    default int updateDeduct(Long merchantId, Integer amount) {
        return update(null, new LambdaUpdateWrapper<DispatchMerchantWalletDO>()
                .setSql(" balance = balance - " + amount)
                .setSql(" total_consume = total_consume + " + amount)
                .eq(DispatchMerchantWalletDO::getMerchantId, merchantId)
                .ge(DispatchMerchantWalletDO::getBalance, amount));
    }

    /** 入账（充值/退款） */
    default int updateAdd(Long merchantId, Integer amount, boolean isRecharge) {
        LambdaUpdateWrapper<DispatchMerchantWalletDO> wrapper = new LambdaUpdateWrapper<DispatchMerchantWalletDO>()
                .setSql(" balance = balance + " + amount)
                .eq(DispatchMerchantWalletDO::getMerchantId, merchantId);
        if (isRecharge) {
            wrapper.setSql(" total_recharge = total_recharge + " + amount);
        }
        return update(null, wrapper);
    }
}
